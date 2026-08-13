package com.fitwallet.domain.payment.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class StoreQrScanRequest {
    @NotBlank(message = "QR 토큰이 필요합니다.")
    private String storeQrToken;

    @NotBlank(message = "PIN 인증 정보가 필요합니다.")
    private String pinAuthId;

    @NotNull(message = "카드를 선택해주세요.")
    private Long userCardId;

    @NotNull(message = "결제 금액이 필요합니다.")
    @Positive(message = "결제 금액은 0보다 커야 합니다.")
    private BigDecimal amount;
}