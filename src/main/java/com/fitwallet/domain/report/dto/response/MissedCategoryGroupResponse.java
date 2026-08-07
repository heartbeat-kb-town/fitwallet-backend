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
public class MissedCategoryGroupResponse {
    private Long categoryId;
    private String categoryName;
    private Integer missedCount;
    private BigDecimal missedAmount;
    private List<MissedTransactionDetailResponse> transactions;
}