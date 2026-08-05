package com.fitwallet.domain.card.dto;

/**
 * {@code card_product.card_type}의 CHECK 제약 값.
 * <p>
 * 상수 이름이 DB에 저장된 문자열과 같으므로 MyBatis 기본 {@code EnumTypeHandler}가
 * {@code name()} 기준으로 자동 변환한다. 커스텀 TypeHandler가 필요 없다.
 */
public enum CardType {

    /** 신용카드. credit_limit, scheduled_payment_amount를 쓴다. */
    CREDIT,

    /** 체크카드. bank_name, balance를 쓴다. */
    DEBIT
}
