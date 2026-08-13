package com.fitwallet.domain.card.service;

import com.fitwallet.domain.benefit.dto.BenefitScopeType;
import com.fitwallet.domain.benefit.dto.BenefitType;
import com.fitwallet.domain.benefit.dto.LimitBasis;
import com.fitwallet.domain.benefit.dto.ValueType;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitCategoryTarget;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitBrandTarget;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitLimitStatus;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitPeriod;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitRule;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitTargetUsage;
import com.fitwallet.domain.card.dto.CardSummaryCardInfo;
import com.fitwallet.domain.card.dto.CardType;
import com.fitwallet.domain.card.dto.CardUsagePerformanceStatus;
import com.fitwallet.domain.card.dto.CardUsageTierState;
import com.fitwallet.domain.card.dto.response.CardUsageTierSummaryResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CardMonthlyBenefitCalculatorTest {

    private final CardMonthlyBenefitUsageCalculator usageCalculator =
            new CardMonthlyBenefitUsageCalculator();
    private final CardMonthlyBenefitCalculator calculator = new CardMonthlyBenefitCalculator(
            usageCalculator,
            new CardMonthlyBenefitItemAssembler(
                    usageCalculator, new CardBenefitValueLabelFormatter()));

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
                noRequirementState(),
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
        assertThat(response.getMonthlySummary().getPotentialBenefitRate())
                .isEqualByComparingTo("80.0");
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
                card(), period(), BigDecimal.ZERO, noRequirementState(), List.of(countRule),
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

    @Test
    void 이용실적_통합구간의_상태와_현재구간을_그대로_반환한다() {
        CardUsageTierSummaryResponse currentTier = CardUsageTierSummaryResponse.builder()
                .tierOrder(0)
                .tierName("0구간")
                .minimumAmount(BigDecimal.ZERO)
                .maximumAmount(new BigDecimal("300000"))
                .build();
        CardUsageTierState tierState = new CardUsageTierState(
                CardUsagePerformanceStatus.INSUFFICIENT,
                currentTier,
                null,
                null,
                BigDecimal.ZERO,
                List.of());

        CardMonthlyBenefitResponse response = calculator.calculate(
                card(), period(), BigDecimal.ZERO, tierState,
                List.of(), List.of(), List.of(), List.of());

        assertThat(response.getPerformance().getStatus())
                .isEqualTo(CardUsagePerformanceStatus.INSUFFICIENT);
        assertThat(response.getPerformance().getCurrentTier()).isSameAs(currentTier);
        assertThat(response.getMonthlySummary().getPotentialBenefitRate()).isNull();
    }

    @Test
    void 정액_주유혜택은_공통문구로_리터당을_표시한다() {
        CardMonthlyBenefitRule fuelRule = CardMonthlyBenefitRule.builder()
                .serviceId(12L)
                .benefitName("주유 할인")
                .benefitType(BenefitType.CASHBACK)
                .valueType(ValueType.FIXED)
                .valueNumber(new BigDecimal("60"))
                .scopeType(BenefitScopeType.INDUSTRY)
                .benefitMinimumAmount(BigDecimal.ZERO)
                .tierId(22L)
                .tierOrder(1)
                .tierMinimumAmount(BigDecimal.ZERO)
                .limitId(32L)
                .limitBasis(LimitBasis.AMOUNT)
                .limitValue(new BigDecimal("5000"))
                .build();

        CardMonthlyBenefitResponse response = calculator.calculate(
                card(), period(), BigDecimal.ZERO, noRequirementState(), List.of(fuelRule),
                List.of(CardMonthlyBenefitCategoryTarget.builder()
                        .serviceId(12L).categoryId(6L).categoryName("주유").build()),
                List.of(), List.of());

        assertThat(response.getCategoryBenefits()).singleElement()
                .extracting(benefit -> benefit.getValueLabel())
                .isEqualTo("리터당 60원 할인");
    }

    @Test
    void 공동한도는_카테고리와_브랜드를_서비스별로_묶고_기존_평면응답도_유지한다() {
        CardMonthlyBenefitUsageCalculator trackedUsageCalculator =
                spy(new CardMonthlyBenefitUsageCalculator());
        CardMonthlyBenefitCalculator trackedCalculator = new CardMonthlyBenefitCalculator(
                trackedUsageCalculator,
                new CardMonthlyBenefitItemAssembler(
                        trackedUsageCalculator, new CardBenefitValueLabelFormatter()));
        CardMonthlyBenefitRule categoryRule = sharedRule(
                101L, "기본혜택 - 카페", BenefitScopeType.INDUSTRY);
        CardMonthlyBenefitRule brandRule = sharedRule(
                102L, "추가혜택 - 편의점(주말)", BenefitScopeType.BRAND);
        CardMonthlyBenefitRule brandCountRule = CardMonthlyBenefitRule.builder()
                .serviceId(102L)
                .servicePlanGroupId(50L)
                .benefitName("추가혜택 - 편의점(주말)")
                .benefitType(BenefitType.CASHBACK)
                .valueType(ValueType.RATE)
                .valueNumber(new BigDecimal("10"))
                .scopeType(BenefitScopeType.BRAND)
                .benefitMinimumAmount(BigDecimal.ZERO)
                .tierId(202L)
                .tierOrder(1)
                .tierMinimumAmount(BigDecimal.ZERO)
                .limitId(302L)
                .limitBasis(LimitBasis.COUNT)
                .limitValue(new BigDecimal("2"))
                .build();

        CardMonthlyBenefitResponse response = trackedCalculator.calculate(
                card(), period(), BigDecimal.ZERO, noRequirementState(),
                List.of(categoryRule, brandRule, brandCountRule),
                List.of(CardMonthlyBenefitCategoryTarget.builder()
                        .serviceId(101L).categoryId(1L).categoryName("카페/디저트").build()),
                List.of(CardMonthlyBenefitBrandTarget.builder()
                        .serviceId(102L).brandId(10L).brandName("테스트브랜드")
                        .categoryId(2L).categoryName("편의점/마트").build()),
                List.of(
                        CardMonthlyBenefitTargetUsage.builder()
                                .serviceId(101L).categoryId(1L).transactionCount(1L)
                                .totalPaymentAmount(new BigDecimal("3000"))
                                .receivedBenefitAmount(new BigDecimal("300"))
                                .build(),
                        CardMonthlyBenefitTargetUsage.builder()
                                .serviceId(102L).categoryId(2L).brandId(10L).transactionCount(1L)
                                .totalPaymentAmount(new BigDecimal("2000"))
                                .receivedBenefitAmount(new BigDecimal("200"))
                                .build()));

        assertThat(response.getCategoryBenefits()).singleElement().satisfies(benefit -> {
            assertThat(benefit.getLimitGroupId()).isEqualTo(50L);
            assertThat(benefit.getMonthlyLimits()).singleElement()
                    .extracting(limit -> limit.getLimitId())
                    .isEqualTo(301L);
        });
        assertThat(response.getBrandBenefits()).singleElement().satisfies(benefit -> {
            assertThat(benefit.getLimitGroupId()).isEqualTo(50L);
            assertThat(benefit.getMonthlyLimits()).hasSize(2);
        });
        assertThat(response.getSharedLimitGroups()).singleElement().satisfies(group -> {
            assertThat(group.getLimitGroupId()).isEqualTo(50L);
            assertThat(group.getCategories()).extracting(category -> category.getCategoryId())
                    .containsExactly(1L, 2L);
            assertThat(group.getSharedMonthlyLimit().getUsedValue()).isEqualByComparingTo("500");
            assertThat(group.getGroupLimitStatus())
                    .isEqualTo(CardMonthlyBenefitLimitStatus.AVAILABLE);
            assertThat(group.getUsageBreakdown()).hasSize(2);
            assertThat(group.getBenefitServices()).hasSize(2)
                    .anySatisfy(service -> {
                        assertThat(service.getBenefitServiceId()).isEqualTo(101L);
                        assertThat(service.getDisplayQualifier()).isNull();
                    })
                    .anySatisfy(service -> {
                        assertThat(service.getBenefitServiceId()).isEqualTo(102L);
                        assertThat(service.getDisplayQualifier()).isEqualTo("주말 추가혜택");
                        assertThat(service.getServiceMonthlyLimits()).singleElement();
                    });
        });
        verify(trackedUsageCalculator, times(3))
                .calculateItemLimitResult(any(), any(), any());
    }

    private CardMonthlyBenefitRule sharedRule(
            Long serviceId, String benefitName, BenefitScopeType scopeType) {
        return CardMonthlyBenefitRule.builder()
                .serviceId(serviceId)
                .servicePlanGroupId(50L)
                .benefitName(benefitName)
                .benefitType(BenefitType.CASHBACK)
                .valueType(ValueType.RATE)
                .valueNumber(new BigDecimal("10"))
                .scopeType(scopeType)
                .benefitMinimumAmount(BigDecimal.ZERO)
                .tierId(201L)
                .limitPlanGroupId(50L)
                .tierOrder(1)
                .tierMinimumAmount(BigDecimal.ZERO)
                .limitId(301L)
                .limitBasis(LimitBasis.AMOUNT)
                .limitValue(new BigDecimal("1000"))
                .build();
    }

    private CardUsageTierState noRequirementState() {
        return new CardUsageTierState(
                CardUsagePerformanceStatus.NO_REQUIREMENT,
                null,
                null,
                null,
                null,
                List.of());
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
