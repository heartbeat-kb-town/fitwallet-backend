package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.CardMonthlyPeriod;
import com.fitwallet.domain.card.dto.CardUsageAmountSummary;
import com.fitwallet.domain.card.dto.CardUsageBenefitAllocation;
import com.fitwallet.domain.card.dto.CardUsageBenefitRule;
import com.fitwallet.domain.card.dto.CardUsageCardInfo;
import com.fitwallet.domain.card.dto.CardUsageRuleSet;
import com.fitwallet.domain.card.dto.CardUsageTierState;
import com.fitwallet.domain.card.dto.CardUsageTierStructure;
import com.fitwallet.domain.card.dto.response.CardUsageCardResponse;
import com.fitwallet.domain.card.dto.response.CardUsageDetailResponse;
import com.fitwallet.domain.card.dto.response.CardUsageSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CardUsageCalculator {

    private final CardUsageRuleNormalizer usageRuleNormalizer;
    private final CardUsageTierIntegrator usageTierIntegrator;
    private final CardUsageBenefitAllocator usageBenefitAllocator;
    private final CardUsageTierStateCalculator usageTierStateCalculator;

    CardUsageDetailResponse calculateDetail(
            CardUsageCardInfo card,
            CardMonthlyPeriod period,
            CardUsageAmountSummary amounts,
            List<CardUsageBenefitRule> rules) {
        CardUsageCalculation calculation = calculate(
                card.getCardProductId(), amounts.getRecognizedAmount(), rules);
        return CardUsageDetailResponse.builder()
                .card(CardUsageCardResponse.builder()
                        .cardName(card.getCardName())
                        .issuerName(card.getIssuerName())
                        .build())
                .yearMonth(period.getYearMonth().toString())
                .availableYearMonths(period.getAvailableYearMonths())
                .tierType(calculation.tierStructure.getTierType())
                .performanceStatus(calculation.tierState.getPerformanceStatus())
                .usageSummary(CardUsageSummaryResponse.builder()
                        .recognizedAmount(amounts.getRecognizedAmount())
                        .excludedAmount(amounts.getExcludedAmount())
                        .build())
                .currentTier(calculation.tierState.getCurrentTier())
                .nextTier(calculation.tierState.getNextTier())
                .amountUntilNextTier(calculation.tierState.getAmountUntilNextTier())
                .tierProgressRate(calculation.tierState.getTierProgressRate())
                .tiers(calculation.tierState.getTiers())
                .defaultBenefits(calculation.benefitAllocation.getDefaultBenefits())
                .build();
    }

    CardUsageTierState calculateTierState(
            Long cardProductId,
            BigDecimal recognizedAmount,
            List<CardUsageBenefitRule> rules) {
        return calculate(cardProductId, recognizedAmount, rules).tierState;
    }

    private CardUsageCalculation calculate(
            Long cardProductId,
            BigDecimal recognizedAmount,
            List<CardUsageBenefitRule> rules) {
        CardUsageRuleSet ruleSet = usageRuleNormalizer.normalize(cardProductId, rules);
        CardUsageTierStructure tierStructure = usageTierIntegrator.integrate(ruleSet);
        CardUsageBenefitAllocation benefitAllocation = usageBenefitAllocator.allocate(
                tierStructure, ruleSet.getBenefits());
        CardUsageTierState tierState = usageTierStateCalculator.calculate(
                recognizedAmount, tierStructure, benefitAllocation);
        return new CardUsageCalculation(tierStructure, benefitAllocation, tierState);
    }

    private static final class CardUsageCalculation {

        private final CardUsageTierStructure tierStructure;
        private final CardUsageBenefitAllocation benefitAllocation;
        private final CardUsageTierState tierState;

        private CardUsageCalculation(
                CardUsageTierStructure tierStructure,
                CardUsageBenefitAllocation benefitAllocation,
                CardUsageTierState tierState) {
            this.tierStructure = tierStructure;
            this.benefitAllocation = benefitAllocation;
            this.tierState = tierState;
        }
    }
}
