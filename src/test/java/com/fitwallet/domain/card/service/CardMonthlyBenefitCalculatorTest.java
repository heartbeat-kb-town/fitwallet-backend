package com.fitwallet.domain.card.service;

import com.fitwallet.domain.benefit.dto.BenefitScopeType;
import com.fitwallet.domain.benefit.dto.BenefitType;
import com.fitwallet.domain.benefit.dto.LimitBasis;
import com.fitwallet.domain.benefit.dto.ValueType;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitCategoryTarget;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitPeriod;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitRule;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitTargetUsage;
import com.fitwallet.domain.card.dto.CardSummaryCardInfo;
import com.fitwallet.domain.card.dto.CardType;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CardMonthlyBenefitCalculatorTest {

    private final CardMonthlyBenefitCalculator calculator = new CardMonthlyBenefitCalculator();

    @Test
    void 포인트는_하단에서_포인트로_표시하고_상단에서_원화로_환산한다() {
        CardMonthlyBenefitRule pointRule = CardMonthlyBenefitRule.builder()
                .serviceId(10L)
                .benefitName("기본 포인트 적립")
                .benefitType(BenefitType.ACCUMULATE)
                .valueType(ValueType.FIXED)
                .valueNumber(new BigDecimal("100"))
                .scopeType(BenefitScopeType.INDUSTRY)
                .benefitMinimumAmount(BigDecimal.ZERO)
                .pointCurrencyName("테스트포인트")
                .krwPerPoint(new BigDecimal("0.5"))
                .tierId(20L)
                .tierOrder(1)
                .tierMinimumAmount(BigDecimal.ZERO)
                .limitId(30L)
                .limitBasis(LimitBasis.POINT)
                .limitValue(new BigDecimal("1000"))
                .build();

        CardMonthlyBenefitResponse response = calculator.calculate(
                card(),
                period(),
                BigDecimal.ZERO,
                List.of(pointRule),
                List.of(CardMonthlyBenefitCategoryTarget.builder()
                        .serviceId(10L)
                        .categoryId(1L)
                        .categoryName("카페/디저트")
                        .build()),
                List.of(),
                List.of(CardMonthlyBenefitTargetUsage.builder()
                        .serviceId(10L)
                        .categoryId(1L)
                        .transactionCount(1L)
                        .totalPaymentAmount(new BigDecimal("5000"))
                        .receivedBenefitAmount(new BigDecimal("100"))
                        .build()));

        assertThat(response.getMonthlySummary().getTotalBenefitLimit())
                .isEqualByComparingTo("500");
        assertThat(response.getMonthlySummary().getReceivedBenefitAmount())
                .isEqualByComparingTo("100");
        assertThat(response.getMonthlySummary().getPotentialBenefitAmount())
                .isEqualByComparingTo("400");
        assertThat(response.getCategoryBenefits()).singleElement().satisfies(benefit -> {
            assertThat(benefit.getReceivedBenefitValue()).isEqualByComparingTo("200");
            assertThat(benefit.getReceivedBenefitLabel()).isEqualTo("총 200P 적립");
            assertThat(benefit.getMonthlyLimits()).singleElement().satisfies(limit -> {
                assertThat(limit.getLimitValue()).isEqualByComparingTo("1000");
                assertThat(limit.getUsedValue()).isEqualByComparingTo("200");
                assertThat(limit.getRemainingValue()).isEqualByComparingTo("800");
            });
        });
    }

    @Test
    void 횟수만_있는_정액혜택은_횟수와_건당혜택으로_잠재금액을_계산한다() {
        CardMonthlyBenefitRule countRule = CardMonthlyBenefitRule.builder()
                .serviceId(11L)
                .benefitName("정액 할인")
                .benefitType(BenefitType.CASHBACK)
                .valueType(ValueType.FIXED)
                .valueNumber(new BigDecimal("500"))
                .scopeType(BenefitScopeType.INDUSTRY)
                .benefitMinimumAmount(BigDecimal.ZERO)
                .tierId(21L)
                .tierOrder(1)
                .tierMinimumAmount(BigDecimal.ZERO)
                .limitId(31L)
                .limitBasis(LimitBasis.COUNT)
                .limitValue(new BigDecimal("3"))
                .build();

        CardMonthlyBenefitResponse response = calculator.calculate(
                card(), period(), BigDecimal.ZERO, List.of(countRule),
                List.of(CardMonthlyBenefitCategoryTarget.builder()
                        .serviceId(11L).categoryId(1L).categoryName("카페/디저트").build()),
                List.of(),
                List.of(CardMonthlyBenefitTargetUsage.builder()
                        .serviceId(11L).categoryId(1L).transactionCount(1L)
                        .totalPaymentAmount(new BigDecimal("5000"))
                        .receivedBenefitAmount(new BigDecimal("500"))
                        .build()));

        assertThat(response.getMonthlySummary().getTotalBenefitLimit())
                .isEqualByComparingTo("1500");
        assertThat(response.getMonthlySummary().getPotentialBenefitAmount())
                .isEqualByComparingTo("1000");
    }

    private CardSummaryCardInfo card() {
        return CardSummaryCardInfo.builder()
                .cardId(1L)
                .cardProductId(1L)
                .cardName("테스트 카드")
                .issuerName("테스트 카드사")
                .cardType(CardType.CREDIT)
                .build();
    }

    private CardMonthlyBenefitPeriod period() {
        return new CardMonthlyBenefitPeriod(
                YearMonth.of(2026, 7),
                LocalDate.of(2026, 7, 23),
                LocalDateTime.of(2026, 7, 1, 0, 0),
                LocalDateTime.of(2026, 7, 24, 0, 0),
                YearMonth.of(2026, 6),
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 7, 1, 0, 0));
    }
}
