package com.fitwallet.domain.user.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Getter
@NoArgsConstructor
public class SignUpRequest {
    @NotBlank(message = "이름은 필수입니다.")
    @Size(max = 50)
    private String name;

    @NotBlank(message = "아이디는 필수입니다.")
    @Size(max = 255)
    private String loginId;

    @NotBlank(message = "휴대폰 번호는 필수입니다.")
    @Size(max = 20)
    private String phone;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
    private String password;

    @NotBlank(message = "비밀번호 확인은 필수입니다.")
    private String passwordConfirm;

    private boolean marketingAgreed;
}
