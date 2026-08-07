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
public class MissedCategoryDetailResponse {
    private BigDecimal totalMissedBenefit;
    private BigDecimal appUnusedAmount;
    private BigDecimal cardMismatchAmount;
    private String lossType;
    private List<MissedCategoryGroupResponse> categories;
}