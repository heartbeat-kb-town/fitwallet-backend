package com.fitwallet.domain.card.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/** 카드 유형과 조회 월에 따라 계산된 월별 거래 조회 기간. */
@Getter
@AllArgsConstructor
public class CardMonthlyPeriod {

    private YearMonth yearMonth;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private boolean currentMonth;
    private List<String> availableYearMonths;
}
