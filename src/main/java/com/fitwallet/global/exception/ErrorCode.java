package com.fitwallet.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 모든 에러 코드가 구현하는 공통 규약.
 * <p>
 * 도메인마다 {@code domain/{도메인}/exception/{도메인}ErrorCode} enum을 만들어 구현한다.
 * 전역 단일 enum을 쓰지 않는 이유는 여러 도메인을 병렬로 개발할 때 한 파일에서 충돌하기 때문이다.
 */
public interface ErrorCode {

    HttpStatus getStatus();

    /** 응답 바디의 {@code code}. enum 상수 이름을 그대로 쓴다. */
    String getCode();

    String getMessage();
}
