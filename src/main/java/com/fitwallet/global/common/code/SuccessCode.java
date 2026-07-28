package com.fitwallet.global.common.code;

import org.springframework.http.HttpStatus;

/**
 * 성공 응답의 {@code code}와 {@code message}.
 * <p>
 * API 명세가 성공 응답에도 엔드포인트별 코드와 메시지를 요구한다
 * (예: {@code "code": "USER_CARDS_FOUND"}). 문자열을 컨트롤러에 흩뿌리지 않도록
 * {@link com.fitwallet.global.exception.ErrorCode}와 같은 방식으로 도메인별 enum이 구현한다.
 */
public interface SuccessCode {

    HttpStatus getStatus();

    String getCode();

    String getMessage();
}
