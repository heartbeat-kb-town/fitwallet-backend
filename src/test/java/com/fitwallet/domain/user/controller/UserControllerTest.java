package com.fitwallet.domain.user.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitwallet.domain.user.dto.response.TokenReissueResponse;
import com.fitwallet.domain.user.dto.response.UserLoginTokenResponse;
import com.fitwallet.domain.user.service.UserService;
import com.fitwallet.global.config.RefreshTokenCookieProvider;
import com.fitwallet.global.exception.BusinessException;
import com.fitwallet.global.exception.CommonErrorCode;
import com.fitwallet.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller 테스트. Service와 쿠키 발급을 목킹해 응답 바디·쿠키 헤더 형태만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private RefreshTokenCookieProvider refreshTokenCookieProvider;

    @Test
    void 로그인_성공시_응답_바디에는_AccessToken만_담기고_RefreshToken은_쿠키로만_내려간다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(userService, refreshTokenCookieProvider))
                .build();

        given(userService.login(ArgumentMatchers.any())).willReturn(
                UserLoginTokenResponse.builder()
                        .accessToken("access-token-value")
                        .refreshToken("refresh-token-value")
                        .refreshTokenExpirationSeconds(1_209_600L)
                        .build()
        );
        ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", "refresh-token-value")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(1_209_600L)
                .build();
        given(refreshTokenCookieProvider.create("refresh-token-value", 1_209_600L))
                .willReturn(refreshTokenCookie);

        MockHttpServletResponse response = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"test-user\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        JsonNode data = new ObjectMapper()
                .readTree(response.getContentAsString())
                .get("data");

        assertThat(data.get("accessToken").asText()).isEqualTo("access-token-value");
        assertThat(data.has("refreshToken")).isFalse();

        String setCookieHeader = response.getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookieHeader).isNotNull();
        assertThat(setCookieHeader).contains("refreshToken=refresh-token-value");
        assertThat(setCookieHeader).contains("HttpOnly");
        assertThat(setCookieHeader).contains("SameSite=Strict");
        assertThat(setCookieHeader).contains("Path=/");
        assertThat(setCookieHeader).contains("Max-Age=1209600");
    }

    @Test
    void 재발급_성공시_쿠키의_refreshToken이_서비스로_전달되고_새_AccessToken이_응답된다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(userService, refreshTokenCookieProvider))
                .build();

        given(userService.reissueAccessToken("refresh-token-value")).willReturn(
                TokenReissueResponse.builder()
                        .accessToken("new-access-token")
                        .build()
        );

        MockHttpServletResponse response = mockMvc.perform(post("/api/user/reissue")
                        .cookie(new Cookie("refreshToken", "refresh-token-value")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        then(userService).should().reissueAccessToken("refresh-token-value");

        JsonNode body = new ObjectMapper().readTree(response.getContentAsString(StandardCharsets.UTF_8));

        assertThat(body.get("success").asBoolean()).isTrue();
        assertThat(body.get("code").asText()).isEqualTo("TOKEN_REISSUE_SUCCESS");
        assertThat(body.get("message").asText()).isEqualTo("Access Token이 재발급되었습니다.");
        assertThat(body.get("data").get("accessToken").asText()).isEqualTo("new-access-token");
    }

    @Test
    void 쿠키가_없으면_null이_서비스로_전달되고_인증_실패_응답이_내려간다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(userService, refreshTokenCookieProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        willThrow(new BusinessException(CommonErrorCode.UNAUTHORIZED))
                .given(userService).reissueAccessToken(null);

        MockHttpServletResponse response = mockMvc.perform(post("/api/user/reissue"))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse();

        then(userService).should().reissueAccessToken(null);

        JsonNode body = new ObjectMapper().readTree(response.getContentAsString(StandardCharsets.UTF_8));

        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("code").asText()).isEqualTo("UNAUTHORIZED");
    }
}
