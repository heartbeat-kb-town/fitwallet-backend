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
public class CategoryTransactionGroupResponse {
    private Long categoryId;
    private String categoryName;
    private Integer usageCount;
    private BigDecimal benefitAmount;
    private List<TransactionDetailResponse> transactions;
}
