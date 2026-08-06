package com.fitwallet.domain.card.dto;

/** 카드 월간 혜택 한도의 사용 가능 상태. */
public enum CardMonthlyBenefitLimitStatus {

    /** 월 한도가 남아 있어 혜택을 더 받을 수 있다. */
    AVAILABLE,

    /** 월 한도를 모두 사용해 혜택을 더 받을 수 없다. */
    LIMIT_EXHAUSTED
}
