package com.fitwallet.domain.card.dto;

/** 이벤트 적용 대상의 범위. {@code card_event}의 XOR 대상 컬럼과 대응한다. */
public enum CardEventTargetType {

    /** 특정 카드 상품에 적용되는 이벤트. */
    CARD_PRODUCT,

    /** 카드사 전체 카드에 적용되는 이벤트. */
    ISSUER
}
