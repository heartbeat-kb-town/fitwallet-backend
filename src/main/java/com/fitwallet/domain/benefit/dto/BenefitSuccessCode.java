package com.fitwallet.domain.benefit.dto;

import com.fitwallet.global.common.code.SuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BenefitSuccessCode implements SuccessCode {

    EXPECTED_BENEFIT_FOUND(HttpStatus.OK, "예상 혜택 조회에 성공했습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
