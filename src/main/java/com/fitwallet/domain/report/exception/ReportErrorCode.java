package com.fitwallet.domain.report.exception;

import com.fitwallet.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ReportErrorCode implements ErrorCode {
    INVALID_YEAR_MONTH(HttpStatus.BAD_REQUEST, "조회 기간이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String message;

    ReportErrorCode(HttpStatus status, String message) {
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