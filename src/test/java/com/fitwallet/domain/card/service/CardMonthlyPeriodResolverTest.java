package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.CardMonthlyPeriod;
import com.fitwallet.domain.card.dto.CardType;
import com.fitwallet.domain.card.exception.CardErrorCode;
import com.fitwallet.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardMonthlyPeriodResolverTest {

    private CardMonthlyPeriodResolver resolver;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-03T15:00:00Z"),
                ZoneId.of("Asia/Seoul"));
        resolver = new CardMonthlyPeriodResolver(clock);
    }

    @Test
    void 거래가_없고_조회연월을_생략하면_현재월만_반환한다() {
        CardMonthlyPeriod period = resolver.resolve(null, CardType.DEBIT);

        assertThat(period.getYearMonth().toString()).isEqualTo("2026-08");
        assertThat(period.getAvailableYearMonths())
                .containsExactly("2026-08");
        assertThat(period.isCurrentMonth()).isTrue();
    }

    @Test
    void 현재월부터_최초거래월까지_빈월을_포함해_최신순으로_반환한다() {
        CardMonthlyPeriod period = resolver.resolve(
                null,
                CardType.DEBIT,
                LocalDateTime.of(2026, 4, 20, 12, 0));

        assertThat(period.getAvailableYearMonths())
                .containsExactly("2026-08", "2026-07", "2026-06", "2026-05", "2026-04");
    }

    @Test
    void 현재월_체크카드는_오늘_거래까지_포함한다() {
        CardMonthlyPeriod period = resolver.resolve("2026-08", CardType.DEBIT);

        assertThat(period.getStartAt()).isEqualTo(LocalDateTime.of(2026, 8, 1, 0, 0));
        assertThat(period.getEndAt()).isEqualTo(LocalDateTime.of(2026, 8, 5, 0, 0));
    }

    @Test
    void 현재월_신용카드는_전날_거래까지만_포함한다() {
        CardMonthlyPeriod period = resolver.resolve("2026-08", CardType.CREDIT);

        assertThat(period.getStartAt()).isEqualTo(LocalDateTime.of(2026, 8, 1, 0, 0));
        assertThat(period.getEndAt()).isEqualTo(LocalDateTime.of(2026, 8, 4, 0, 0));
    }

    @Test
    void 과거월은_카드유형과_관계없이_월전체를_조회한다() {
        CardMonthlyPeriod creditPeriod = resolver.resolve("2026-07", CardType.CREDIT);
        CardMonthlyPeriod debitPeriod = resolver.resolve("2026-07", CardType.DEBIT);

        assertThat(creditPeriod.getStartAt()).isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
        assertThat(creditPeriod.getEndAt()).isEqualTo(LocalDateTime.of(2026, 8, 1, 0, 0));
        assertThat(debitPeriod.getStartAt()).isEqualTo(creditPeriod.getStartAt());
        assertThat(debitPeriod.getEndAt()).isEqualTo(creditPeriod.getEndAt());
        assertThat(creditPeriod.isCurrentMonth()).isFalse();
    }

    @Test
    void 조회연월_형식이_잘못되면_INVALID_YEAR_MONTH_예외를_던진다() {
        assertThatThrownBy(() -> resolver.resolve("2026-8", CardType.DEBIT))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CardErrorCode.INVALID_YEAR_MONTH);
    }

    @Test
    void 미래월이면_YEAR_MONTH_OUT_OF_RANGE_예외를_던진다() {
        assertThatThrownBy(() -> resolver.resolve("2026-09", CardType.DEBIT))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CardErrorCode.YEAR_MONTH_OUT_OF_RANGE);
    }

    @Test
    void 최초거래월보다_과거인_월도_빈내역을_조회할수_있다() {
        CardMonthlyPeriod period = resolver.resolve(
                "2020-01",
                CardType.DEBIT,
                LocalDateTime.of(2026, 4, 20, 12, 0));

        assertThat(period.getYearMonth().toString()).isEqualTo("2020-01");
        assertThat(period.getStartAt()).isEqualTo(LocalDateTime.of(2020, 1, 1, 0, 0));
        assertThat(period.getEndAt()).isEqualTo(LocalDateTime.of(2020, 2, 1, 0, 0));
        assertThat(period.getAvailableYearMonths())
                .startsWith("2026-08")
                .endsWith("2026-04");
    }
}
