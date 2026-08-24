package com.fitwallet.domain.payment.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class QrGenerateRequest {
    @NotNull(message = "카드를 선택해주세요.")
    private Long userCardId;

    @NotBlank(message = "PIN 인증 정보가 필요합니다.")
    private String pinAuthId;

    //@Positive는 null이면 검증을 통과시킴 (선택값)
    @Positive(message = "결제 금액은 0보다 커야 합니다.")
    private BigDecimal amount;
}
