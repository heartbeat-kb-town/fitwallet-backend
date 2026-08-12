package com.fitwallet.domain.report.dto;

import com.fitwallet.global.common.code.SuccessCode;
import org.springframework.http.HttpStatus;

public enum MissedBenefitSuccessCode implements SuccessCode {
    MISSED_BENEFIT_DETAIL_FOUND(HttpStatus.OK, "놓친 혜택 리포트를 조회했습니다.");

    private final HttpStatus status;
    private final String message;

    MissedBenefitSuccessCode(HttpStatus status, String message) {
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
