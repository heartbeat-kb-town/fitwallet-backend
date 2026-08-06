package com.fitwallet.domain.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MissedBenefitInfo {
    private Long betterUserCardId;
    private BigDecimal alternativeDiscountAmount;
    private BigDecimal missedAmount;
}
