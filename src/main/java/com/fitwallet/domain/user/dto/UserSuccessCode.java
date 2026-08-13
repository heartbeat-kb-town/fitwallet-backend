package com.fitwallet.domain.user.dto;

import com.fitwallet.global.common.code.SuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserSuccessCode implements SuccessCode {

    USER_SIGNUP_SUCCESS(HttpStatus.CREATED, "회원가입이 완료되었습니다."),
    LOGIN_SUCCESS(HttpStatus.OK, "로그인에 성공했습니다."),
    FREQUENT_PLACES_FOUND(HttpStatus.OK, "자주 찾는 장소를 조회했습니다."),
    TOKEN_REISSUE_SUCCESS(HttpStatus.OK, "Access Token이 재발급되었습니다."),
    PAYMENT_PIN_CREATED(HttpStatus.CREATED, "결제 PIN을 등록했습니다."),
    LOCATION_AGREEMENT_UPDATED(HttpStatus.OK, "위치 정보 이용 동의 상태를 변경했습니다."),
    LOGOUT_SUCCESS(HttpStatus.OK, "로그아웃되었습니다."),
    PAYMENT_PIN_UPDATED(HttpStatus.OK, "결제 비밀번호를 변경했습니다."),
    USER_INFO_RETRIEVED(HttpStatus.OK, "사용자 정보를 조회했습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
