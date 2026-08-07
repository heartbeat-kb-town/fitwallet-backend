package com.fitwallet.domain.report.exception;

import com.fitwallet.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CardBenefitErrorCode implements ErrorCode {
    CARD_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 카드를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    CardBenefitErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getMessage() {
        return message;
    }
}