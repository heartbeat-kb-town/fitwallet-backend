package com.fitwallet.domain.store.service;

import com.fitwallet.domain.store.dto.request.StoreSearchCondition;
import com.fitwallet.domain.store.dto.response.PopularKeywordsResponse;
import com.fitwallet.domain.store.dto.response.StoreKeywordsResponse;
import com.fitwallet.domain.store.dto.response.StoreSearchResponse;
import com.fitwallet.domain.store.dto.response.StoreSummaryResponse;
import com.fitwallet.domain.store.exception.StoreErrorCode;
import com.fitwallet.domain.store.mapper.StoreMapper;
import com.fitwallet.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * {@code @Transactional}은 인터페이스가 아니라 여기, 구현체 메서드에 붙인다(§9).
 * <p>
 * {@link SearchHistoryService}는 반드시 이 인터페이스 타입으로 주입받는다.
 * {@code <tx:annotation-driven>}에 {@code proxy-target-class}가 없어 JDK 동적 프록시가
 * 만들어지므로, 구현체({@code DefaultSearchHistoryService}) 타입으로 필드를 선언하면
 * 주입 자체가 실패한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultStoreService implements StoreService {

    private static final int NEARBY_MAX_RADIUS_METERS = 3000;

    /** {@code StoreMapper.findStores}의 {@code LIMIT}과 맞춘다. 계단식 반경의 종료 판단 기준이다. */
    private static final int RESULT_LIMIT = 5;

    /**
     * 계단식 반경의 중간 단계(m). 마지막 단계는 확정 반경이라 여기 없다.
     * <p>
     * 값의 근거는 272만 행 perf DB 강남역 실측이다. 반경을 넓힐수록 사각형 안 행이 급격히 늘어난다 —
     * 300m 3,871행(0.01s) · 1km 18,730행(0.04s) · 3km 84,677행(0.20s). 그런데 <b>상위 5건은
     * 다섯 반경 전부 동일했다</b>(전부 69m). 즉 밀집 지역에서 3km를 뒤지는 것은 전부 헛일이다.
     * <p>
     * 반대로 희소 지역은 넓혀야 결과가 나오지만 넓혀도 싸다 — 평창 3km가 0.02s다. 데이터가 없으니
     * 읽을 것도 없기 때문이다. <b>넓혀야 하는 곳은 넓혀도 싸고, 비싼 곳은 넓힐 필요가 없다.</b>
     * 이 자기교정적 성질이 계단식이 성립하는 이유다.
     */
    private static final int[] CASCADE_STEPS_METERS = {300, 1000};

    /**
     * 검색어 갈래의 계단식 반경(m). 마지막에 전국 단계가 이어지므로 여기 상한이 곧 사다리의 끝이다.
     * <p>
     * 주변 조회의 {@link #CASCADE_STEPS_METERS}와 달리 10km까지 올라간다. 밀집 지역은 300m에서
     * 끝나므로 이 상한이 비용이 되지 않고, <b>희소 지역에서만 실제로 올라가는데 거기서는 넓혀도
     * 싸기 때문이다</b>(사각형이 넓어져도 읽을 행이 없다). 272만 행 perf DB, 1km 안 매장이 0건인
     * 좌표(지리산) × '식당' 실측:
     * <pre>
     *   300m  0.7ms(0건)   1km  1.3ms(0건)   3km  3.9ms(0건)   10km  11.8ms(5건)
     *   전국 FULLTEXT 122.6ms
     * </pre>
     * 10km 사각형이 전국 폴백의 10분의 1이다. 1km에서 끊고 전국으로 떨어뜨리면 이 좌표가
     * 운영 환산 267ms가 된다.
     */
    private static final int[] KEYWORD_CASCADE_STEPS_METERS = {300, 1000, 3000, 10000};

    /**
     * {@link #KEYWORD_CASCADE_STEPS_METERS}에서 전국 단계 갈림길이 놓이는 자리. 이 인덱스
     * <b>앞</b>의 계단은 무조건 밟고, 뒤의 계단은 {@link #FULLTEXT_ROUTING_THRESHOLD} 판정을
     * 통과해야 밟는다. 3이면 300m·1km·3km를 밟은 뒤 갈린다.
     * <p>
     * 근거는 {@link #findStoresByKeyword}의 "갈림길을 3km 뒤로 옮긴 이유"에 있다.
     * 값을 줄이면 예전 동작으로 돌아간다(1이면 300m 뒤에 갈림).
     */
    private static final int KEYWORD_ROUTING_DECISION_INDEX = 3;

    /**
     * 검색어 조각식 → 전국 FULLTEXT 매칭 수 캐시.
     * <p>
     * <b>이 값은 {@code store}가 바뀌지 않는 한 상수다.</b> 그런데 예전에는 요청마다 다시
     * 셌고, 운영 트레이스에서 <b>82.6ms</b>가 찍혔다('주유소', 244ms 요청의 34%). 같은 쿼리가
     * 다른 요청에서는 11.2ms였다 — FULLTEXT 포스팅 리스트가 버퍼에 남아 있느냐에 따라 갈린다.
     * 즉 <b>느린 쪽이 실사용 값이고, 반복 측정으로는 안 보인다.</b>
     * <p>
     * ⚠️ <b>키가 사용자 입력이라 무한히 늘 수 있다.</b> 그래서 LRU로 상한을 둔다. 상한이
     * 없으면 검색어를 계속 바꿔 던지는 것만으로 힙이 찬다.
     * <p>
     * ⚠️ <b>인스턴스마다 따로 갖는다.</b> 지금은 단일 인스턴스라 무해하고, 늘어나도 캐시
     * 미스가 늘 뿐 정답은 같다({@code store}가 안 바뀌므로).
     * <p>
     * {@code store}를 재적재하면 낡는다. 그때는 앱을 재기동한다 — 재적재 자체가 배포 급의
     * 작업이라 별도 무효화 경로를 두지 않는다.
     */
    private static final int FULLTEXT_COUNT_CACHE_MAX = 1_000;

    /** 위 주석의 캐시 본체. 접근 순서 {@link LinkedHashMap}이라 put/get이 곧 LRU 갱신이다. */
    private final Map<String, Integer> fulltextCountCache = Collections.synchronizedMap(
            new LinkedHashMap<String, Integer>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
                    return size() > FULLTEXT_COUNT_CACHE_MAX;
                }
            });

    /**
     * 계단을 더 밟을지, 전국 단계로 바로 갈지 가르는 전국 매칭 수 기준.
     * <p>
     * <b>어느 쪽으로 가도 응답은 같다.</b> 이 값은 비용에만 영향을 준다.
     * <p>
     * 두 경로의 비용이 서로 다른 것에 비례하기 때문에 교차점이 생긴다 — 전국 단계는 <b>매칭
     * 행수</b>에, 사각형 단계는 <b>좌표 주변 밀도</b>에 비례한다.
     * <p>
     * 판정식은 이것이다. 계단을 더 밟는 쪽이 이득인 조건:
     * <pre>
     *   10km 단 비용  &lt;  P(10km에서 5건이 참) × 전국 단계 비용
     * </pre>
     *
     * <h2>V15(2차원 인덱스) 이후 좌변이 폭락해 13,000 → 1,000으로 내렸다 (2026-08-23)</h2>
     *
     * 예전 값 13,000의 근거는 강남역 실측이었다 — '부산'(10,237) 전국 88ms 대 계단 178ms,
     * '치킨'(20,793) 전국 158ms 대 계단 90ms. <b>그 계단 비용은 좌표 인덱스가 위도만 좁히던
     * 시절의 값이라 지금은 무효다.</b> V15가 사각형을 2차원으로 좁힌 뒤 다시 재면:
     * <pre>
     *   10km 단   tier 1  5.5ms   ·  tier 4  57.8ms  ·  tier 7  82.2ms
     *   (V15 이전에는 희소 좌표에서 270ms였다)
     * </pre>
     * 그리고 <b>갈림길에 도달하는 것은 거의 희소 좌표뿐이다</b> — 밀집 좌표는 3km에서 이미
     * 끝난다. 즉 실제로 판정이 일어나는 자리에서 10km 단은 5.5ms다.
     * <p>
     * 우변인 전국 단계는 매칭 행수에 약 6.3μs/행이다(운영 트레이스 실측: FT 8,681건 →
     * 128.8ms). FT 1,000이면 약 6ms로 10km 단과 비슷해지고, 그보다 크면 계단이 이긴다.
     * <p>
     * <b>그리고 이 계산이 실측으로 반증됐다 (2026-08-23).</b> 위 근거로 1,000까지 내려
     * 배포하고 같은 2,000행으로 재보니 <b>p95는 96 → 95ms로 무변화인데 p99가 182 → 217ms,
     * max가 296 → 370ms로 나빠졌다.</b> 좋아진 검색어가 하나도 없었다.
     * <pre>
     *   국수/칼국수        167 → 208ms
     *   아이스크림 할인점    131 → 168ms
     *   족발/보쌈           54 →  80ms
     * </pre>
     * 틀린 곳이 둘이다. ① "헛밟는 손해 5.5ms"는 희소 좌표 <b>한 곳</b>의 값이었고 실제
     * 표본에서는 26~42ms였다 — 판정이 희소 좌표에서만 일어난다는 전제가 데이터로 뒷받침되지
     * 않았다. ② 이득 쪽이 0이었다. FT 1,000~13,000 구간에서 10km를 밟아 5건이 차는 경우가
     * 이 표본에 사실상 없다.
     * <p>
     * ⚠️ <b>그래서 13,000으로 되돌렸지만, 13,000에도 지금 유효한 근거는 없다.</b> 그 값의
     * 실측(아래)은 V15 이전이라 계단 비용이 지금과 다르다. 두 값 모두 근거가 없는 상태이고,
     * 1,000 쪽만 반증돼 있어 덜 나쁜 쪽으로 돌아간 것이다. 다시 손대려면 <b>10km 단에서 5건이
     * 차는 비율</b>을 먼저 측정해야 한다 — 그것이 이 값이 실제로 예측하려는 것이다.
     * <p>
     * ⚠️ 이 값을 <b>"남은 예산에 전국 단계가 들어가는가"로 정하면 안 된다.</b> 예전에 그렇게
     * 5,000을 잡았다가 실패한 적이 있다. 올바른 질문은 "전국이 계단보다 싼가"이고, 지금
     * 1,000은 그 질문에 위 실측으로 답한 값이다 — 우연히 비슷한 자릿수일 뿐 근거가 다르다.
     * <p>
     * ⚠️ <b>이 값으로 못 고치는 종이 있다.</b> '국수/칼국수'는 ngram이 {@code +"국수" +"칼국수"}로
     * 쪼개져 FT가 8,681인데 LIKE 결과는 전국 11건뿐이다. 10km에서 절대 5건이 안 차므로 어느
     * 쪽으로 라우팅해도 전국 단계를 낸다. 그건 {@link #buildMatchExpression}의 조각 분리 문제라
     * 별개 축이다.
     */
    private static final int FULLTEXT_ROUTING_THRESHOLD = 13_000;

    /**
     * 검색어 최소 길이. 이보다 짧으면 {@link StoreErrorCode#KEYWORD_TOO_SHORT}로 거부한다.
     * <p>
     * <b>ngram 인덱스의 {@code ngram_token_size = 2}와 묶여 있다.</b> 1글자는 구조적으로
     * 색인되지 않아 전국 단계가 인덱스를 못 타고 풀스캔으로 떨어진다(로컬 795ms). 검색으로서도
     * 의미가 없다 — '주' 한 글자가 132,196건에 매칭된다. 부하 데이터의 검색어 80만 건 중
     * 1글자는 0건이라 실사용에서 잃는 것도 없다.
     * <p>
     * {@code ngram_token_size}를 올리면 이 값도 함께 올려야 한다. 내리는 것은 안전하지 않다.
     */
    private static final int KEYWORD_MIN_LENGTH = 2;

    /** DB에서 오지 않는 서비스 정책값. {@code StoreMapper.insertPopularKeywords}의 집계 기간과 맞춘다. */
    private static final int POPULAR_PERIOD_DAYS = 7;

    /**
     * 인기 검색어 재집계 주기(ms). {@code @Scheduled} 인자로 쓰이므로 컴파일 상수여야 한다.
     * <p>
     * 5분인 근거는 비용이다 — 집계 1회가 운영에서 135ms라 부하가 {@code 135 / 300_000 = 0.045%}다.
     * 하루 1회로 묶어 아낄 것이 없고, 대신 신선도 지연이 최대 5분으로 짧아진다.
     */
    private static final long POPULAR_REFRESH_INTERVAL_MS = 300_000L;

    private final StoreMapper storeMapper;
    private final SearchHistoryService searchHistoryService;

    @Override
    @Transactional(readOnly = true)
    public StoreSearchResponse searchStores(Long userId, StoreSearchCondition cond) {
        Double latitude = cond.getLatitude();
        Double longitude = cond.getLongitude();
        if (latitude == null || longitude == null) {
            throw new BusinessException(StoreErrorCode.COORDINATE_PAIR_REQUIRED);
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new BusinessException(StoreErrorCode.INVALID_COORDINATE);
        }

        String keyword = normalizeKeyword(cond.getKeyword());
        // trim 뒤에 잰다. DTO에 @Size를 달면 원본을 보므로 " 역 "이 3자로 통과하고, 애초에
        // @ModelAttribute에 @Valid가 없어 실행조차 되지 않는다(StoreSearchCondition 참고).
        if (keyword != null && keyword.length() < KEYWORD_MIN_LENGTH) {
            throw new BusinessException(StoreErrorCode.KEYWORD_TOO_SHORT);
        }
        Long categoryId = cond.getCategoryId();

        Integer requestedRadius = cond.getRadiusMeters();
        if (requestedRadius != null && requestedRadius <= 0) {
            throw new BusinessException(StoreErrorCode.INVALID_RADIUS);
        }
        if (requestedRadius != null && requestedRadius > NEARBY_MAX_RADIUS_METERS) {
            throw new BusinessException(StoreErrorCode.RADIUS_EXCEEDED);
        }

        if (!Boolean.TRUE.equals(storeMapper.findLocationAgreed(userId))) {
            throw new BusinessException(StoreErrorCode.LOCATION_AGREEMENT_REQUIRED);
        }

        if (categoryId != null && !storeMapper.existsCategory(categoryId)) {
            throw new BusinessException(StoreErrorCode.CATEGORY_NOT_FOUND);
        }

        /*
         * 검색어 갈래도 3km로 자른다 (2026-08-23 정책 변경).
         *
         * 예전에는 검색어만 오면 반경이 없어 전국에서 가까운 순 5건을 냈다. 이제 모든 검색이
         * 3km 안으로 한정된다 — 3km 밖에 있으면 결과에 안 나온다.
         *
         * ⚠️ **성능 최적화가 아니라 제품 결정이다.** 운영 2,000행 실측으로 대가가 이만큼이다:
         *   - 5건 미만으로 줄어드는 요청 1,082건(54.1%)
         *   - 0건이 되는 요청 720건(36.0%)
         * 지리산에서 '스타벅스'를 치면 예전에는 98km 밖 매장이 나왔고 이제는 0건이다.
         *
         * 얻는 것은 전국 FULLTEXT 단계가 요청 경로에서 사라지는 것이다. 그 단계는 사각형이
         * 없어 좌표 인덱스가 닿지 않았고(V15의 2차원 인덱스도 무력), 남은 SLO 초과의 대부분이
         * 거기였다.
         *
         * ⚠️ 삼항 연산자를 중첩하면 Integer/int가 섞여 requestedRadius가 null일 때 자동
         * 언박싱으로 NPE가 난다(JLS 이항 수치 승격). if-else로 풀어 박싱 타입을 유지한다.
         */
        Integer confirmedRadius = requestedRadius;
        if (requestedRadius == null) {
            confirmedRadius = NEARBY_MAX_RADIUS_METERS;
        }

        StoreSearchCondition confirmedCondition = StoreSearchCondition.builder()
                .keyword(keyword)
                .categoryId(categoryId)
                .latitude(latitude)
                .longitude(longitude)
                .radiusMeters(confirmedRadius)
                .build();
        // 반경이 확정됐으면(주변 조회, 또는 검색어와 반경이 함께 온 요청) 그 반경 안에서 계단식으로
        // 좁힌다. 반경이 없으면(= 검색어만 온 전국 검색) 계단을 밟다가 전국 단계로 넘어간다.
        //
        // 예전에는 검색어가 있으면 계단을 아예 안 걸었다. "LIKE가 먼저 대부분을 쳐내니 단계를
        // 늘리는 만큼 손해"라는 판단이었는데(강남역 '스타벅스' 0.05 + 0.12 + 0.49s), 그건 사각형이
        // 인덱스를 못 타던 시절의 측정이다. V11의 좌표 인덱스와 V14의 커버링 인덱스가 들어온 지금은
        // 뒤집혔다 — 같은 좌표에서 300m 단계가 6ms다.
        // 반경은 위에서 항상 확정된다(3km 상한 정책). 그래서 전국 단계로 내려가는 경로가 없다.
        List<StoreSummaryResponse> stores = findStoresByCascadingRadius(confirmedCondition);

        if (keyword != null) {
            recordSearchHistory(userId, keyword);
        }

        // radiusMeters는 계단식이 실제로 멈춘 단계가 아니라 확정 반경을 그대로 내린다.
        // 계단은 같은 답을 싸게 얻으려는 내부 구현이라 응답에 드러나면 안 된다 — 300m에서
        // 멈췄다고 300을 내리면 "3km 안을 봤다"는 계약이 깨지고, 클라이언트가 이 값으로 지도
        // 반경을 그리면 화면이 요청할 때마다 달라진다.
        return StoreSearchResponse.builder()
                .keyword(keyword)
                .categoryId(categoryId)
                .radiusMeters(confirmedRadius)
                .stores(stores)
                .build();
    }

    /**
     * {@code recent}는 이 사용자의 검색 이력에서 바로 읽고, {@code popular}는
     * {@link #refreshPopularKeywords()}가 미리 채워 둔 결과를 읽는다 — <b>여기서 집계하지 않는다.</b>
     * <p>
     * 그래서 {@code popular}는 최대 {@value #POPULAR_REFRESH_INTERVAL_MS}ms만큼 낡을 수 있다.
     * 사용자가 자기 검색 이력을 지워도({@link #deleteKeyword}/{@link #deleteAllKeywords})
     * 인기 검색어에서 빠지기까지 그만큼 걸린다.
     * <p>
     * {@code periodDays}는 DB에서 오지 않는 서비스 정책값이라 여기서 채운다.
     */
    @Override
    @Transactional(readOnly = true)
    public StoreKeywordsResponse findKeywords(Long userId) {
        return StoreKeywordsResponse.builder()
                .recent(storeMapper.findRecentKeywords(userId))
                .popular(PopularKeywordsResponse.builder()
                        .periodDays(POPULAR_PERIOD_DAYS)
                        .keywords(storeMapper.findPopularKeywords())
                        .build())
                .build();
    }

    /**
     * 인기 검색어를 5분마다 다시 집계한다.
     *
     * <h2>왜 요청 경로 밖으로 뺐나</h2>
     * 예전에는 {@code findKeywords}가 매번 {@code search_history}를 {@code GROUP BY}했다.
     * V12 커버링 인덱스로 읽는 행을 80만에서 7일 창 크기로 줄여 운영 p50 297ms → 127ms까지
     * 왔지만 SLO 100ms를 못 넘겼다. 남은 비용의 대부분(약 70ms)이 22만 행을 집계하는
     * 임시 테이블이고, 그건 인덱스가 손댈 수 있는 자리가 아니었다(V12/V13 주석에 구간별 측정).
     * <p>
     * 미리 집계해 두면 조회가 5행 읽기(0.025ms)로 끝난다. 집계 1회는 운영에서 135ms이므로
     * 5분 주기의 DB 부하는 {@code 135ms / 300초 = 0.045%}다.
     *
     * <h2>{@code initialDelay = 0}인 이유</h2>
     * 기동 직후 한 번 돌아야 첫 배포 후 5분간 {@code popular}이 빈 배열로 나가는 것을 막는다.
     * Flyway가 {@code sqlSessionFactory}보다 먼저 끝나므로({@code root-context.xml}의
     * {@code depends-on}) 이때 테이블은 이미 존재한다.
     *
     * <h2>{@code fixedDelay}이지 {@code fixedRate}가 아닌 이유</h2>
     * {@code fixedRate}는 이전 실행이 늦어지면 다음 실행이 곧바로 이어져 집계가 몰릴 수 있다.
     * {@code fixedDelay}는 <b>끝난 뒤부터</b> 5분을 세므로 겹치지 않는다. 신선도가 5분에서
     * 5분+실행시간으로 늘어나지만 135ms짜리라 무시할 수 있다.
     *
     * <h2>실패하면 로그를 남기고 <b>다시 던진다</b></h2>
     * 잡아서 삼키면 안 된다. {@code @Transactional} 프록시가 메서드 <b>바깥</b>에 있어서,
     * 여기서 삼키면 프록시는 예외를 못 보고 그대로 커밋한다 — {@code DELETE}는 성공하고
     * {@code INSERT}가 실패한 경우 <b>인기 검색어가 통째로 비어 있는 채로 커밋된다.</b>
     * <p>
     * 다시 던지면 트랜잭션이 롤백돼 {@code DELETE}가 취소되고 <b>이전 집계 결과가 그대로
     * 살아남는다.</b> 조회는 (그만큼 낡은 값으로) 계속 동작한다.
     * <p>
     * 던져도 다음 주기는 취소되지 않는다. Spring이 반복 스케줄 작업을
     * {@code TaskUtils.LOG_AND_SUPPRESS_ERROR_HANDLER}로 감싸 예외를 로그하고 삼키기 때문이다
     * ({@code ScheduledThreadPoolExecutor}에 예외가 그대로 올라가면 그 작업이 <b>영구 취소</b>되는데,
     * Spring이 그 앞에서 막아 준다). 그래도 직접 한 번 로그를 남기는 이유는 그 기본 핸들러의
     * 메시지가 "Unexpected error occurred in scheduled task"뿐이라 맥락이 없기 때문이다.
     *
     * <h2>단일 인스턴스 전제</h2>
     * 이 스케줄러는 인스턴스마다 하나씩 돈다. EB가 단일 인스턴스라 지금은 문제가 없다.
     * 인스턴스를 늘리면 5분마다 N번 집계되므로(결과는 같아 무해하나 낭비이고, 동시에 돌면
     * 한쪽이 락을 기다린다) 그때 MySQL EVENT나 락 기반 단일화로 옮겨야 한다.
     */
    @Override
    @Scheduled(fixedDelay = POPULAR_REFRESH_INTERVAL_MS, initialDelay = 0L)
    @Transactional
    public void refreshPopularKeywords() {
        try {
            storeMapper.deletePopularKeywords();
            int inserted = storeMapper.insertPopularKeywords();
            log.info("인기 검색어 재집계 완료. periodDays={}, rows={}", POPULAR_PERIOD_DAYS, inserted);
        } catch (RuntimeException e) {
            log.error("인기 검색어 재집계 실패. 롤백되므로 이전 집계 결과가 그대로 남는다"
                    + "(그만큼 낡는다). 다음 주기는 {}ms 뒤다.", POPULAR_REFRESH_INTERVAL_MS, e);
            throw e;
        }
    }

    @Override
    @Transactional
    public void deleteKeyword(Long userId, Long searchHistoryId) {
        int affected = storeMapper.deleteSearchHistory(searchHistoryId, userId);
        if (affected == 0) {
            throw new BusinessException(StoreErrorCode.SEARCH_HISTORY_NOT_FOUND);
        }
    }

    @Override
    @Transactional
    public void deleteAllKeywords(Long userId) {
        storeMapper.deleteAllSearchHistory(userId);
    }

    /**
     * 좁은 반경부터 차례로 넓히며 조회하고, 5건이 차면 거기서 멈춘다.
     * <p>
     * <b>왜 필요한가.</b> 사각형 선필터(매퍼)만으로는 부족하다. 강남역 3km 사각형 안에 가맹점이
     * 84,677개나 들어와, 인덱스를 타더라도 그 8만 건을 전부 읽고 거리를 계산한 뒤 정렬해서
     * 5건을 뽑아야 한다(로컬 0.20s ≈ 운영 340ms). 그런데 실제로 나가는 5건은 전부 69m 안에 있다.
     * 300m만 봐도 답이 같다는 뜻이다.
     * <p>
     * <b>결과가 바뀌지 않는 이유.</b> 정렬 기준의 첫 키가 거리다. 반경 R로 좁힌 결과가 5건이면
     * 그 5건은 반경 R 안의 모든 후보를 이미 본 뒤 고른 것이고, R을 넓혀서 추가되는 행은 전부
     * 거리가 R보다 커 그 5건 뒤로 정렬된다. 그래서 상위 5건이 달라질 수 없다.
     * <p>
     * <b>그럼에도 5번째 거리가 단계 반경보다 작을 때만 멈춘다.</b> 위 증명은 "단계 R의 결과가
     * 반경 R 안의 모든 후보를 봤다"에 기대는데, 그 전제는 매퍼의 사각형이 HAVING이 통과시키는
     * 영역을 빠짐없이 덮을 때만 성립한다. 실제로 이 전제는 한 번 깨진 적이 있다 — distance_meters가
     * ROUND된 정수라 HAVING은 실제 거리 R + 0.5m까지 받아들이는데 사각형을 정확히 R로 그렸더니
     * 그 틈의 가맹점이 좁은 단계에서만 사라졌다(매퍼에 +1m 여유를 넣어 고쳤고, 통합 테스트
     * {@code 반경_경계에_정확히_걸친_매장도_사각형에_들어온다}가 이를 지킨다).
     * 경계에 걸친 행은 반올림하면 거리가 R로 같아져 store_rank·store_name 타이브레이크로
     * 순서가 갈리므로, 5번째가 경계에 닿으면 한 단계 더 넓혀 확인한다. 조회 한 번의 값이다.
     *
     * @param cond 반경 정책까지 확정된 조건. {@code radiusMeters}가 {@code null}이 아님이 보장된다
     * @return 마지막까지 5건을 못 채웠으면 가장 넓은 단계의 결과(있는 만큼)
     */
    private List<StoreSummaryResponse> findStoresByCascadingRadius(StoreSearchCondition cond) {
        int maxRadius = cond.getRadiusMeters();

        // 요청 반경으로 클램프한 뒤 중복을 제거한다. 클램프가 없으면 radiusMeters=100 요청에
        // [300, 1000, 100]을 돌려 사용자가 요청한 것보다 넓은 범위를 뒤지게 된다 — 결과가 5건 차는
        // 순간 멈추므로 100m 밖 가맹점이 응답에 실려 명세를 어긴다. 클램프하면 [100] 하나가 된다.
        int[] steps = IntStream.concat(IntStream.of(CASCADE_STEPS_METERS), IntStream.of(maxRadius))
                .map(step -> Math.min(step, maxRadius))
                .distinct()
                .toArray();

        List<StoreSummaryResponse> found = Collections.emptyList();
        for (int step : steps) {
            found = findStoresWithin(cond, step);
            if (isSettled(found, step)) {
                return found;
            }
        }
        return found;
    }

    /** 이 단계에서 멈춰도 최대 반경까지 갔을 때와 결과가 같은가. 판단 근거는 위 Javadoc에 있다. */
    private boolean isSettled(List<StoreSummaryResponse> found, int step) {
        if (found.size() < RESULT_LIMIT) {
            return false;
        }
        Integer farthest = found.get(found.size() - 1).getDistanceMeters();
        return farthest != null && farthest < step;
    }

    /** {@code @Setter}가 금지라(§4) 반경만 바꾼 새 인스턴스를 만든다. */
    /**
     * 위도 1도의 미터 환산. <b>매퍼 XML의 사각형 식과 같은 상수여야 한다</b> — 다르면 셀이
     * 사각형을 덜 덮어 결과가 조용히 빠진다.
     */
    private static final double METERS_PER_DEGREE_LATITUDE = 6_371_000 * Math.PI / 180;

    /** {@code store.lat_cell}의 격자 배율(V15). 0.01도 = 약 1.11km. */
    private static final int LAT_CELL_SCALE = 100;

    /**
     * 사각형이 걸치는 {@code lat_cell} 목록. 반경이 있고 검색어 갈래일 때만 쓴다.
     * <p>
     * <b>넉넉하게 잡는다.</b> 셀은 정답을 정하지 않는다 — 정답은 매퍼의
     * {@code latitude BETWEEN}이 정하고, 셀은 인덱스가 덜 걷게 하는 힌트일 뿐이다. 그래서
     * 한 칸 더 붙여도 결과가 안 바뀌고, 자바 {@code double}과 MySQL {@code DECIMAL} 산술이
     * 경계에서 어긋나 <b>사각형 가장자리 행이 조용히 빠지는 것</b>만 막는다.
     * <p>
     * 개수는 3km에서 7~8개, 10km에서 20개 안쪽이다.
     */
    private List<Integer> latCells(double latitude, int radiusMeters) {
        double halfHeight = (radiusMeters + 1) / METERS_PER_DEGREE_LATITUDE;
        int from = (int) Math.floor((latitude - halfHeight) * LAT_CELL_SCALE) - 1;
        int to = (int) Math.floor((latitude + halfHeight) * LAT_CELL_SCALE) + 1;
        List<Integer> cells = new ArrayList<>(to - from + 1);
        for (int cell = from; cell <= to; cell++) {
            cells.add(cell);
        }
        return cells;
    }

    /**
     * 한 계단을 밟는다. 검색어 갈래에만 셀 목록을 넘긴다 — 좌표·카테고리 갈래는 각자의
     * 인덱스가 이미 맞고, 셀을 넘기면 그 인덱스에 없는 컬럼이라 서버 필터만 늘어난다.
     */
    private List<StoreSummaryResponse> findStoresWithin(StoreSearchCondition cond, int radiusMeters) {
        List<Integer> cells = cond.getKeyword() == null
                ? null
                : latCells(cond.getLatitude(), radiusMeters);
        return storeMapper.findStores(withRadius(cond, radiusMeters), cells);
    }

    private StoreSearchCondition withRadius(StoreSearchCondition cond, int radiusMeters) {
        return StoreSearchCondition.builder()
                .keyword(cond.getKeyword())
                .categoryId(cond.getCategoryId())
                .latitude(cond.getLatitude())
                .longitude(cond.getLongitude())
                .radiusMeters(radiusMeters)
                .build();
    }

    /**
     * 검색어만 온(= 반경이 없는) 전국 검색. 계단식 사각형으로 좁혀 보고, 안 되면 전국을 훑는다.
     *
     * <h2>왜 계단이 먼저인가</h2>
     * 정렬 첫 키가 거리이므로 <b>300m 안에서 5건이 차면 그게 곧 전국의 정답</b>이다. 그리고
     * 사각형은 좌표 인덱스를, 전국은 FULLTEXT를 타는데 <b>둘의 비용이 서로 다른 것에 비례한다</b> —
     * 사각형은 좌표 주변 밀도에, 전국은 매칭 행수에 비례한다. 밀집 지역은 300m에서 끝나고
     * (강남역 '식당' 6.4ms), 희소 지역은 계단을 올라가지만 거기서는 넓혀도 싸다.
     *
     * <h2>왜 중간에 한 번 갈라지나</h2>
     * 계단이 항상 이기지는 않는다. 매칭이 적은 검색어는 전국 단계가 사각형 한 칸보다도 싸다 —
     * 강남역에서 '파리바게뜨'(전국 2,601건)의 전국 단계가 15.3ms인데 1km 사각형은 23ms다.
     * 그런 검색어를 계단에 태우면 손해라, {@link StoreMapper#countByFulltext}로 한 번 물어보고
     * {@link #FULLTEXT_ROUTING_THRESHOLD} 기준으로 가른다.
     * <p>
     * <b>어느 쪽으로 가도 결과는 같다.</b> 이 분기는 비용만 고른다.
     *
     * <h2>갈림길을 3km 뒤로 옮긴 이유 (2026-08-23)</h2>
     * 예전에는 <b>300m</b>가 실패하면 바로 갈랐다. 그런데 전국 매칭 수는 <b>계단을 몇 단
     * 밟게 될지를 말해주지 않는다</b> — 같은 1만 건이라도 지역 분포에 따라 갈린다.
     * <pre>
     *   '주유소' 10,917건 — 전국에 고르게 깔려 1~3km에서 끝난다
     *   '부산'   10,237건 — 부산에 몰려 있어 강남에서는 10km까지 올라간다
     * </pre>
     * 그래서 매칭 수로 가르면 '주유소'류가 전국으로 잘못 떨어진다. 운영 실측(1 VU, 2,000행)에서
     * 100ms를 넘긴 검색어 12종이 전부 임계값 <b>아래</b> 구간(1,257~10,917)이었다.
     * <p>
     * 앞 세 단이 싸다는 것이 이 변경의 근거다. '주유소' 계단 총비용을 밀도 단계별로 재면
     * <b>7단계 중 6단계가 20ms 이하</b>다(tier 2 3.8ms · tier 4 10.4ms · tier 5 18.6ms ·
     * tier 7만 113ms). 같은 검색어의 전국 단계는 138ms다. <b>세 단을 다 밟아보는 쪽이 싸다.</b>
     * <p>
     * 대가: '부산'처럼 지역 편중이 심한 검색어는 결국 전국으로 가면서 앞 세 단 비용을 더 낸다.
     * 최악이 밀집 좌표의 +113ms인데, 그건 좌표 인덱스가 위도만 좁히는 별개 문제다
     * (사각형 3,871행을 얻으려 위도 띠 32,329행을 훑는다).
     *
     * <h2>전국 단계는 성능이 아니라 정확성 요건이다</h2>
     * 마지막 계단까지 5건을 못 채웠으면 반드시 전국으로 가야 한다. 생략하면 5건 미만을 반환해
     * 응답이 바뀐다 — 계단은 같은 답을 싸게 얻으려는 내부 구현이지 검색 범위의 축소가 아니다.
     *
     * @param cond 반경이 {@code null}인 확정 조건. {@code keyword}는 {@code null}이 아니다
     */
    /** 캐시를 먼저 보고, 없으면 세어서 담는다. {@link #fulltextCountCache} 주석 참고. */
    private int fulltextMatchCount(String matchExpression) {
        Integer cached = fulltextCountCache.get(matchExpression);
        if (cached != null) {
            return cached;
        }
        int counted = storeMapper.countByFulltext(matchExpression);
        fulltextCountCache.put(matchExpression, counted);
        return counted;
    }

    /**
     * ⚠️ <b>2026-08-23 3km 상한 정책 이후 호출되지 않는다.</b> {@code searchStores}가 반경을 항상
     * 확정하므로 여기로 내려오는 경로가 없다. 정책을 되돌리면 그대로 살아난다 — 그때까지
     * 딸린 것들({@link #KEYWORD_CASCADE_STEPS_METERS} · {@link #FULLTEXT_ROUTING_THRESHOLD} ·
     * {@link #fulltextCountCache} · {@code buildMatchExpression} · 매퍼의 {@code countByFulltext}
     * /{@code findStoresByFulltext} · {@code ft_store_name} 인덱스)도 함께 남겨 둔다.
     * <p>
     * 정책이 굳으면 이 묶음을 통째로 걷어내고 인덱스(약 65MB)도 내린다 — 별건이다.
     */
    @SuppressWarnings("unused")
    private List<StoreSummaryResponse> findStoresByKeyword(StoreSearchCondition cond) {
        String matchExpression = buildMatchExpression(cond.getKeyword());

        // 갈림길 앞의 싼 계단들. 여기서 끝나면 count도 전국도 타지 않는다.
        List<StoreSummaryResponse> found = Collections.emptyList();
        for (int i = 0; i < KEYWORD_ROUTING_DECISION_INDEX; i++) {
            int step = KEYWORD_CASCADE_STEPS_METERS[i];
            found = findStoresWithin(cond, step);
            if (isSettled(found, step)) {
                return found;
            }
        }

        // 조각이 없으면 셀 것도 없다. 그때는 전국 단계가 LIKE 풀스캔이라 비싸므로 계단을 끝까지 밟는다.
        boolean climb = matchExpression == null
                || fulltextMatchCount(matchExpression) > FULLTEXT_ROUTING_THRESHOLD;

        if (climb) {
            for (int i = KEYWORD_ROUTING_DECISION_INDEX; i < KEYWORD_CASCADE_STEPS_METERS.length; i++) {
                int step = KEYWORD_CASCADE_STEPS_METERS[i];
                found = findStoresWithin(cond, step);
                if (isSettled(found, step)) {
                    return found;
                }
            }
        }
        return storeMapper.findStoresByFulltext(cond, matchExpression);
    }

    /**
     * 전국 단계에서 쓸 {@code MATCH ... AGAINST} 불리언 모드 식을 만든다.
     * 예: {@code 호텔/리조트} → {@code +"호텔" +"리조트"}
     *
     * <h2>이 메서드가 지켜야 하는 것 — MATCH는 LIKE의 상위집합이어야 한다</h2>
     * MATCH는 판정자가 아니라 <b>가속기</b>다. 판정은 SQL에 남아 있는 {@code LIKE}가 한다.
     * 그래서 MATCH가 걸러낸 집합이 LIKE의 답을 <b>빠짐없이 포함</b>하기만 하면 응답이 보존된다.
     * <p>
     * 키워드를 문자·숫자가 아닌 문자로 쪼개 조각을 AND로 묶는 것이 그 보장이다 — 어떤 상호명이
     * 키워드 전체를 포함하면 그 조각도 전부 포함하기 때문이다. <b>조각을 더 잘게 쪼개는 것은
     * 언제나 안전하다</b>(조건이 느슨해질 뿐이다). 반대로 ngram이 구분자로 보는 문자를 조각에
     * 남기면 거짓 음성이 난다.
     *
     * <h2>왜 조각으로 쪼개야만 하나</h2>
     * ngram은 구분자로 잘린 조각이 2글자 미만이면 토큰을 만들지 않는다. 키워드를 통째로 넘기면
     * <b>쿼리는 성공하고 결과만 조용히 0건이 된다</b>. 실측: {@code (주)}(LIKE 1,307건) ·
     * {@code e-편한}(50건) · {@code X-TOP}(1건)이 전부 0건이었다. 부하 데이터의 검색어 80만 건 중
     * <b>63%가 공백이나 기호를 포함</b>하므로 "한글 두 글자면 되겠지"로 넘기면 대부분이 0건이 된다.
     *
     * @return 불리언 모드 식. <b>쓸 조각이 하나도 없으면 {@code null}</b>이고, 그때 매퍼는
     *         MATCH를 붙이지 않는다({@code (주)}, {@code a b} 같은 키워드). 빈 문자열을 넘기면
     *         0건이 나와 결과가 바뀌므로 {@code null}이어야 한다
     */
    private String buildMatchExpression(String keyword) {
        StringBuilder expression = new StringBuilder();
        for (String fragment : keyword.split("[^\\p{IsAlphabetic}\\p{IsDigit}]+")) {
            if (fragment.length() < KEYWORD_MIN_LENGTH) {
                continue;
            }
            if (expression.length() > 0) {
                expression.append(' ');
            }
            // 조각에는 문자·숫자만 남아 있어 따옴표가 섞일 수 없다. 이스케이프가 필요 없는 이유다.
            expression.append("+\"").append(fragment).append('"');
        }
        return expression.length() == 0 ? null : expression.toString();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    /**
     * 명세: "이 기록은 조회 응답을 막지 않으며, 실패해도 결과는 그대로 200으로 반환합니다."
     * <p>
     * {@code searchHistoryService.record}는 예외를 그대로 던지는 계약이라({@code SearchHistoryService}
     * 참고) 여기서 감싼다. {@code REQUIRES_NEW}가 메서드 진입 전에 커넥션을 얻으므로 이 try-catch를
     * {@code record()} 내부로 옮길 수 없다 — 커넥션 풀 고갈 같은 실패는 본문 진입 전에 터진다.
     */
    private void recordSearchHistory(Long userId, String keyword) {
        try {
            searchHistoryService.record(userId, keyword);
        } catch (RuntimeException e) {
            log.warn("검색어 기록 실패. 조회 결과는 그대로 반환한다. userId={}, keyword={}", userId, keyword, e);
        }
    }
}
