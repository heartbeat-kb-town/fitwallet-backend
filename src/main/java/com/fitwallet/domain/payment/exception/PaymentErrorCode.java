package com.fitwallet.domain.payment.exception;

import com.fitwallet.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    PIN_MISMATCH(HttpStatus.UNAUTHORIZED, "결제 비밀번호가 일치하지 않습니다."),
    PIN_AUTH_ID_INVALID(HttpStatus.BAD_REQUEST, "인증 정보가 유효하지 않습니다. 비밀번호를 다시 입력해주세요.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}