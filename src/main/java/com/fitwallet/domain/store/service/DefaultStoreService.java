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

import java.util.List;

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
        if (keyword == null && categoryId == null) {
            throw new BusinessException(StoreErrorCode.KEYWORD_OR_CATEGORY_REQUIRED);
        }

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
        List<StoreSummaryResponse> stores = storeMapper.findStores(confirmedCondition);

        if (keyword != null) {
            recordSearchHistory(userId, keyword);
        }

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
