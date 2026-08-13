package com.fitwallet.domain.report.dto;

/**
 * 혜택이 원화 할인인지 포인트 적립인지 구분한다.
 * DDL {@code benefit_service.benefit_type}의 CHECK 값 그대로다.
 * 스키마가 {@code (benefit_type='ACCUMULATE') = (point_currency_id IS NOT NULL)} 를 강제하므로
 * ACCUMULATE는 항상 포인트(단위 P), CASHBACK은 항상 원화(단위 ₩)다.
 */
public enum BenefitType {
    CASHBACK,
    ACCUMULATE
}
