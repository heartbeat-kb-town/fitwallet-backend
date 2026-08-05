package com.fitwallet.domain.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionDetailResponse {
    private String approvedAt;
    private String storeName;
    private Integer discountRate;
    private BigDecimal paidAmount;
    private BigDecimal benefitAmount;
}
