package com.fitwallet.global.exception;

import com.fitwallet.global.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 모든 예외를 응답으로 바꾸는 단일 지점.
 * <p>
 * 컨트롤러에서 try-catch로 에러 응답을 만들지 않는다.
 * <p>
 * 주의: 이 클래스는 {@code @Controller}가 아니라 {@code @Component} 계열이라
 * {@code servlet-context.xml}의 component-scan include 필터에
 * {@code @ControllerAdvice}가 들어 있어야 등록된다.
 * <p>
 * {@code basePackages}로 범위를 좁힌 이유: 범위를 안 주면 springfox가 등록하는 컨트롤러
 * ({@code /v3/api-docs}, {@code /swagger-resources/**})의 예외까지 잡아 아래 catch-all이
 * {@code INTERNAL_ERROR} JSON으로 삼킨다. Swagger 배선이 틀렸을 때 원인이 안 보이게 된다.
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.fitwallet")
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("business exception: {} - {}", errorCode.getCode(), errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode, e.getData()));
    }

    /** {@code @Valid} 검증 실패. 어떤 필드가 왜 틀렸는지까지 내려준다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        return ResponseEntity.status(CommonErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ApiResponse.error(CommonErrorCode.INVALID_INPUT_VALUE, e.getBindingResult()));
    }

    /**
     * 쿼리 파라미터 바인딩 실패(예: {@code ?latitude=abc}의 타입 변환 실패).
     * {@link MethodArgumentNotValidException}이 이 타입의 하위 타입이라, Spring이 예외 계층에서
     * 더 구체적인 핸들러를 알아서 고른다 — 이 메서드가 {@code @Valid} 검증 실패 응답을 가로채지 않는다.
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException e) {
        return ResponseEntity.status(CommonErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ApiResponse.error(CommonErrorCode.INVALID_INPUT_VALUE, e.getBindingResult()));
    }

    /**
     * 경로 변수·쿼리 파라미터의 타입 변환 실패(예: {@code /keywords/recent/abc}의 {@code Long}
     * 변환 실패). {@link BindException}의 하위 타입이 아니라서 별도 핸들러가 필요하다 — 없으면
     * 마지막 {@link Exception} 핸들러가 삼켜 500이 나간다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(CommonErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ApiResponse.error(CommonErrorCode.INVALID_INPUT_VALUE));
    }

    /**
     * 예상하지 못한 예외. 내부 메시지를 그대로 노출하지 않고 스택트레이스만 로그로 남긴다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("unhandled exception", e);
        return ResponseEntity.status(CommonErrorCode.INTERNAL_ERROR.getStatus())
                .body(ApiResponse.error(CommonErrorCode.INTERNAL_ERROR));
    }
}
