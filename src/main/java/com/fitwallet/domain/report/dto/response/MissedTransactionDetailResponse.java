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
public class MissedTransactionDetailResponse {
    private LocalDateTime approvedAt;
    private String storeName;
    private String usedCardName;
    private BigDecimal paidAmount;
    private String alternativeCardName;
    private BigDecimal discountRate;
    private BigDecimal diffAmount;
}