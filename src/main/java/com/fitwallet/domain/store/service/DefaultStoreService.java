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

import java.util.Collections;
import java.util.List;
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
     * 계단을 더 밟을지, 전국 단계로 바로 갈지 가르는 전국 매칭 수 기준.
     * <p>
     * <b>어느 쪽으로 가도 응답은 같다.</b> 이 값은 비용에만 영향을 준다.
     * <p>
     * 두 경로의 비용이 서로 다른 것에 비례하기 때문에 교차점이 생긴다 — 전국 단계는 <b>매칭
     * 행수</b>에(약 2.9ms/1,000행), 사각형 단계는 <b>좌표 주변 밀도</b>에 비례한다. 그래서 가장
     * 밀집한 좌표(강남역, 1km 안 18,772행 → 23ms)를 기준으로 잡는다. 그보다 희소한 좌표에서는
     * 사각형이 더 싸지므로 이 기준이 항상 안전한 쪽으로 기운다.
     * <p>
     * 강남역 실측으로 경계를 좁혔다(왕복 합계, 운영 환산):
     * <pre>
     *   '부산'  10,237건 → 전국 88ms  · 계단 178ms(1km에서 4건이라 3km까지 올라간다)
     *   '미용'  16,905건 → 전국 137ms · 계단  87ms
     *   '치킨'  20,793건 → 전국 158ms · 계단  90ms
     * </pre>
     * 교차점이 1.0만~1.7만 사이다. 13,000은 그 가운데다.
     * <p>
     * ⚠️ 이 값을 <b>"남은 예산에 전국 단계가 들어가는가"로 정하면 안 된다.</b> 처음에 그렇게
     * 5,000을 잡았다가 '제주'(9,710)·'부산'(10,237)이 불필요하게 계단을 타 SLO를 넘겼다.
     * 올바른 질문은 "전국이 계단보다 싼가"다.
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

        // 키워드가 없으면 주변 조회 모드다. 카테고리도 없는(= 좌표만 온) 요청도 여기 포함된다 —
        // 앱이 검색 화면에 처음 들어와 아무것도 입력하지 않은 상태에서 내 주변 가맹점을 보여주는 경우다.
        boolean nearbyMode = keyword == null;
        Integer requestedRadius = cond.getRadiusMeters();
        if (requestedRadius != null && requestedRadius <= 0) {
            throw new BusinessException(StoreErrorCode.INVALID_RADIUS);
        }
        if (nearbyMode && requestedRadius != null && requestedRadius > NEARBY_MAX_RADIUS_METERS) {
            throw new BusinessException(StoreErrorCode.RADIUS_EXCEEDED);
        }

        if (!Boolean.TRUE.equals(storeMapper.findLocationAgreed(userId))) {
            throw new BusinessException(StoreErrorCode.LOCATION_AGREEMENT_REQUIRED);
        }

        if (categoryId != null && !storeMapper.existsCategory(categoryId)) {
            throw new BusinessException(StoreErrorCode.CATEGORY_NOT_FOUND);
        }

        // 삼항 연산자를 중첩하면 Integer/int가 섞여 requestedRadius가 null일 때 자동 언박싱으로
        // NPE가 난다(JLS 이항 수치 승격). if-else로 풀어 박싱 타입을 그대로 유지한다.
        Integer confirmedRadius = requestedRadius;
        if (nearbyMode && requestedRadius == null) {
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
        List<StoreSummaryResponse> stores = confirmedRadius != null
                ? findStoresByCascadingRadius(confirmedCondition)
                : findStoresByKeyword(confirmedCondition);

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
            found = storeMapper.findStores(withRadius(cond, step));
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
     * 그런 검색어를 계단에 태우면 손해라, 300m가 실패한 시점에 {@link StoreMapper#countByFulltext}로
     * 한 번 물어보고 {@link #FULLTEXT_ROUTING_THRESHOLD} 기준으로 가른다.
     * <p>
     * <b>어느 쪽으로 가도 결과는 같다.</b> 이 분기는 비용만 고른다.
     *
     * <h2>전국 단계는 성능이 아니라 정확성 요건이다</h2>
     * 마지막 계단까지 5건을 못 채웠으면 반드시 전국으로 가야 한다. 생략하면 5건 미만을 반환해
     * 응답이 바뀐다 — 계단은 같은 답을 싸게 얻으려는 내부 구현이지 검색 범위의 축소가 아니다.
     *
     * @param cond 반경이 {@code null}인 확정 조건. {@code keyword}는 {@code null}이 아니다
     */
    private List<StoreSummaryResponse> findStoresByKeyword(StoreSearchCondition cond) {
        String matchExpression = buildMatchExpression(cond.getKeyword());

        int firstStep = KEYWORD_CASCADE_STEPS_METERS[0];
        List<StoreSummaryResponse> found = storeMapper.findStores(withRadius(cond, firstStep));
        if (isSettled(found, firstStep)) {
            return found;
        }

        // 조각이 없으면 셀 것도 없다. 그때는 전국 단계가 LIKE 풀스캔이라 비싸므로 계단을 끝까지 밟는다.
        boolean climb = matchExpression == null
                || storeMapper.countByFulltext(matchExpression) > FULLTEXT_ROUTING_THRESHOLD;

        if (climb) {
            for (int i = 1; i < KEYWORD_CASCADE_STEPS_METERS.length; i++) {
                int step = KEYWORD_CASCADE_STEPS_METERS[i];
                found = storeMapper.findStores(withRadius(cond, step));
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
