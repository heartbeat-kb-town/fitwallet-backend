package com.fitwallet.domain.card.dto;

import com.fitwallet.domain.benefit.dto.BenefitType;
import com.fitwallet.domain.benefit.dto.ValueType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 카드상품의 혜택과 그 혜택에 연결된 원본 실적 구간을 함께 담는 내부 조회 결과. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardUsageBenefitRule {

    private Long benefitId;
    private Long planGroupId;
    private String benefitName;
    private BenefitType benefitType;
    private ValueType valueType;
    private BigDecimal valueNumber;
    private BigDecimal benefitMinimumAmount;
    private BigDecimal benefitMaximumAmount;
    private String pointCurrencyName;
    private Long sourceTierId;
    private Integer sourceTierOrder;
    private BigDecimal tierMinimumAmount;
    private BigDecimal tierMaximumAmount;
}
