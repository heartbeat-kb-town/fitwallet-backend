package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.CardMonthlyBenefitPeriod;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class CardMonthlyBenefitPeriodResolverTest {

    @Test
    void 이번달은_오늘을_제외하고_전월은_전체기간으로_계산한다() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-23T15:00:00Z"),
                ZoneId.of("Asia/Seoul"));

        CardMonthlyBenefitPeriod period = new CardMonthlyBenefitPeriodResolver(clock).resolve();

        assertThat(period.getYearMonth().toString()).isEqualTo("2026-07");
        assertThat(period.getAsOfDate()).isEqualTo(LocalDate.of(2026, 7, 23));
        assertThat(period.getBenefitStartAt()).isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
        assertThat(period.getBenefitEndAt()).isEqualTo(LocalDateTime.of(2026, 7, 24, 0, 0));
        assertThat(period.getPerformanceMonth().toString()).isEqualTo("2026-06");
        assertThat(period.getPerformanceStartAt()).isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0));
        assertThat(period.getPerformanceEndAt()).isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
    }
}
