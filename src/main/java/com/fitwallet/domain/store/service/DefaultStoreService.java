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

    /** DB에서 오지 않는 서비스 정책값. {@code StoreMapper.findPopularKeywords}의 집계 기간과 맞춘다. */
    private static final int POPULAR_PERIOD_DAYS = 7;

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
        // 계단식 반경은 주변 조회 모드에만 건다. 검색어가 있으면 LIKE가 먼저 대부분을 쳐내
        // 단계를 늘리는 만큼 그대로 손해다(강남역 '스타벅스' 실측: 0.05 + 0.12 + 0.49s).
        List<StoreSummaryResponse> stores = nearbyMode
                ? findStoresByCascadingRadius(confirmedCondition)
                : storeMapper.findStores(confirmedCondition);

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
