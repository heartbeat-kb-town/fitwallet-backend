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
}
