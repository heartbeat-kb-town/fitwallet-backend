package com.fitwallet.domain.store.dto;

import com.fitwallet.global.common.code.SuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StoreSuccessCode implements SuccessCode {

    STORE_SEARCH_FOUND(HttpStatus.OK, "가맹점 조회에 성공했습니다."),
    SEARCH_KEYWORDS_FOUND(HttpStatus.OK, "검색어 조회에 성공했습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
