package com.fitwallet.domain.benefit.dto;

/**
 * {@code benefit_service.benefit_type}의 CHECK 제약 값.
 * <p>
 * 상수 이름이 DB에 저장된 문자열과 같으므로 MyBatis 기본 {@code EnumTypeHandler}가
 * {@code name()} 기준으로 자동 변환한다. 커스텀 TypeHandler가 필요 없다.
 */
public enum BenefitType {

    /** 할인. 단위는 원. */
    CASHBACK,

    /** 적립. 단위는 포인트이며 {@code point_currency_id}가 항상 함께 채워진다. */
    ACCUMULATE
}
