package com.fitwallet.domain.card.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 마이데이터 연동으로 받아온 거래 한 건. 우리 API 응답 DTO가 아니라 외부 데이터 형태다. */
@Getter
@Builder
@AllArgsConstructor
public class MyDataTransaction {

    private final BigDecimal amount;
    private final Long storeId;
    private final LocalDateTime paidAt;
}
