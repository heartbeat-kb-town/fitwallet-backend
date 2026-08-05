package com.fitwallet.domain.card.dto;

/** 카드 상품의 통합 이용 실적 구간 유형. */
public enum CardUsageTierType {

    /** 전월 이용 실적 조건이 없다. */
    NO_REQUIREMENT,

    /** 0원보다 큰 실적 기준이 하나다. */
    SINGLE_TIER,

    /** 0원보다 큰 실적 기준이 둘 이상이다. */
    MULTIPLE_TIERS
}
