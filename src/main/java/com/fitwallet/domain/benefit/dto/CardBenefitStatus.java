package com.fitwallet.domain.benefit.dto;

/**
 * 카드 하나의 예상 혜택 판정 결과. DB CHECK 값이 아니라 응답 payload 안의 도메인 값이다.
 */
public enum CardBenefitStatus {

    /** 조건을 만족하는 혜택이 있고 한도도 남아 있다. */
    AVAILABLE,

    /** 스코프에 걸리는 혜택은 있으나 전월실적 미달이거나 한도가 소진됐다. */
    CONDITION_NOT_MET,

    /** 이 가맹점 스코프에 걸리는 혜택이 아예 없다. */
    NO_BENEFIT
}
