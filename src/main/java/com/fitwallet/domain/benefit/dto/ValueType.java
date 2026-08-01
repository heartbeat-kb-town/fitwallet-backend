package com.fitwallet.domain.benefit.dto;

/**
 * {@code benefit_service.value_type}의 CHECK 제약 값.
 * <p>
 * 상수 이름이 DB에 저장된 문자열과 같으므로 MyBatis 기본 {@code EnumTypeHandler}가
 * {@code name()} 기준으로 자동 변환한다. 커스텀 TypeHandler가 필요 없다.
 */
public enum ValueType {

    /** 정액. {@code value_number}를 원 또는 포인트 그대로 쓴다. */
    FIXED,

    /** 정률(%). {@code value_number}가 백분율 값이다. */
    RATE
}
