package com.fitwallet.domain.card.dto;

/** 카드 거래의 승인 상태. DDL의 transaction_status CHECK 값과 동일하게 유지한다. */
public enum CardTransactionStatus {
    APPROVED,
    CANCELED
}
