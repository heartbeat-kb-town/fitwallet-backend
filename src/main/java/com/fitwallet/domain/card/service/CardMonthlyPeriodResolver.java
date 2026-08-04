package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.CardMonthlyPeriod;
import com.fitwallet.domain.card.dto.CardType;
import com.fitwallet.domain.card.exception.CardErrorCode;
import com.fitwallet.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** 카드 월별 조회 API가 공유하는 연월 검증과 조회 경계 계산기. */
@Component
@RequiredArgsConstructor
public class CardMonthlyPeriodResolver {

    private static final int AVAILABLE_MONTH_COUNT = 3;

    private final Clock clock;

    public CardMonthlyPeriod resolve(String requestedYearMonth, CardType cardType) {
        LocalDate today = LocalDate.now(clock);
        YearMonth currentYearMonth = YearMonth.from(today);
        YearMonth yearMonth = resolveYearMonth(requestedYearMonth, currentYearMonth);
        boolean currentMonth = yearMonth.equals(currentYearMonth);

        LocalDateTime startAt = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime endAt = resolveEndAt(cardType, yearMonth, currentMonth, today);

        return new CardMonthlyPeriod(
                yearMonth,
                startAt,
                endAt,
                currentMonth,
                createAvailableYearMonths(currentYearMonth));
    }

    private YearMonth resolveYearMonth(String requestedYearMonth, YearMonth currentYearMonth) {
        if (requestedYearMonth == null || requestedYearMonth.isBlank()) {
            return currentYearMonth;
        }

        final YearMonth yearMonth;
        try {
            yearMonth = YearMonth.parse(requestedYearMonth);
        } catch (DateTimeException exception) {
            throw new BusinessException(CardErrorCode.INVALID_YEAR_MONTH);
        }

        if (yearMonth.isAfter(currentYearMonth)
                || yearMonth.isBefore(currentYearMonth.minusMonths(AVAILABLE_MONTH_COUNT - 1L))) {
            throw new BusinessException(CardErrorCode.YEAR_MONTH_OUT_OF_RANGE);
        }
        return yearMonth;
    }

    private LocalDateTime resolveEndAt(
            CardType cardType,
            YearMonth yearMonth,
            boolean currentMonth,
            LocalDate today) {
        if (!currentMonth) {
            return yearMonth.plusMonths(1).atDay(1).atStartOfDay();
        }
        if (cardType == CardType.CREDIT) {
            return today.atStartOfDay();
        }
        return today.plusDays(1).atStartOfDay();
    }

    private List<String> createAvailableYearMonths(YearMonth currentYearMonth) {
        List<String> availableYearMonths = new ArrayList<>(AVAILABLE_MONTH_COUNT);
        for (int index = 0; index < AVAILABLE_MONTH_COUNT; index++) {
            availableYearMonths.add(currentYearMonth.minusMonths(index).toString());
        }
        return availableYearMonths;
    }
}
