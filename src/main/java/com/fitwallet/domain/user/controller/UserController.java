package com.fitwallet.domain.user.controller;

import com.fitwallet.domain.user.dto.UserSuccessCode;
import com.fitwallet.domain.user.dto.request.SignUpRequest;
import com.fitwallet.domain.user.service.UserService;
import com.fitwallet.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

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

    @PostMapping("/user/signup")
    public ResponseEntity<ApiResponse<Void>> signUp(
            @Valid @RequestBody SignUpRequest request) {

        userService.signUp(request);

        return ApiResponse.of(
                UserSuccessCode.USER_SIGNUP_SUCCESS,
                null
        );
    }
}