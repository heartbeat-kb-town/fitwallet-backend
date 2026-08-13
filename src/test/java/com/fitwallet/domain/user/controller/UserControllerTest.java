package com.fitwallet.domain.user.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitwallet.domain.user.dto.response.FrequentPlaceResponse;
import com.fitwallet.domain.user.dto.response.TokenReissueResponse;
import com.fitwallet.domain.user.dto.response.UserInfoResponse;
import com.fitwallet.domain.user.dto.response.UserLoginTokenResponse;
import com.fitwallet.domain.user.service.UserService;
import com.fitwallet.global.config.AuthInterceptor;
import com.fitwallet.global.config.JwtProvider;
import com.fitwallet.global.config.LoginUserIdArgumentResolver;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    @Test
    void 결제_PIN_등록은_LoginUserId를_서비스에_전달하고_201과_PAYMENT_PIN_CREATED를_반환한다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(userService, refreshTokenCookieProvider))
                .setCustomArgumentResolvers(new LoginUserIdArgumentResolver())
                .addInterceptors(new AuthInterceptor(jwtProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);

        mockMvc.perform(post("/api/user/payment-pin")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"123456\",\"pinConfirm\":\"123456\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("PAYMENT_PIN_CREATED"))
                .andExpect(jsonPath("$.message").value("결제 PIN을 등록했습니다."));

        then(userService).should().registerPaymentPin(eq(1L), ArgumentMatchers.any());
    }

    @Test
    void 결제_PIN_변경은_LoginUserId를_서비스에_전달하고_200과_PAYMENT_PIN_UPDATED를_반환한다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(userService, refreshTokenCookieProvider))
                .setCustomArgumentResolvers(new LoginUserIdArgumentResolver())
                .addInterceptors(new AuthInterceptor(jwtProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);

        mockMvc.perform(patch("/api/user/payment-pin")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPin\":\"111111\",\"newPin\":\"222222\",\"newPinConfirm\":\"222222\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("PAYMENT_PIN_UPDATED"))
                .andExpect(jsonPath("$.message").value("결제 비밀번호를 변경했습니다."));

        then(userService).should().updatePaymentPin(eq(1L), ArgumentMatchers.any());
    }

    @Test
    void 결제_PIN_변경시_필드가_누락되면_400과_INVALID_INPUT_VALUE를_반환한다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(userService, refreshTokenCookieProvider))
                .setCustomArgumentResolvers(new LoginUserIdArgumentResolver())
                .addInterceptors(new AuthInterceptor(jwtProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);

        mockMvc.perform(patch("/api/user/payment-pin")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPin\":\"222222\",\"newPinConfirm\":\"222222\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        then(userService).shouldHaveNoInteractions();
    }

    @Test
    void 결제_PIN_확인은_LoginUserId를_서비스에_전달하고_200과_CURRENT_PAYMENT_PIN_VERIFIED를_반환한다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(userService, refreshTokenCookieProvider))
                .setCustomArgumentResolvers(new LoginUserIdArgumentResolver())
                .addInterceptors(new AuthInterceptor(jwtProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);

        mockMvc.perform(post("/api/user/payment-pin/verify")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPin\":\"111111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("CURRENT_PAYMENT_PIN_VERIFIED"))
                .andExpect(jsonPath("$.message").value("현재 결제 비밀번호를 확인했습니다."));

        then(userService).should().verifyPaymentPin(eq(1L), ArgumentMatchers.any());
    }

    @Test
    void 마이페이지_조회는_LoginUserId를_서비스에_전달하고_200과_이름을_반환한다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(userService, refreshTokenCookieProvider))
                .setCustomArgumentResolvers(new LoginUserIdArgumentResolver())
                .addInterceptors(new AuthInterceptor(jwtProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);
        given(userService.findUserInfo(1L))
                .willReturn(UserInfoResponse.builder().name("김국민").build());

        mockMvc.perform(get("/api/user/me")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("USER_INFO_RETRIEVED"))
                .andExpect(jsonPath("$.data.name").value("김국민"));
    }

    @Test
    void 로그아웃은_LoginUserId를_서비스에_전달하고_RefreshToken_쿠키를_만료시킨다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(userService, refreshTokenCookieProvider))
                .setCustomArgumentResolvers(new LoginUserIdArgumentResolver())
                .addInterceptors(new AuthInterceptor(jwtProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);
        ResponseCookie expiredCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
        given(refreshTokenCookieProvider.clear()).willReturn(expiredCookie);

        MockHttpServletResponse response = mockMvc.perform(post("/api/user/logout")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("LOGOUT_SUCCESS"))
                .andExpect(jsonPath("$.message").value("로그아웃되었습니다."))
                .andReturn()
                .getResponse();

        then(userService).should().logout(eq(1L));

        String setCookieHeader = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeader).contains("refreshToken=");
        assertThat(setCookieHeader).contains("Max-Age=0");
    }
}
