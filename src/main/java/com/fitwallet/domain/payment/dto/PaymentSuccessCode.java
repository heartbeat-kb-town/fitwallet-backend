package com.fitwallet.domain.payment.dto;

import com.fitwallet.global.common.code.SuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentSuccessCode implements SuccessCode {

    PIN_VERIFIED(HttpStatus.OK, "결제 비밀번호가 확인되었습니다."),
    QR_CREATED(HttpStatus.CREATED, "QR 코드가 생성되었습니다."),
    QR_STATUS_CREATED(HttpStatus.OK, "QR 생성 완료, 스캔 대기 중입니다."),
    QR_STATUS_SCANNED(HttpStatus.OK, "가맹점에서 QR을 스캔했습니다."),
    PAYMENT_PROCESSING(HttpStatus.OK, "결제 처리 중입니다."),
    PAYMENT_COMPLETED(HttpStatus.OK, "결제가 완료되었습니다."),
    PAYMENT_FAILED(HttpStatus.OK, "결제 승인에 실패했습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
