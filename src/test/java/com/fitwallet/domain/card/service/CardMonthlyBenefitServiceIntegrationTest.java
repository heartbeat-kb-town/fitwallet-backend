package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.CardMonthlyBenefitLimitStatus;
import com.fitwallet.domain.card.dto.CardUsagePerformanceStatus;
import com.fitwallet.domain.card.dto.request.CardUsageSearchRequest;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitResponse;
import com.fitwallet.domain.card.dto.response.CardUsageDetailResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** 고정 기준일과 실제 시드 데이터로 월간 혜택 조회 전체 조합을 검증한다. */
@SpringJUnitConfig(locations = "classpath:root-context.xml")
@Transactional
class CardMonthlyBenefitServiceIntegrationTest {

    @Autowired
    private CardService cardService;

    @Test
    void 올패스카드의_공유금액한도와_개별횟수한도를_함께_계산한다() {
        CardMonthlyBenefitResponse response = cardService.getCardMonthlyBenefit(1L, 2L);

        assertThat(response.getYearMonth()).isEqualTo("2026-07");
        assertThat(response.getAsOfDate().toString()).isEqualTo("2026-07-23");
        assertThat(response.getPerformance().getStatus())
                .isEqualTo(CardUsagePerformanceStatus.ACHIEVED);
        assertThat(response.getMonthlySummary().getTotalBenefitLimit())
                .isEqualByComparingTo("5000");
        assertThat(response.getMonthlySummary().getReceivedBenefitAmount())
                .isEqualByComparingTo("1000");
        assertThat(response.getMonthlySummary().getPotentialBenefitAmount())
                .isEqualByComparingTo("4000");
        assertThat(response.getMonthlySummary().getPotentialBenefitRate())
                .isEqualByComparingTo("80.0");

        assertThat(response.getCategoryBenefits()).isEmpty();
        assertThat(response.getBrandBenefits()).hasSize(6);
        assertThat(response.getBrandBenefits()).allSatisfy(benefit -> {
            assertThat(benefit.getMonthlyLimits()).hasSize(2);
            assertThat(benefit.getItemLimitStatus())
                    .isEqualTo(CardMonthlyBenefitLimitStatus.AVAILABLE);
        });

        assertThat(response.getBrandBenefits())
                .filteredOn(benefit -> benefit.getBenefitServiceId().equals(53L))
                .flatExtracting(benefit -> benefit.getMonthlyLimits())
                .anySatisfy(limit -> {
                    assertThat(limit.isShared()).isTrue();
                    assertThat(limit.getUsedValue()).isEqualByComparingTo("1000");
                    assertThat(limit.getRemainingValue()).isEqualByComparingTo("4000");
                });
    }

    @Test
    void 보유카드_전체의_전월통합구간은_이용실적조회와_동일하다() {
        CardUsageSearchRequest previousMonthRequest = new CardUsageSearchRequest();
        ReflectionTestUtils.setField(previousMonthRequest, "yearMonth", "2026-06");

        for (long cardId = 1L; cardId <= 5L; cardId++) {
            CardMonthlyBenefitResponse monthlyBenefit =
                    cardService.getCardMonthlyBenefit(1L, cardId);
            CardUsageDetailResponse previousMonthUsage =
                    cardService.getCardUsage(1L, cardId, previousMonthRequest);

            assertThat(monthlyBenefit.getPerformance().getStatus())
                    .as("cardId=%s 전월 실적 상태", cardId)
                    .isEqualTo(previousMonthUsage.getPerformanceStatus());
            assertThat(monthlyBenefit.getPerformance().getCurrentTier())
                    .as("cardId=%s 전월 통합 구간", cardId)
                    .usingRecursiveComparison()
                    .isEqualTo(previousMonthUsage.getCurrentTier());
            assertThat(monthlyBenefit.getCategoryBenefits()).isNotNull();
            assertThat(monthlyBenefit.getBrandBenefits()).isNotNull();
        }
    }
}
