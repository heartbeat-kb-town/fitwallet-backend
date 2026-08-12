package com.fitwallet.domain.user.controller;

import com.fitwallet.domain.user.dto.UserSuccessCode;
import com.fitwallet.domain.user.dto.request.LocationAgreeRequest;
import com.fitwallet.domain.user.dto.request.PinRegisterRequest;
import com.fitwallet.domain.user.dto.request.PinUpdateRequest;
import com.fitwallet.domain.user.dto.request.SignUpRequest;
import com.fitwallet.domain.user.dto.request.UserLoginRequest;
import com.fitwallet.domain.user.dto.response.FrequentPlaceResponse;
import com.fitwallet.domain.user.dto.response.TokenReissueResponse;
import com.fitwallet.domain.user.dto.response.UserLoginResponse;
import com.fitwallet.domain.user.dto.response.UserLoginTokenResponse;
import com.fitwallet.domain.user.service.UserService;
import com.fitwallet.global.common.annotation.LoginUserId;
import com.fitwallet.global.common.dto.ApiResponse;
import com.fitwallet.global.config.RefreshTokenCookieProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.List;

/**
 * 사용자 도메인의 요청을 처리한다.
 * <p>
 * 회원가입은 인증 전 요청이므로 사용자 식별자를 받지 않는다.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @PostMapping("/user/signup")
    public ResponseEntity<ApiResponse<Void>> signUp(
            @Valid @RequestBody SignUpRequest request) {

        userService.signUp(request);

        return ApiResponse.of(
                UserSuccessCode.USER_SIGNUP_SUCCESS,
                null
        );
    }

    /**
     * 일반 로그인을 처리한다.
     * Access Token은 응답 data에, Refresh Token은 HttpOnly 쿠키에 담는다.
     */
    @PostMapping("/user/login")
    public ResponseEntity<ApiResponse<UserLoginResponse>> login(
            @Valid @RequestBody UserLoginRequest request,
            HttpServletResponse servletResponse) {

        UserLoginTokenResponse tokens = userService.login(request);

        ResponseCookie refreshTokenCookie = refreshTokenCookieProvider.create(
                tokens.getRefreshToken(),
                tokens.getRefreshTokenExpirationSeconds()
        );

        UserLoginResponse loginResponse = UserLoginResponse.builder()
                .accessToken(tokens.getAccessToken())
                .build();

        servletResponse.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookie.toString()
        );

        return ApiResponse.of(
                UserSuccessCode.LOGIN_SUCCESS,
                loginResponse
        );
    }

    @GetMapping("/user/frequent-places")
    public ResponseEntity<ApiResponse<List<FrequentPlaceResponse>>> findFrequentPlaces(
            @LoginUserId Long userId) {

        return ApiResponse.of(
                UserSuccessCode.FREQUENT_PLACES_FOUND,
                userService.findFrequentPlaces(userId)
        );
    }

    /**
     * Access Token을 재발급한다.
     * Refresh Token은 HttpOnly 쿠키로만 전달받는다
     */
    @PostMapping("/user/reissue")
    public ResponseEntity<ApiResponse<TokenReissueResponse>> reissueAccessToken(
            @CookieValue(value = "refreshToken", required = false) String refreshToken) {

        TokenReissueResponse response = userService.reissueAccessToken(refreshToken);

        return ApiResponse.of(
                UserSuccessCode.TOKEN_REISSUE_SUCCESS,
                response
        );
    }

    @PostMapping("/user/payment-pin")
    public ResponseEntity<ApiResponse<Void>> registerPaymentPin(
            @LoginUserId Long userId,
            @Valid @RequestBody PinRegisterRequest request) {

        userService.registerPaymentPin(userId, request);

        return ApiResponse.of(
                UserSuccessCode.PAYMENT_PIN_CREATED,
                null
        );
    }

    @PatchMapping("/user/location-agreement")
    public ResponseEntity<ApiResponse<Void>> updateLocationAgreement(
            @LoginUserId Long userId,
            @Valid @RequestBody LocationAgreeRequest request) {

        userService.updateLocationAgreement(userId, request);

        return ApiResponse.of(
                UserSuccessCode.LOCATION_AGREEMENT_UPDATED,
                null
        );
    }

    /**
     * 로그아웃을 처리한다.
     * 저장된 Refresh Token을 삭제하고, 로그인과 같은 이름·속성의 쿠키를 즉시 만료시켜 내려준다.
     */
    @PostMapping("/user/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @LoginUserId Long userId,
            HttpServletResponse servletResponse) {

        userService.logout(userId);

        servletResponse.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookieProvider.clear().toString()
        );

        return ApiResponse.of(
                UserSuccessCode.LOGOUT_SUCCESS,
                null
        );
    }

    @PatchMapping("/user/payment-pin")
    public ResponseEntity<ApiResponse<Void>> updatePaymentPin(
            @LoginUserId Long userId,
            @Valid @RequestBody PinUpdateRequest request) {

        userService.updatePaymentPin(userId, request);

        return ApiResponse.of(
                UserSuccessCode.PAYMENT_PIN_UPDATED,
                null
        );
    }
}
