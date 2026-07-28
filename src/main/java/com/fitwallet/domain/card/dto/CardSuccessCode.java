package com.fitwallet.domain.card.dto;

import com.fitwallet.global.common.code.SuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CardSuccessCode implements SuccessCode {

    USER_CARDS_FOUND(HttpStatus.OK, "보유 카드 목록을 조회했습니다."),
    CARD_SUMMARY_FOUND(HttpStatus.OK, "카드 요약 정보를 조회했습니다."),
    CARD_REGISTERED(HttpStatus.CREATED, "카드를 등록했습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
