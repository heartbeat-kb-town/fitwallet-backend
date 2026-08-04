package com.fitwallet.domain.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QrSessionInfo {
    private PaymentSessionStatus status;
    private String paymentId;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
