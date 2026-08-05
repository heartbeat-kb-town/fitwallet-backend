package com.fitwallet.domain.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fitwallet.domain.payment.dto.PaymentSessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResultResponse {
    private String paymentId;
    private Long paymentTransactionId;
    private PaymentSessionStatus status;
    private String storeName;
    private String paymentMethod;
    private BigDecimal amount;
    private BigDecimal expectedBenefitAmount;
    private LocalDateTime paidAt;
    private String failReason;
}