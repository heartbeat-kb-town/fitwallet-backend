package com.fitwallet.global.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fitwallet.global.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.List;

/**
 * 모든 에러 응답의 공통 바디.
 *
 * <pre>
 * { "code": "CARD_NOT_FOUND", "message": "카드를 찾을 수 없습니다." }
 *
 * { "code": "INVALID_INPUT_VALUE", "message": "입력값이 올바르지 않습니다.",
 *   "errors": [ { "field": "first4", "reason": "4자리여야 합니다." } ] }
 * </pre>
 *
 * {@code errors}는 검증 실패일 때만 내려간다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ErrorResponse {

    private String code;
    private String message;
    private List<FieldErrorDetail> errors;

    public static ErrorResponse of(ErrorCode errorCode) {
        return ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
    }

    public static ErrorResponse of(ErrorCode errorCode, BindingResult bindingResult) {
        return ErrorResponse.builder()
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
