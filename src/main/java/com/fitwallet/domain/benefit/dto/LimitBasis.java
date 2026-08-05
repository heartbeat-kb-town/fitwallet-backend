package com.fitwallet.domain.benefit.dto;

/**
 * {@code benefit_limit.limit_basis}의 CHECK 제약 값.
 * <p>
 * 상수 이름이 DB에 저장된 문자열과 같으므로 MyBatis 기본 {@code EnumTypeHandler}가
 * {@code name()} 기준으로 자동 변환한다. 커스텀 TypeHandler가 필요 없다.
 */
public enum LimitBasis {

    /** 할인 한도. 단위는 원. */
    AMOUNT,

    /** 적립 한도. 단위는 포인트(원 환산값을 {@code krw_per_point}로 되돌려 비교한다). */
    POINT,

    /** 혜택 횟수 한도. 단위는 건. */
    COUNT
}
