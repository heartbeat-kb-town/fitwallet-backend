package com.fitwallet.domain.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로그인 서비스가 컨트롤러에 전달하는 토큰 발급 결과.
 * Refresh Token은 응답 data에 노출하지 않고 HttpOnly 쿠키로 전달한다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLoginTokenResponse {

    private String accessToken;
    private String refreshToken;
    private long refreshTokenExpirationSeconds;

}
