package com.fitwallet.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenCookieProviderTest {

    @Test
    void create는_이름_경로_보안속성을_갖춘_쿠키를_만든다() {
        RefreshTokenCookieProvider cookieProvider = new RefreshTokenCookieProvider(true);

        ResponseCookie cookie = cookieProvider.create("refresh-token-value", 1_209_600L);

        assertThat(cookie.getName()).isEqualTo("refreshToken");
        assertThat(cookie.getValue()).isEqualTo("refresh-token-value");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofSeconds(1_209_600L));
    }
}
