package com.fitwallet.domain.report.dto;

import com.fitwallet.global.common.code.SuccessCode;
import org.springframework.http.HttpStatus;

public enum ReportSuccessCode implements SuccessCode {
    BENEFIT_SUMMARY_FOUND(HttpStatus.OK, "리포트 요약을 조회했습니다.");

    private final HttpStatus status;
    private final String message;

    ReportSuccessCode(HttpStatus status, String message) {
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