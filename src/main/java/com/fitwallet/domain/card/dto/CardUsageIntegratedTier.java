package com.fitwallet.domain.card.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 카드상품 전체의 실적 경계를 금액순으로 합친 내부 구간. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardUsageIntegratedTier {

    private Integer tierOrder;
    private String tierName;
    private BigDecimal minimumAmount;
}
