package com.fitwallet.domain.card.dto;

/** 선택 월의 인정 실적에 따른 카드 혜택 달성 상태. */
public enum CardUsagePerformanceStatus {

    /** 전월 이용 실적 조건이 없다. */
    NO_REQUIREMENT,

    /** 첫 번째 실적 기준에 도달하지 못했다. */
    INSUFFICIENT,

    /** 첫 번째 실적 기준 이상을 달성했다. */
    ACHIEVED
}
