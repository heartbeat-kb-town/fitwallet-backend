package com.fitwallet.domain.payment.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

@Getter
@NoArgsConstructor
public class PinVerifyRequest {

    @NotNull(message = "카드를 선택해주세요.")
    private Long userCardId;

    @Pattern(regexp = "\\d{6}", message = "결제 비밀번호는 6자리 숫자입니다.")
    private String paymentPin;
}
