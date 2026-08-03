package com.fitwallet.domain.payment.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
@NoArgsConstructor
public class QrGenerateRequest {
    @NotNull(message = "카드를 선택해주세요.")
    private Long userCardId;

    @NotBlank(message = "PIN 인증 정보가 필요합니다.")
    private String pinAuthId;
}
