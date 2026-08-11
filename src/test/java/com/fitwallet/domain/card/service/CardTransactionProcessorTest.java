package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.CardMonthlyPeriod;
import com.fitwallet.domain.card.dto.request.CardTransactionSearchRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CardTransactionProcessorTest {

    private final CardTransactionProcessor processor = new CardTransactionProcessor();

    @Test
    void 합계는_월_전체_조건을_사용하고_목록만_커서와_limit을_사용한다() {
        CardMonthlyPeriod period = new CardMonthlyPeriod(
                YearMonth.of(2026, 7),
                LocalDateTime.of(2026, 7, 1, 0, 0),
                LocalDateTime.of(2026, 8, 1, 0, 0),
                false,
                List.of("2026-07"));
        CardTransactionSearchRequest request = new CardTransactionSearchRequest();
        ReflectionTestUtils.setField(request, "size", 10);
        String cursor = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "10|2026-07|2026-07-20T09:08:55|341".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(request, "cursor", cursor);

        CardTransactionProcessor.PreparedTransactionQuery query =
                processor.prepareQuery(10L, request, period);

        assertThat(query.getSummaryCondition().getStartAt()).isEqualTo(period.getStartAt());
        assertThat(query.getSummaryCondition().getEndAt()).isEqualTo(period.getEndAt());
        assertThat(query.getSummaryCondition().getCursorPaidAt()).isNull();
        assertThat(query.getSummaryCondition().getCursorTransactionId()).isNull();
        assertThat(query.getSummaryCondition().getLimit()).isNull();
        assertThat(query.getPageCondition().getCursorPaidAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 20, 9, 8, 55));
        assertThat(query.getPageCondition().getCursorTransactionId()).isEqualTo(341L);
        assertThat(query.getPageCondition().getLimit()).isEqualTo(11);
    }
}
