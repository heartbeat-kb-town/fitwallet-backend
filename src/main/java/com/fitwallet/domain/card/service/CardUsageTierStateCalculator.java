package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.CardUsageBenefitAllocation;
import com.fitwallet.domain.card.dto.CardUsageIntegratedTier;
import com.fitwallet.domain.card.dto.CardUsagePerformanceStatus;
import com.fitwallet.domain.card.dto.CardUsageTierBenefitGroup;
import com.fitwallet.domain.card.dto.CardUsageTierState;
import com.fitwallet.domain.card.dto.CardUsageTierStructure;
import com.fitwallet.domain.card.dto.CardUsageTierType;
import com.fitwallet.domain.card.dto.response.CardUsageTierResponse;
import com.fitwallet.domain.card.dto.response.CardUsageTierSummaryResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** 인정 실적 금액을 기준으로 현재·다음 통합 구간과 전체 구간 바 진행률을 계산한다. */
@Component
public class CardUsageTierStateCalculator {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal HIGHEST_TIER_PROGRESS_RATE = new BigDecimal("100.0");

    public CardUsageTierState calculate(
            BigDecimal recognizedAmount,
            CardUsageTierStructure tierStructure,
            CardUsageBenefitAllocation benefitAllocation) {
        validateInputs(recognizedAmount, tierStructure, benefitAllocation);

        if (tierStructure.getTierType() == CardUsageTierType.NO_REQUIREMENT) {
            return new CardUsageTierState(
                    CardUsagePerformanceStatus.NO_REQUIREMENT,
                    null,
                    null,
                    null,
                    null,
                    List.of());
        }

        List<CardUsageIntegratedTier> integratedTiers = tierStructure.getTiers();
        int currentIndex = findCurrentIndex(recognizedAmount, integratedTiers);
        CardUsageIntegratedTier current = integratedTiers.get(currentIndex);
        CardUsageIntegratedTier next = currentIndex + 1 < integratedTiers.size()
                ? integratedTiers.get(currentIndex + 1)
                : null;

        List<CardUsageTierResponse> tiers = createTierResponses(
                recognizedAmount,
                currentIndex,
                integratedTiers,
                benefitAllocation.getTierBenefitGroups());

        return new CardUsageTierState(
                resolvePerformanceStatus(recognizedAmount, integratedTiers),
                toSummary(current, next),
                next == null ? null : toSummary(next, tierAfter(integratedTiers, currentIndex + 1)),
                next == null ? null : next.getMinimumAmount().subtract(recognizedAmount).max(BigDecimal.ZERO),
                calculateProgressRate(
                        recognizedAmount,
                        currentIndex,
                        current,
                        next,
                        integratedTiers.size()),
                tiers);
    }

    private void validateInputs(
            BigDecimal recognizedAmount,
            CardUsageTierStructure tierStructure,
            CardUsageBenefitAllocation benefitAllocation) {
        if (recognizedAmount == null || recognizedAmount.signum() < 0) {
            throw new IllegalStateException("인정 실적 금액이 없거나 음수입니다.");
        }
        if (tierStructure == null || tierStructure.getTierType() == null
                || tierStructure.getTiers() == null || benefitAllocation == null
                || benefitAllocation.getTierBenefitGroups() == null) {
            throw new IllegalStateException("구간 상태를 계산할 이용 실적 데이터가 없습니다.");
        }
        if (tierStructure.getTierType() == CardUsageTierType.NO_REQUIREMENT) {
            if (!tierStructure.getTiers().isEmpty()
                    || !benefitAllocation.getTierBenefitGroups().isEmpty()) {
                throw new IllegalStateException("실적 조건 없는 카드에 통합 구간이 존재합니다.");
            }
            return;
        }
        if (tierStructure.getTiers().size() < 2
                || tierStructure.getTiers().size() != benefitAllocation.getTierBenefitGroups().size()) {
            throw new IllegalStateException("통합 구간과 구간별 혜택 목록의 개수가 다릅니다.");
        }
        List<CardUsageIntegratedTier> tiers = tierStructure.getTiers();
        if (tiers.get(0).getTierOrder() != 0 || tiers.get(0).getMinimumAmount().signum() != 0) {
            throw new IllegalStateException("실적 조건이 있는 카드의 첫 구간이 합성 0구간이 아닙니다.");
        }
        for (int index = 1; index < tiers.size(); index++) {
            if (tiers.get(index - 1).getMinimumAmount()
                    .compareTo(tiers.get(index).getMinimumAmount()) >= 0) {
                throw new IllegalStateException("통합 구간의 최소금액이 오름차순이 아닙니다.");
            }
        }
    }

    private int findCurrentIndex(
            BigDecimal recognizedAmount,
            List<CardUsageIntegratedTier> tiers) {
        int currentIndex = 0;
        for (int index = 1; index < tiers.size(); index++) {
            if (recognizedAmount.compareTo(tiers.get(index).getMinimumAmount()) < 0) {
                break;
            }
            currentIndex = index;
        }
        return currentIndex;
    }

    private CardUsagePerformanceStatus resolvePerformanceStatus(
            BigDecimal recognizedAmount,
            List<CardUsageIntegratedTier> tiers) {
        return recognizedAmount.compareTo(tiers.get(1).getMinimumAmount()) >= 0
                ? CardUsagePerformanceStatus.ACHIEVED
                : CardUsagePerformanceStatus.INSUFFICIENT;
    }

    private List<CardUsageTierResponse> createTierResponses(
            BigDecimal recognizedAmount,
            int currentIndex,
            List<CardUsageIntegratedTier> integratedTiers,
            List<CardUsageTierBenefitGroup> benefitGroups) {
        List<CardUsageTierResponse> responses = new ArrayList<>(integratedTiers.size());
        for (int index = 0; index < integratedTiers.size(); index++) {
            CardUsageIntegratedTier tier = integratedTiers.get(index);
            CardUsageTierBenefitGroup benefitGroup = benefitGroups.get(index);
            if (benefitGroup.getTier().getMinimumAmount().compareTo(tier.getMinimumAmount()) != 0) {
                throw new IllegalStateException("통합 구간과 혜택 구간의 금액이 다릅니다.");
            }

            responses.add(CardUsageTierResponse.builder()
                    .tierOrder(tier.getTierOrder())
                    .tierName(tier.getTierName())
                    .minimumAmount(tier.getMinimumAmount())
                    .maximumAmount(tierAfter(integratedTiers, index) == null
                            ? null : tierAfter(integratedTiers, index).getMinimumAmount())
                    .achieved(tier.getTierOrder() > 0
                            && recognizedAmount.compareTo(tier.getMinimumAmount()) >= 0)
                    .current(index == currentIndex)
                    .benefits(benefitGroup.getBenefits())
                    .build());
        }
        return List.copyOf(responses);
    }

    private BigDecimal calculateProgressRate(
            BigDecimal recognizedAmount,
            int currentIndex,
            CardUsageIntegratedTier current,
            CardUsageIntegratedTier next,
            int tierCount) {
        if (next == null) {
            return HIGHEST_TIER_PROGRESS_RATE;
        }
        int intervalCount = tierCount - 1;
        if (intervalCount <= 0) {
            throw new IllegalStateException("통합 구간 바의 간격이 없습니다.");
        }

        BigDecimal intervalProgressRate = calculateIntervalProgressRate(
                recognizedAmount, current, next);
        return BigDecimal.valueOf(currentIndex)
                .multiply(ONE_HUNDRED)
                .add(intervalProgressRate)
                .divide(BigDecimal.valueOf(intervalCount), 1, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO)
                .min(HIGHEST_TIER_PROGRESS_RATE);
    }

    private BigDecimal calculateIntervalProgressRate(
            BigDecimal recognizedAmount,
            CardUsageIntegratedTier current,
            CardUsageIntegratedTier next) {
        BigDecimal interval = next.getMinimumAmount().subtract(current.getMinimumAmount());
        if (interval.signum() <= 0) {
            throw new IllegalStateException("통합 구간의 금액 간격이 0 이하입니다.");
        }

        BigDecimal progressed = recognizedAmount.subtract(current.getMinimumAmount());
        BigDecimal rate = progressed.multiply(ONE_HUNDRED)
                .divide(interval, 1, RoundingMode.HALF_UP);
        return rate.max(BigDecimal.ZERO).min(HIGHEST_TIER_PROGRESS_RATE);
    }

    private CardUsageTierSummaryResponse toSummary(
            CardUsageIntegratedTier tier,
            CardUsageIntegratedTier next) {
        return CardUsageTierSummaryResponse.builder()
                .tierOrder(tier.getTierOrder())
                .tierName(tier.getTierName())
                .minimumAmount(tier.getMinimumAmount())
                .maximumAmount(next == null ? null : next.getMinimumAmount())
                .build();
    }

    private CardUsageIntegratedTier tierAfter(
            List<CardUsageIntegratedTier> tiers,
            int index) {
        return index + 1 < tiers.size() ? tiers.get(index + 1) : null;
    }
}
