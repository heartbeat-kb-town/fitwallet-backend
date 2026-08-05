package com.fitwallet.domain.user.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitwallet.domain.user.dto.response.FrequentPlaceResponse;
import com.fitwallet.domain.user.dto.response.UserLoginTokenResponse;
import com.fitwallet.domain.user.service.UserService;
import com.fitwallet.global.config.AuthInterceptor;
import com.fitwallet.global.config.JwtProvider;
import com.fitwallet.global.config.LoginUserIdArgumentResolver;
import com.fitwallet.global.config.RefreshTokenCookieProvider;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    @Mock
    private JwtProvider jwtProvider;

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
    void 자주찾는장소_조회는_LoginUserId를_서비스에_전달하고_공통_성공_응답을_반환한다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(userService, refreshTokenCookieProvider))
                .setCustomArgumentResolvers(new LoginUserIdArgumentResolver())
                .addInterceptors(new AuthInterceptor(jwtProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);
        given(userService.findFrequentPlaces(1L)).willReturn(List.of(
                FrequentPlaceResponse.builder()
                        .storeId(10L)
                        .storeName("스타벅스 강남점")
                        .address("서울 강남구")
                        .categoryName("카페/디저트")
                        .build()
        ));

        mockMvc.perform(get("/api/user/frequent-places")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("FREQUENT_PLACES_FOUND"))
                .andExpect(jsonPath("$.message").value("자주 찾는 장소를 조회했습니다."))
                .andExpect(jsonPath("$.data[0].storeId").value(10));

        then(userService).should().findFrequentPlaces(eq(1L));
    }
}
