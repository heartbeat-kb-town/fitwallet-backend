package com.fitwallet.domain.card.exception;

import com.fitwallet.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CardErrorCode implements ErrorCode {

    INVALID_CARD_EVENT_DATA(HttpStatus.INTERNAL_SERVER_ERROR, "카드 이벤트 데이터가 올바르지 않습니다."),

    CARD_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 카드를 찾을 수 없습니다."),
    INVALID_YEAR_MONTH(HttpStatus.BAD_REQUEST, "조회 연월 형식이 올바르지 않습니다."),
    YEAR_MONTH_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "미래 월의 내역은 조회할 수 없습니다."),
    INVALID_TRANSACTION_PAGE_SIZE(HttpStatus.BAD_REQUEST, "조회 개수는 1개 이상 100개 이하여야 합니다."),
    INVALID_TRANSACTION_CURSOR(HttpStatus.BAD_REQUEST, "유효하지 않은 결제 내역 커서입니다."),
    INVALID_CARD_PAYMENT_DATA(HttpStatus.INTERNAL_SERVER_ERROR, "카드 결제 이용금액 데이터가 올바르지 않습니다."),
    INVALID_CARD_SUMMARY_DATA(HttpStatus.INTERNAL_SERVER_ERROR, "카드 요약 데이터가 올바르지 않습니다."),
    INVALID_CARD_MONTHLY_BENEFIT_DATA(HttpStatus.INTERNAL_SERVER_ERROR,
            "카드 월간 혜택 데이터가 올바르지 않습니다."),
    CARD_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 등록된 카드입니다."),
    INVALID_CARD_DISPLAY_ORDER(HttpStatus.BAD_REQUEST, "요청한 카드 목록이 보유 카드와 일치하지 않습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
