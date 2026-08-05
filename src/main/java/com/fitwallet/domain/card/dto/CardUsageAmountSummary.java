package com.fitwallet.domain.card.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 조회 기간의 실적 인정 금액과 실적 미인정 금액 집계 결과. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardUsageAmountSummary {

    private BigDecimal recognizedAmount;
    private BigDecimal excludedAmount;
}
