package com.fitwallet.domain.card.dto;

/**
 * 카드 유형에 따른 결제 내역 화면의 상단 금액 종류.
 */
public enum CardTransactionSummaryType {

    /** 전날까지 반영하여 저장한 신용카드 결제 이용금액. */
    SCHEDULED_PAYMENT,

    /** 거래를 합산한 과거 월 신용카드 또는 체크카드의 월 결제 이용금액. */
    MONTHLY_PAYMENT_AMOUNT
}
