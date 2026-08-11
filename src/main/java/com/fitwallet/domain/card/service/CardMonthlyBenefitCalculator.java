package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.CardMonthlyBenefitBrandTarget;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitCategoryTarget;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitPeriod;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitRule;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitTargetUsage;
import com.fitwallet.domain.card.dto.CardSummaryCardInfo;
import com.fitwallet.domain.card.dto.CardUsagePerformanceStatus;
import com.fitwallet.domain.card.dto.CardUsageTierState;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitCardResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitPerformanceResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitSummaryResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyBrandBenefitResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyCategoryBenefitResponse;
import com.fitwallet.domain.card.exception.CardErrorCode;
import com.fitwallet.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.fitwallet.domain.card.service.CardMonthlyBenefitCalculationContext.SelectedService;
import static com.fitwallet.domain.card.service.CardMonthlyBenefitCalculationContext.SummaryAmounts;
import static com.fitwallet.domain.card.service.CardMonthlyBenefitCalculationContext.UsageIndex;

@Component
@RequiredArgsConstructor
public class CardMonthlyBenefitCalculator {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final String ACHIEVED_MESSAGE = "전월 실적 조건이 적용 중이에요.";
    private static final String INSUFFICIENT_MESSAGE = "전월 실적 조건을 충족하지 못했어요.";
    private static final String NO_REQUIREMENT_MESSAGE = "전월 실적 조건이 없어요.";

    private final CardMonthlyBenefitUsageCalculator usageCalculator;
    private final CardMonthlyBenefitItemAssembler itemAssembler;

    public CardMonthlyBenefitResponse calculate(
            CardSummaryCardInfo card,
            CardMonthlyBenefitPeriod period,
            BigDecimal previousMonthSpend,
            CardUsageTierState performanceTierState,
            List<CardMonthlyBenefitRule> rules,
            List<CardMonthlyBenefitCategoryTarget> categoryTargets,
            List<CardMonthlyBenefitBrandTarget> brandTargets,
            List<CardMonthlyBenefitTargetUsage> usages) {
        requireNotNull(card, period, previousMonthSpend, performanceTierState,
                rules, categoryTargets, brandTargets, usages);

        Map<Long, List<CardMonthlyBenefitRule>> rulesByService = groupRulesByService(rules);
        List<SelectedService> selectedServices = selectServices(rulesByService, previousMonthSpend);
        UsageIndex usageIndex = usageCalculator.createUsageIndex(usages, rulesByService);
        usageCalculator.validatePointCurrencies(selectedServices);
        SummaryAmounts summaryAmounts = usageCalculator.calculateSummaryAmounts(
                selectedServices, rulesByService, usageIndex);
        List<CardMonthlyCategoryBenefitResponse> categoryBenefits =
                itemAssembler.createCategoryBenefits(selectedServices, categoryTargets, usageIndex);
        List<CardMonthlyBrandBenefitResponse> brandBenefits =
                itemAssembler.createBrandBenefits(selectedServices, brandTargets, usageIndex);

        return CardMonthlyBenefitResponse.builder()
                .card(CardMonthlyBenefitCardResponse.builder()
                        .userCardId(card.getCardId())
                        .cardName(card.getCardName())
                        .issuerName(card.getIssuerName())
                        .cardImageUrl(card.getCardImageUrl())
                        .cardType(card.getCardType())
                        .build())
                .yearMonth(period.getYearMonth().toString())
                .asOfDate(period.getAsOfDate())
                .monthlySummary(CardMonthlyBenefitSummaryResponse.builder()
                        .potentialBenefitAmount(summaryAmounts.potential)
                        .receivedBenefitAmount(summaryAmounts.received)
                        .totalBenefitLimit(summaryAmounts.total)
                        .potentialBenefitRate(calculatePotentialBenefitRate(summaryAmounts))
                        .receivedBenefitDetailAvailable(summaryAmounts.received.signum() > 0)
                        .build())
                .performance(CardMonthlyBenefitPerformanceResponse.builder()
                        .performanceMonth(period.getPerformanceMonth().toString())
                        .status(performanceTierState.getPerformanceStatus())
                        .currentTier(performanceTierState.getCurrentTier())
                        .message(performanceMessage(performanceTierState.getPerformanceStatus()))
                        .build())
                .categoryBenefits(categoryBenefits)
                .brandBenefits(brandBenefits)
                .build();
    }

    private Map<Long, List<CardMonthlyBenefitRule>> groupRulesByService(
            List<CardMonthlyBenefitRule> rules) {
        Map<Long, List<CardMonthlyBenefitRule>> grouped = new LinkedHashMap<>();
        for (CardMonthlyBenefitRule rule : rules) {
            validateRule(rule);
            grouped.computeIfAbsent(rule.getServiceId(), ignored -> new ArrayList<>()).add(rule);
        }
        return grouped;
    }

    private List<SelectedService> selectServices(
            Map<Long, List<CardMonthlyBenefitRule>> rulesByService,
            BigDecimal previousMonthSpend) {
        List<SelectedService> selected = new ArrayList<>();
        for (List<CardMonthlyBenefitRule> serviceRules : rulesByService.values()) {
            CardMonthlyBenefitRule definition = serviceRules.get(0);
            validateConsistentServiceRows(definition, serviceRules);
            if (!within(previousMonthSpend,
                    definition.getBenefitMinimumAmount(), definition.getBenefitMaximumAmount())) {
                continue;
            }

            Map<String, Map<Long, List<CardMonthlyBenefitRule>>> tiersByOwner = new LinkedHashMap<>();
            for (CardMonthlyBenefitRule rule : serviceRules) {
                tiersByOwner
                        .computeIfAbsent(ownerKey(rule), ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(rule.getTierId(), ignored -> new ArrayList<>())
                        .add(rule);
            }

            List<CardMonthlyBenefitRule> selectedLimits = new ArrayList<>();
            for (Map<Long, List<CardMonthlyBenefitRule>> ownerTiers : tiersByOwner.values()) {
                List<List<CardMonthlyBenefitRule>> applicableTiers = ownerTiers.values().stream()
                        .filter(tier -> within(previousMonthSpend,
                                tier.get(0).getTierMinimumAmount(), tier.get(0).getTierMaximumAmount()))
                        .toList();
                if (applicableTiers.size() > 1) {
                    throw invalidData();
                }
                if (applicableTiers.size() == 1) {
                    selectedLimits.addAll(applicableTiers.get(0));
                }
            }

            if (!selectedLimits.isEmpty()) {
                Map<Long, CardMonthlyBenefitRule> uniqueLimits = new LinkedHashMap<>();
                selectedLimits.forEach(rule -> uniqueLimits.put(rule.getLimitId(), rule));
                selected.add(new SelectedService(definition, List.copyOf(uniqueLimits.values())));
            }
        }
        return List.copyOf(selected);
    }

    private BigDecimal calculatePotentialBenefitRate(SummaryAmounts amounts) {
        if (amounts.total.signum() <= 0) {
            return null;
        }
        BigDecimal rate = amounts.potential
                .divide(amounts.total, 12, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(1, RoundingMode.HALF_UP);
        return rate.max(BigDecimal.ZERO).min(ONE_HUNDRED);
    }

    private String performanceMessage(CardUsagePerformanceStatus status) {
        return switch (status) {
            case ACHIEVED -> ACHIEVED_MESSAGE;
            case INSUFFICIENT -> INSUFFICIENT_MESSAGE;
            case NO_REQUIREMENT -> NO_REQUIREMENT_MESSAGE;
        };
    }

    private boolean within(BigDecimal value, BigDecimal minimum, BigDecimal maximum) {
        if (value == null || minimum == null) {
            throw invalidData();
        }
        return value.compareTo(minimum) >= 0
                && (maximum == null || value.compareTo(maximum) < 0);
    }

    private String ownerKey(CardMonthlyBenefitRule rule) {
        if (rule.getLimitPlanGroupId() != null) {
            return "GROUP:" + rule.getLimitPlanGroupId();
        }
        if (rule.getServiceId() != null) {
            return "SERVICE:" + rule.getServiceId();
        }
        throw invalidData();
    }

    private void validateRule(CardMonthlyBenefitRule rule) {
        if (rule == null || rule.getServiceId() == null || rule.getBenefitName() == null
                || rule.getBenefitType() == null || rule.getValueType() == null
                || rule.getValueNumber() == null || rule.getScopeType() == null
                || rule.getBenefitMinimumAmount() == null || rule.getTierId() == null
                || rule.getTierOrder() == null || rule.getTierMinimumAmount() == null
                || rule.getLimitId() == null || rule.getLimitBasis() == null
                || rule.getLimitValue() == null || rule.getLimitValue().signum() <= 0) {
            throw invalidData();
        }
        ownerKey(rule);
    }

    private void validateConsistentServiceRows(
            CardMonthlyBenefitRule definition,
            List<CardMonthlyBenefitRule> rows) {
        boolean inconsistent = rows.stream().anyMatch(row ->
                !Objects.equals(definition.getBenefitName(), row.getBenefitName())
                        || definition.getBenefitType() != row.getBenefitType()
                        || definition.getValueType() != row.getValueType()
                        || definition.getValueNumber().compareTo(row.getValueNumber()) != 0
                        || definition.getScopeType() != row.getScopeType());
        if (inconsistent) {
            throw invalidData();
        }
    }

    private void requireNotNull(Object... values) {
        for (Object value : values) {
            if (value == null) {
                throw invalidData();
            }
        }
    }

    private BusinessException invalidData() {
        return new BusinessException(CardErrorCode.INVALID_CARD_MONTHLY_BENEFIT_DATA);
    }
}
