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

    /** 이 가맹점 스코프에 걸리는 혜택이 없다. */
    NO_BENEFIT_FOR_STORE
}
