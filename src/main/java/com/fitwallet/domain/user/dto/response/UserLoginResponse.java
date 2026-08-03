package com.fitwallet.domain.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 로그인 성공 응답의 data에 담기는 Access Token. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLoginResponse {
    private String accessToken;
}
