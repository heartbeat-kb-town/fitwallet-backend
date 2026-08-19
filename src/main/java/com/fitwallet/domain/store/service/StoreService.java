package com.fitwallet.domain.store.service;

import com.fitwallet.domain.store.dto.request.StoreSearchCondition;
import com.fitwallet.domain.store.dto.response.StoreKeywordsResponse;
import com.fitwallet.domain.store.dto.response.StoreSearchResponse;

/**
 * 컨트롤러는 이 인터페이스에만 의존한다. 구현체는 {@link DefaultStoreService}.
 */
public interface StoreService {

    StoreSearchResponse searchStores(Long userId, StoreSearchCondition cond);

    StoreKeywordsResponse findKeywords(Long userId);

    /**
     * 인기 검색어를 다시 집계해 {@code popular_keyword}에 채운다.
     * <p>
     * <b>요청 경로에서 호출하지 않는다.</b> 스케줄러만 부른다 — 요청마다 집계하지 않으려고
     * 만든 것이 이 메서드이기 때문이다. 주기와 배선은 구현체와 {@code root-context.xml} 참고.
     */
    void refreshPopularKeywords();

    void deleteKeyword(Long userId, Long searchHistoryId);

    void deleteAllKeywords(Long userId);
}
