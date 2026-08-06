package com.fitwallet.domain.payment.exception;

import com.fitwallet.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    PIN_MISMATCH(HttpStatus.UNAUTHORIZED, "결제 비밀번호가 일치하지 않습니다."),
    PIN_AUTH_ID_INVALID(HttpStatus.BAD_REQUEST, "인증 정보가 유효하지 않습니다. 비밀번호를 다시 입력해주세요."),
    QR_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 QR 세션입니다."),
    QR_EXPIRED(HttpStatus.GONE, "QR 세션이 만료되었습니다. QR을 다시 생성해주세요."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 내역을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}