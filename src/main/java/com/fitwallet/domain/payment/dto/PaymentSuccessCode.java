package com.fitwallet.domain.payment.dto;

import com.fitwallet.global.common.code.SuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentSuccessCode implements SuccessCode {

    PIN_VERIFIED(HttpStatus.OK, "비밀번호가 확인되었습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
