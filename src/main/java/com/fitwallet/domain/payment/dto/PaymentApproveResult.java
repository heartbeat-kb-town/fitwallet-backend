package com.fitwallet.domain.payment.dto;

import com.fitwallet.domain.payment.dto.response.PaymentResultResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentApproveResult {
    private PaymentResultResponse response;
    private boolean alreadyProcessed;
}
