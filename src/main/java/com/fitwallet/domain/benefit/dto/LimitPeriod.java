package com.fitwallet.domain.benefit.dto;

/**
 * {@code benefit_limit.limit_period}의 CHECK 제약 값.
 * <p>
 * 상수 이름이 DB에 저장된 문자열과 같으므로 MyBatis 기본 {@code EnumTypeHandler}가
 * {@code name()} 기준으로 자동 변환한다. 커스텀 TypeHandler가 필요 없다.
 */
public enum LimitPeriod {

    /** 건당 한도. 결제 금액을 모르면 소진 여부를 판정할 수 없어 소진 판정 대상에서 제외한다. */
    PER_TRANSACTION,

    /** 일 한도. 오늘 00:00부터 집계한다. */
    DAY,

    /** 월 한도. 이달 1일 00:00부터 집계한다. */
    MONTH,

    /** 연 한도. 올해 1/1 00:00부터 집계한다. */
    YEAR
}
