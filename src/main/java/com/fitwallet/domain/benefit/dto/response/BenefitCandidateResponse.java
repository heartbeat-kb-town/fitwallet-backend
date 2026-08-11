package com.fitwallet.domain.benefit.dto.response;

import com.fitwallet.domain.benefit.dto.BenefitType;
import com.fitwallet.domain.benefit.dto.ValueType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 가맹점 스코프(브랜드·업종)에 매칭된 후보 혜택 한 건. {@code benefit_service}가 카드상품별로
 * 여러 전월실적 구간에 걸쳐 여러 행을 가지므로, 이 DTO도 구간 하나당 한 건이다.
 * <p>
 * {@code tierOk}는 전월실적이 이 구간을 통과했는지 여부다 — 구간을 통과하지 못한 행도
 * 결과에서 빼지 않는다({@code PREV_SPEND_NOT_MET} 판정에 그 자체가 필요하다).
 * <p>
 * MyBatis가 리플렉션으로 채우므로 {@code @Setter}는 붙이지 않는다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenefitCandidateResponse {

    private Long serviceId;

    /** null이면 {@code serviceId}로 tier를 직결한다({@code ck_benefit_tier_xor}). */
    private Long planGroupId;

    private String benefitName;
    private BenefitType benefitType;
    private ValueType valueType;
    private BigDecimal valueNumber;

    /** {@code benefitType=ACCUMULATE}일 때만 값이 있다. */
    private String currencyName;

    /** {@code benefitType=ACCUMULATE}일 때만 값이 있다. POINT 한도 소진량 환산에 쓴다. */
    private BigDecimal krwPerPoint;

    /** 전월실적이 이 혜택의 구간(min~max)을 통과했는지 여부. */
    private Boolean tierOk;

    /**
     * 건당 최소 이용금액. 이번 결제 1건이 이 금액 미만이면 혜택이 아예 발생하지 않는다.
     * "조건 없음"은 {@code null}이 아니라 {@code 0}이다.
     * <p>
     * ⚠️ 이름이 비슷한 {@code min_payment_amount}는 <b>전월실적</b> 하한이라 축이 다르다.
     * 이쪽만 "이번 결제 1건"을 본다.
     */
    private BigDecimal minTxAmount;

    /**
     * 건당 혜택 캡(원화). {@code null}이면 캡이 없다.
     * <p>
     * 건당 캡의 정본은 이 컬럼이다 — {@code benefit_limit}에도
     * {@code limit_period='PER_TRANSACTION'}으로 표현할 자리가 있지만 시드에 0건이라 쓰지 않는다.
     */
    private BigDecimal perTxLimitAmount;
}
