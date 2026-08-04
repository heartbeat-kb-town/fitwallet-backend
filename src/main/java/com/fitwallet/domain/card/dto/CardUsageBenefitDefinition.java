package com.fitwallet.domain.card.dto;

import com.fitwallet.domain.benefit.dto.BenefitType;
import com.fitwallet.domain.benefit.dto.ValueType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 원본 구간 중복을 제거한 카드상품 혜택 정의. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class CardUsageBenefitDefinition {

    private Long benefitId;
    private Long planGroupId;
    private String benefitName;
    private BenefitType benefitType;
    private ValueType valueType;
    private BigDecimal valueNumber;
    private BigDecimal minimumAmount;
    private BigDecimal maximumAmount;
    private String pointCurrencyName;
}
