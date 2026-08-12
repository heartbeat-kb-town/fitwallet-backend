package com.fitwallet.domain.benefit.dto;

/**
 * {@link CardBenefitStatus#CONDITION_NOT_MET}·{@link CardBenefitStatus#NO_BENEFIT}일 때
 * 그 사유를 나타내는 payload 안의 도메인 값. DB CHECK 값이 아니다.
 */
public enum BenefitReasonCode {

    /** 조건을 만족하는 혜택은 있으나 한도가 소진됐다. */
    LIMIT_EXHAUSTED,

    /** 전월실적이 어떤 구간에도 들지 못했다. */
    PREV_SPEND_NOT_MET,

    /**
     * 이번 결제 1건의 금액이 건당 최소 이용금액({@code benefit_service.min_tx_amount})에 못 미친다.
     * 전월실적 하한({@code min_payment_amount})과는 축이 다르다 — 이쪽만 "이번 결제 1건"을 본다.
     */
    MIN_TX_AMOUNT_NOT_MET,

    /** 이 가맹점 스코프에 걸리는 혜택이 없다. */
    NO_BENEFIT_FOR_STORE
}
