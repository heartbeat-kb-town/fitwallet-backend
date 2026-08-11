package com.fitwallet.domain.benefit.exception;

import com.fitwallet.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BenefitErrorCode implements ErrorCode {

    STORE_ID_REQUIRED(HttpStatus.BAD_REQUEST, "storeId를 전달해 주세요."),
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 가맹점입니다."),
    AMOUNT_INVALID(HttpStatus.BAD_REQUEST, "결제 금액은 0보다 큰 숫자로 입력해 주세요.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
