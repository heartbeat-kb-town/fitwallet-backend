package com.fitwallet.global.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fitwallet.global.common.code.SuccessCode;
import com.fitwallet.global.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.List;

/**
 * 모든 {@code /api} 응답의 공통 봉투. API 명세가 정의한 형태다.
 *
 * <pre>
 * // 성공
 * { "success": true, "code": "USER_CARDS_FOUND",
 *   "message": "보유 카드 목록을 조회했습니다.", "data": [ ... ] }
 *
 * // 실패
 * { "success": false, "code": "CARD_NOT_FOUND",
 *   "message": "카드를 찾을 수 없습니다.", "data": null }
 *
 * // 검증 실패 — errors가 추가된다
 * { "success": false, "code": "INVALID_INPUT_VALUE",
 *   "message": "입력값이 올바르지 않습니다.", "data": null,
 *   "errors": [ { "field": "first4", "reason": "..." } ] }
 * </pre>
 *
 * {@code data}는 실패 시에도 {@code null}로 내려간다 (명세 형태 유지).
 * {@code errors}만 없을 때 생략된다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {

    private boolean success;
    private String code;
    private String message;
    private T data;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<FieldErrorDetail> errors;

    /** 컨트롤러에서 쓴다. SuccessCode가 HTTP 상태코드까지 들고 있다. */
    public static <T> ResponseEntity<ApiResponse<T>> of(SuccessCode successCode, T data) {
        return ResponseEntity.status(successCode.getStatus())
                .body(ApiResponse.<T>builder()
                        .success(true)
                        .code(successCode.getCode())
                        .message(successCode.getMessage())
                        .data(data)
                        .build());
    }

    /** GlobalExceptionHandler에서만 쓴다. */
    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return ApiResponse.<Void>builder()
                .success(false)
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, BindingResult bindingResult) {
        return ApiResponse.<Void>builder()
                .success(false)
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .errors(FieldErrorDetail.from(bindingResult))
                .build();
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FieldErrorDetail {

        private String field;
        private String reason;

        private static List<FieldErrorDetail> from(BindingResult bindingResult) {
            return bindingResult.getFieldErrors().stream()
                    .map(FieldErrorDetail::from)
                    .toList();
        }

        private static FieldErrorDetail from(FieldError fieldError) {
            return FieldErrorDetail.builder()
                    .field(fieldError.getField())
                    .reason(fieldError.getDefaultMessage())
                    .build();
        }
    }
}
