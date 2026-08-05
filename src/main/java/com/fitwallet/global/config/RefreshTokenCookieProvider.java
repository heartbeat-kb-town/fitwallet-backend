package com.fitwallet.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Refresh Token 쿠키의 생성과 삭제를 담당한다.
 * 로그인·재발급·로그아웃이 같은 쿠키 설정(이름·경로·보안 옵션)을 공유하므로 한 곳에 모은다.
 */
@Component
public class RefreshTokenCookieProvider {

    private static final String COOKIE_NAME = "refreshToken";
    private static final String SAME_SITE = "Strict";
    private static final String PATH = "/";
    private final boolean secure;

    public RefreshTokenCookieProvider(@Value("${cookie.refresh-token-secure}") boolean secure) {
        this.secure = secure;
    }

    /** 로그인·재발급 시 Refresh Token을 담은 쿠키를 만든다. */
    public ResponseCookie create(String refreshToken, long maxAgeSeconds) {
        return baseCookie(refreshToken)
                .maxAge(maxAgeSeconds)
                .build();
    }

    /** 로그아웃 시 같은 이름·경로·보안 설정으로 빈 값 + maxAge(0)을 내려 쿠키를 지운다. */
    public ResponseCookie clear() {
        return baseCookie("")
                .maxAge(0)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(SAME_SITE)
                .path(PATH);
    }
}
