package com.fitwallet.global.exception;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반을 나타내는 단일 예외.
 * <p>
 * 에러 종류는 예외 클래스를 늘리지 않고 {@link ErrorCode} 상수로 구분한다.
 * {@link GlobalExceptionHandler}가 ErrorCode의 상태코드로 변환해 응답한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final transient ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
