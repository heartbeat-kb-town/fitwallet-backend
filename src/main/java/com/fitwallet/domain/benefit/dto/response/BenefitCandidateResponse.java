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
}
