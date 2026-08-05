package com.fitwallet.domain.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionDetailResponse {
    private LocalDateTime approvedAt;
    private String storeName;
    private BigDecimal discountRate;
    private BigDecimal paidAmount;
    private BigDecimal benefitAmount;
}
