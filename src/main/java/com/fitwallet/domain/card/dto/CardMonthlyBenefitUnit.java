package com.fitwallet.domain.card.dto;

/** 카드 월간 혜택 응답에서 값과 한도를 표시하는 단위. */
public enum CardMonthlyBenefitUnit {

    /** 정률 혜택의 백분율. */
    PERCENT,

    /** 대한민국 원. */
    KRW,

    /** 혜택 서비스가 적립하는 포인트. */
    POINT,

    /** 혜택 적용 거래 횟수. */
    COUNT
}
