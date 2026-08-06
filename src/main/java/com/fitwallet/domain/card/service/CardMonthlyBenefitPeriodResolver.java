package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.CardMonthlyBenefitPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

/** 카드 유형과 관계없이 오늘 거래를 제외하는 월간 혜택 전용 기간 계산기. */
@Component
@RequiredArgsConstructor
public class CardMonthlyBenefitPeriodResolver {

    private final Clock clock;

    public CardMonthlyBenefitPeriod resolve() {
        LocalDate today = LocalDate.now(clock);
        YearMonth yearMonth = YearMonth.from(today);
        YearMonth performanceMonth = yearMonth.minusMonths(1);

        return new CardMonthlyBenefitPeriod(
                yearMonth,
                today.minusDays(1),
                yearMonth.atDay(1).atStartOfDay(),
                today.atStartOfDay(),
                performanceMonth,
                performanceMonth.atDay(1).atStartOfDay(),
                yearMonth.atDay(1).atStartOfDay());
    }
}
