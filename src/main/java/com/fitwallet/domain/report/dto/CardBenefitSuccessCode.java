package com.fitwallet.domain.report.dto;

import com.fitwallet.global.common.code.SuccessCode;
import org.springframework.http.HttpStatus;

public enum CardBenefitSuccessCode implements SuccessCode {
    CARD_BENEFIT_DETAIL_FOUND(HttpStatus.OK, "카드별 받은 혜택 상세를 조회했습니다.");

    private final HttpStatus status;
    private final String message;

    CardBenefitSuccessCode(HttpStatus status, String message) {
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