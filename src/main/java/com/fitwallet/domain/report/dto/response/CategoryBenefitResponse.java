package com.fitwallet.domain.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CategoryBenefitResponse {
    private Long categoryId;
    private String categoryName;
    private BigDecimal benefitAmount;
    private BigDecimal spendAmount;
}
