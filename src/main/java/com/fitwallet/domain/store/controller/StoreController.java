package com.fitwallet.domain.store.controller;

import com.fitwallet.domain.store.dto.StoreSuccessCode;
import com.fitwallet.domain.store.dto.request.StoreSearchCondition;
import com.fitwallet.domain.store.dto.response.StoreKeywordsResponse;
import com.fitwallet.domain.store.dto.response.StoreSearchResponse;
import com.fitwallet.domain.store.service.StoreService;
import com.fitwallet.global.common.annotation.LoginUserId;
import com.fitwallet.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가맹점 도메인 컨트롤러. 사용자 식별자는 {@link LoginUserId}로만 받는다.
 * 에러 응답은 여기서 만들지 않는다 — {@code GlobalExceptionHandler}가 변환한다.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    /**
     * {@link StoreSearchCondition}은 {@code @Setter}가 금지(§4)라 {@code WebDataBinder} 기본값
     * (JavaBean 프로퍼티, 즉 setter 기반) 바인딩으로는 필드가 전부 {@code null}로 남는다(예외도
     * 나지 않아 조용히 실패한다). {@code initDirectFieldAccess()}로 필드에 직접 꽂도록 바꾼다.
     */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.initDirectFieldAccess();
    }

    @GetMapping("/store/search")
    public ResponseEntity<ApiResponse<StoreSearchResponse>> searchStores(
            @LoginUserId Long userId,
            @ModelAttribute StoreSearchCondition cond) {

        return ApiResponse.of(StoreSuccessCode.STORE_SEARCH_FOUND,
                storeService.searchStores(userId, cond));
    }

    @GetMapping("/store/keywords")
    public ResponseEntity<ApiResponse<StoreKeywordsResponse>> findKeywords(@LoginUserId Long userId) {
        return ApiResponse.of(StoreSuccessCode.SEARCH_KEYWORDS_FOUND, storeService.findKeywords(userId));
    }
}
