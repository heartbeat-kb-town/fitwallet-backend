package com.fitwallet.domain.user.service;

import com.fitwallet.domain.user.dto.request.SignUpRequest;
import com.fitwallet.domain.user.dto.request.UserLoginRequest;
import com.fitwallet.domain.user.dto.response.UserLoginTokenResponse;

/** 사용자 회원가입과 로그인을 처리하는 서비스 계약. */
public interface UserService {

    void signUp(SignUpRequest request);

    /** 자격 증명을 검증하고 Access Token과 Refresh Token을 발급한다. */
    UserLoginTokenResponse login(UserLoginRequest request);
}
