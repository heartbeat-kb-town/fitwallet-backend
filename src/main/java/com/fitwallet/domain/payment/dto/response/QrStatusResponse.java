package com.fitwallet.domain.payment.dto.response;

import com.fitwallet.domain.payment.dto.PaymentSessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QrStatusResponse {
    private PaymentSessionStatus status;
    private String paymentId;
}
