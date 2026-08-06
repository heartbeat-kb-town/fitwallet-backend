package com.fitwallet.domain.payment.dto;

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
public class PaymentResultSessionInfo {
    private Long paymentSessionId;
    private PaymentSessionStatus status;
    private Long storeId;
    private BigDecimal amount;
    private Long userCardId;
    private LocalDateTime updatedAt;
    private String failReason;
}