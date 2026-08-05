package com.fitwallet.domain.user.exception;

import com.fitwallet.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "비밀번호와 비밀번호 확인이 일치하지 않습니다."),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 일치하지 않습니다."),
    PIN_CONFIRM_MISMATCH(HttpStatus.BAD_REQUEST, "결제 PIN과 결제 PIN 확인이 일치하지 않습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}