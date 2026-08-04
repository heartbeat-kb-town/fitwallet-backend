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
public class QrGenerateResponse {
    private String qrToken;
    private PaymentSessionStatus status;
    private int expiresIn;
}
