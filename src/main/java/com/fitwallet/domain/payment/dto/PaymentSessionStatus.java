package com.fitwallet.domain.payment.dto;

/**
 * {@code payment_session.status}의 CHECK 제약 값.
 * MyBatis 기본 {@code EnumTypeHandler}가 name() 기준으로 자동 변환한다.
 */
public enum PaymentSessionStatus {
    PENDING,
    SCANNED,
    PROCESSING,
    COMPLETED,
    EXPIRED,
    FAILED
}
