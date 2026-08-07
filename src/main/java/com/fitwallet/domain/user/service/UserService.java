package com.fitwallet.domain.user.service;

import com.fitwallet.domain.user.dto.request.LocationAgreeRequest;
import com.fitwallet.domain.user.dto.request.PinRegisterRequest;
import com.fitwallet.domain.user.dto.request.SignUpRequest;
import com.fitwallet.domain.user.dto.request.UserLoginRequest;
import com.fitwallet.domain.user.dto.response.FrequentPlaceResponse;
import com.fitwallet.domain.user.dto.response.TokenReissueResponse;
import com.fitwallet.domain.user.dto.response.UserLoginTokenResponse;

import java.util.List;

/** 사용자 회원가입과 로그인을 처리하는 서비스 계약. */
public interface UserService {

    void signUp(SignUpRequest request);

    /** 자격 증명을 검증하고 Access Token과 Refresh Token을 발급한다. */
    UserLoginTokenResponse login(UserLoginRequest request);

    /** 로그인 사용자가 최근 1개월간 가장 자주 결제한 장소를 최대 3개 조회한다. */
    List<FrequentPlaceResponse> findFrequentPlaces(Long userId);

    /** Refresh Token을 검증하고 새 Access Token을 발급한다. */
    TokenReissueResponse reissueAccessToken(String refreshToken);

    /** 로그인 사용자의 결제 PIN을 등록한다. 이미 등록돼 있어도 그대로 덮어쓴다. */
    void registerPaymentPin(Long userId, PinRegisterRequest request);

    /** 로그인 사용자의 위치 정보 동의 여부를 갱신한다. */
    void updateLocationAgreement(Long userId, LocationAgreeRequest request);
}
