package com.fitwallet.domain.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenefitSummaryResponse {
    private BigDecimal totalReceivedBenefit;
    private BigDecimal totalMissedBenefit;
    private List<CategoryBenefitResponse> categories;
    private List<CardRecommendationResponse> recommendations;
}
