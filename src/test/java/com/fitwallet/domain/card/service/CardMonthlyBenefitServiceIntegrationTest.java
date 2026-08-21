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

import java.math.BigDecimal;
import java.math.RoundingMode;

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
        // 한도값은 참조 데이터라 고정이지만, 사용액은 시드 거래 물량에 딸려 움직인다
        // (V906 이 NOW() 기준으로 넣는 거래가 적재 월에 따라 이 구간에 들어오기도 하고 아니기도 하다).
        // 그래서 절대값 대신 세 값이 서로 맞물리는지를 본다 — 이 테스트가 확인해야 하는 것도
        // "공유 금액한도와 개별 횟수한도가 함께 계산되는가"이지 특정 금액이 아니다.
        BigDecimal totalLimit = response.getMonthlySummary().getTotalBenefitLimit();
        BigDecimal received = response.getMonthlySummary().getReceivedBenefitAmount();
        BigDecimal potential = response.getMonthlySummary().getPotentialBenefitAmount();

        assertThat(totalLimit).isEqualByComparingTo("30000");
        assertThat(received).isPositive();
        assertThat(potential).isEqualByComparingTo(totalLimit.subtract(received));
        assertThat(response.getMonthlySummary().getPotentialBenefitRate())
                .isEqualByComparingTo(potential.multiply(BigDecimal.valueOf(100))
                        .divide(totalLimit, 1, RoundingMode.HALF_UP));

        // V905 가 더한 시연용 혜택(902 카페·906 병원)은 INDUSTRY 스코프라 카테고리 쪽으로 간다.
        assertThat(response.getCategoryBenefits()).hasSize(2);
        assertThat(response.getCategoryBenefits())
                .extracting(benefit -> benefit.getBenefitServiceId())
                .containsExactlyInAnyOrder(902L, 906L);

        assertThat(response.getBrandBenefits()).hasSize(6);
        assertThat(response.getBrandBenefits()).allSatisfy(benefit -> {
            assertThat(benefit.getLimitGroupId()).isEqualTo(10L);
            assertThat(benefit.getMonthlyLimits()).hasSize(2);
            assertThat(benefit.getItemLimitStatus())
                    .isEqualTo(CardMonthlyBenefitLimitStatus.AVAILABLE);
        });

        assertThat(response.getBrandBenefits())
                .filteredOn(benefit -> benefit.getBenefitServiceId().equals(53L))
                .flatExtracting(benefit -> benefit.getMonthlyLimits())
                .anySatisfy(limit -> {
                    assertThat(limit.getLimitId()).isNotNull();
                    assertThat(limit.isShared()).isTrue();
                    assertThat(limit.getUsedValue()).isEqualByComparingTo(received);
                    assertThat(limit.getRemainingValue()).isEqualByComparingTo(potential);
                });

        assertThat(response.getSharedLimitGroups()).singleElement().satisfies(group -> {
            assertThat(group.getLimitGroupId()).isEqualTo(10L);
            assertThat(group.getCategories()).isNotEmpty();
            assertThat(group.getSharedMonthlyLimit().getLimitId()).isNotNull();
            assertThat(group.getSharedMonthlyLimit().getUsedValue()).isEqualByComparingTo(received);
            assertThat(group.getUsageBreakdown()).isNotEmpty();
            assertThat(group.getBenefitServices()).hasSize(4);
            assertThat(group.getBenefitServices()).allSatisfy(service ->
                    assertThat(service.getTargets()).isNotEmpty());
            // 53·54 는 그룹 금액한도 위에 자기 tier 의 횟수한도를 하나씩 더 갖는다.
            // 902·906 은 그룹 한도만 쓰므로 개별 월 한도가 없다 — 여기가 두 한도가
            // 함께 계산되는지 갈라 보는 지점이다.
            assertThat(group.getBenefitServices())
                    .filteredOn(service -> service.getBenefitServiceId().equals(53L)
                            || service.getBenefitServiceId().equals(54L))
                    .hasSize(2)
                    .allSatisfy(service ->
                            assertThat(service.getServiceMonthlyLimits()).hasSize(1));
            assertThat(group.getBenefitServices())
                    .filteredOn(service -> service.getBenefitServiceId().equals(902L)
                            || service.getBenefitServiceId().equals(906L))
                    .hasSize(2)
                    .allSatisfy(service ->
                            assertThat(service.getServiceMonthlyLimits()).isEmpty());
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
            assertThat(monthlyBenefit.getSharedLimitGroups()).isNotNull();
        }
    }
}
