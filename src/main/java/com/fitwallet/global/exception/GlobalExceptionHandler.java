package com.fitwallet.global.exception;

import com.fitwallet.global.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 모든 예외를 응답으로 바꾸는 단일 지점.
 * <p>
 * 컨트롤러에서 try-catch로 에러 응답을 만들지 않는다.
 * <p>
 * 주의: 이 클래스는 {@code @Controller}가 아니라 {@code @Component} 계열이라
 * {@code servlet-context.xml}의 component-scan include 필터에
 * {@code @ControllerAdvice}가 들어 있어야 등록된다.
 */
@Slf4j
@RestControllerAdvice
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
     * 예상하지 못한 예외. 내부 메시지를 그대로 노출하지 않고 스택트레이스만 로그로 남긴다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("unhandled exception", e);
        return ResponseEntity.status(CommonErrorCode.INTERNAL_ERROR.getStatus())
                .body(ApiResponse.error(CommonErrorCode.INTERNAL_ERROR));
    }
}
