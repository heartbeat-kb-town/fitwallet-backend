package com.fitwallet.domain.card.dto;

/**
 * 카드 유형에 따른 결제 내역 화면의 상단 금액 종류.
 */
public enum CardTransactionSummaryType {

    /** 현재 월 1일부터 전날까지 승인 거래의 final_amount를 합산한 신용카드 결제예정금액. */
    SCHEDULED_PAYMENT,

    /** 거래를 합산한 과거 월 신용카드 또는 체크카드의 월 결제 이용금액. */
    MONTHLY_PAYMENT_AMOUNT
}
