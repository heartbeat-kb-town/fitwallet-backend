package com.fitwallet.domain.card.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

/** 카드 월간 혜택과 전월 실적을 계산하는 KST 기준 기간. */
@Getter
@AllArgsConstructor
public class CardMonthlyBenefitPeriod {

    private YearMonth yearMonth;
    private LocalDate asOfDate;
    private LocalDateTime benefitStartAt;
    private LocalDateTime benefitEndAt;
    private YearMonth performanceMonth;
    private LocalDateTime performanceStartAt;
    private LocalDateTime performanceEndAt;
}
