package com.fitwallet.domain.user.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import javax.validation.constraints.NotBlank;

/** 일반 로그인에 필요한 아이디와 비밀번호를 받는다. */
@Getter
@NoArgsConstructor
public class UserLoginRequest {
    @NotBlank(message = "아이디는 필수입니다.")
    private String loginId;

    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;
}
