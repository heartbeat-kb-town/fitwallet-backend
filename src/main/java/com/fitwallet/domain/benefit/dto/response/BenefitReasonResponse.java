package com.fitwallet.domain.benefit.dto.response;

import com.fitwallet.domain.benefit.dto.BenefitReasonCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenefitReasonResponse {

    private BenefitReasonCode code;
    private String message;
}
