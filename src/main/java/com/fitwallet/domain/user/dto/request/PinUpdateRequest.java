package com.fitwallet.domain.user.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Getter
@NoArgsConstructor
public class PinUpdateRequest {

    @NotBlank(message = "현재 결제 PIN은 필수입니다.")
    @Pattern(regexp = "\\d{6}", message = "결제 PIN은 6자리 숫자입니다.")
    private String currentPin;

    @NotBlank(message = "새로운 결제 PIN은 필수입니다.")
    @Pattern(regexp = "\\d{6}", message = "결제 PIN은 6자리 숫자입니다.")
    private String newPin;

    @NotBlank(message = "결제 PIN 확인은 필수입니다.")
    @Pattern(regexp = "\\d{6}", message = "결제 PIN 확인은 6자리 숫자입니다.")
    private String newPinConfirm;

}
