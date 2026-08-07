package com.fitwallet.domain.card.service;

import com.fitwallet.domain.benefit.dto.BenefitScopeType;
import com.fitwallet.domain.benefit.dto.BenefitType;
import com.fitwallet.domain.benefit.dto.LimitBasis;
import com.fitwallet.domain.benefit.dto.ValueType;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitBrandTarget;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitCategoryTarget;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitLimitStatus;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitPeriod;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitRule;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitTargetUsage;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitUnit;
import com.fitwallet.domain.card.dto.CardSummaryCardInfo;
import com.fitwallet.domain.card.dto.CardUsagePerformanceStatus;
import com.fitwallet.domain.card.dto.CardUsageTierState;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitCardResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitLimitResponse;
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
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 월간 혜택 원본 조회 결과를 검증하고 화면 응답으로 조합한다. */
@Component
@RequiredArgsConstructor
public class CardMonthlyBenefitCalculator {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final String ACHIEVED_MESSAGE = "전월 실적 조건이 적용 중이에요.";
    private static final String INSUFFICIENT_MESSAGE = "전월 실적 조건을 충족하지 못했어요.";
    private static final String NO_REQUIREMENT_MESSAGE = "전월 실적 조건이 없어요.";

    private final CardBenefitValueLabelFormatter benefitValueLabelFormatter;

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
        UsageIndex usageIndex = createUsageIndex(usages, rulesByService);
        validateSharedPointCurrencies(selectedServices);

        SummaryAmounts summaryAmounts = calculateSummary(selectedServices, rulesByService, usageIndex);

        List<CardMonthlyCategoryBenefitResponse> categoryBenefits = createCategoryBenefits(
                selectedServices, categoryTargets, usageIndex);
        List<CardMonthlyBrandBenefitResponse> brandBenefits = createBrandBenefits(
                selectedServices, brandTargets, usageIndex);

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

    private List<CardMonthlyCategoryBenefitResponse> createCategoryBenefits(
            List<SelectedService> selectedServices,
            List<CardMonthlyBenefitCategoryTarget> targets,
            UsageIndex usageIndex) {
        Map<Long, List<CardMonthlyBenefitCategoryTarget>> targetsByService = new HashMap<>();
        targets.forEach(target -> targetsByService
                .computeIfAbsent(target.getServiceId(), ignored -> new ArrayList<>()).add(target));

        List<CardMonthlyCategoryBenefitResponse> responses = new ArrayList<>();
        for (SelectedService service : selectedServices) {
            if (service.definition.getScopeType() != BenefitScopeType.INDUSTRY) {
                continue;
            }
            List<CardMonthlyBenefitCategoryTarget> serviceTargets = targetsByService
                    .getOrDefault(service.definition.getServiceId(), List.of());
            if (serviceTargets.isEmpty()) {
                throw invalidData();
            }
            List<CardMonthlyBenefitLimitResponse> limits = createLimitResponses(service, usageIndex);
            CardMonthlyBenefitLimitStatus itemStatus = itemStatus(limits);
            for (CardMonthlyBenefitCategoryTarget target : serviceTargets) {
                UsageTotals usage = usageIndex.category.getOrDefault(
                        targetKey(service.definition.getServiceId(), target.getCategoryId()), UsageTotals.ZERO);
                responses.add(CardMonthlyCategoryBenefitResponse.builder()
                        .benefitServiceId(service.definition.getServiceId())
                        .categoryId(target.getCategoryId())
                        .categoryName(target.getCategoryName())
                        .categoryImageUrl(target.getCategoryImageUrl())
                        .displayName(categoryDisplayName(target, service.definition.getBenefitName()))
                        .benefitType(service.definition.getBenefitType())
                        .valueType(service.definition.getValueType())
                        .valueNumber(service.definition.getValueNumber())
                        .valueUnit(valueUnit(service.definition))
                        .pointCurrencyName(service.definition.getPointCurrencyName())
                        .valueLabel(valueLabel(service.definition))
                        .perTransactionLimitValue(perTransactionLimitValue(service.definition))
                        .perTransactionLimitLabel(perTransactionLimitLabel(service.definition))
                        .transactionCount(usage.count)
                        .totalPaymentAmount(money(usage.payment))
                        .receivedBenefitValue(receivedDisplayValue(service.definition, usage.received))
                        .receivedBenefitLabel(receivedLabel(service.definition, usage.received))
                        .monthlyLimits(limits)
                        .itemLimitStatus(itemStatus)
                        .build());
            }
        }
        responses.sort(Comparator
                .comparing(CardMonthlyCategoryBenefitResponse::getItemLimitStatus)
                .thenComparing(CardMonthlyCategoryBenefitResponse::getCategoryId)
                .thenComparing(CardMonthlyCategoryBenefitResponse::getBenefitServiceId));
        return List.copyOf(responses);
    }

    private List<CardMonthlyBrandBenefitResponse> createBrandBenefits(
            List<SelectedService> selectedServices,
            List<CardMonthlyBenefitBrandTarget> targets,
            UsageIndex usageIndex) {
        Map<Long, List<CardMonthlyBenefitBrandTarget>> targetsByService = new HashMap<>();
        targets.forEach(target -> targetsByService
                .computeIfAbsent(target.getServiceId(), ignored -> new ArrayList<>()).add(target));

        List<CardMonthlyBrandBenefitResponse> responses = new ArrayList<>();
        for (SelectedService service : selectedServices) {
            if (service.definition.getScopeType() != BenefitScopeType.BRAND) {
                continue;
            }
            List<CardMonthlyBenefitBrandTarget> serviceTargets = targetsByService
                    .getOrDefault(service.definition.getServiceId(), List.of());
            if (serviceTargets.isEmpty()) {
                throw invalidData();
            }
            List<CardMonthlyBenefitLimitResponse> limits = createLimitResponses(service, usageIndex);
            CardMonthlyBenefitLimitStatus itemStatus = itemStatus(limits);
            for (CardMonthlyBenefitBrandTarget target : serviceTargets) {
                UsageTotals usage = usageIndex.brand.getOrDefault(
                        targetKey(service.definition.getServiceId(), target.getBrandId()), UsageTotals.ZERO);
                responses.add(CardMonthlyBrandBenefitResponse.builder()
                        .benefitServiceId(service.definition.getServiceId())
                        .brandId(target.getBrandId())
                        .brandName(target.getBrandName())
                        .brandImageUrl(target.getBrandImageUrl())
                        .displayName(target.getBrandName())
                        .benefitType(service.definition.getBenefitType())
                        .valueType(service.definition.getValueType())
                        .valueNumber(service.definition.getValueNumber())
                        .valueUnit(valueUnit(service.definition))
                        .pointCurrencyName(service.definition.getPointCurrencyName())
                        .valueLabel(valueLabel(service.definition))
                        .perTransactionLimitValue(perTransactionLimitValue(service.definition))
                        .perTransactionLimitLabel(perTransactionLimitLabel(service.definition))
                        .transactionCount(usage.count)
                        .totalPaymentAmount(money(usage.payment))
                        .receivedBenefitValue(receivedDisplayValue(service.definition, usage.received))
                        .receivedBenefitLabel(receivedLabel(service.definition, usage.received))
                        .monthlyLimits(limits)
                        .itemLimitStatus(itemStatus)
                        .build());
            }
        }
        responses.sort(Comparator
                .comparing(CardMonthlyBrandBenefitResponse::getItemLimitStatus)
                .thenComparing(CardMonthlyBrandBenefitResponse::getBrandId)
                .thenComparing(CardMonthlyBrandBenefitResponse::getBenefitServiceId));
        return List.copyOf(responses);
    }

    private List<CardMonthlyBenefitLimitResponse> createLimitResponses(
            SelectedService service,
            UsageIndex usageIndex) {
        List<CardMonthlyBenefitLimitResponse> responses = new ArrayList<>();
        for (CardMonthlyBenefitRule limit : service.limits) {
            UsageTotals usage = limitUsage(limit, usageIndex);
            BigDecimal rawUsed = rawLimitUsageValue(service.definition, limit, usage);
            BigDecimal rawRemaining = limit.getLimitValue().subtract(rawUsed).max(BigDecimal.ZERO);
            CardMonthlyBenefitUnit unit = limitUnit(service.definition, limit.getLimitBasis());
            BigDecimal displayLimit = displayLimitValue(service.definition, limit, limit.getLimitValue());
            BigDecimal displayUsed = displayLimitValue(service.definition, limit, rawUsed);
            BigDecimal displayRemaining = displayLimitValue(service.definition, limit, rawRemaining);
            CardMonthlyBenefitLimitStatus status = rawRemaining.signum() == 0
                    ? CardMonthlyBenefitLimitStatus.LIMIT_EXHAUSTED
                    : CardMonthlyBenefitLimitStatus.AVAILABLE;

            responses.add(CardMonthlyBenefitLimitResponse.builder()
                    .limitBasis(limit.getLimitBasis())
                    .limitValue(displayLimit)
                    .usedValue(displayUsed)
                    .remainingValue(displayRemaining)
                    .limitUnit(unit)
                    .limitLabel(limitLabel(displayUsed, displayLimit, unit))
                    .limitStatus(status)
                    .shared(limit.isShared())
                    .build());
        }
        responses.sort(Comparator.comparing(response -> response.getLimitBasis().ordinal()));
        return List.copyOf(responses);
    }

    private BigDecimal rawLimitUsageValue(
            CardMonthlyBenefitRule definition,
            CardMonthlyBenefitRule limit,
            UsageTotals usage) {
        return switch (limit.getLimitBasis()) {
            case COUNT -> BigDecimal.valueOf(usage.count);
            case AMOUNT -> usage.received;
            case POINT -> {
                if (definition.getBenefitType() != BenefitType.ACCUMULATE) {
                    throw invalidData();
                }
                yield usage.received.divide(
                        validPointRate(definition.getKrwPerPoint()), 12, RoundingMode.DOWN);
            }
        };
    }

    private SummaryAmounts calculateSummary(
            List<SelectedService> selectedServices,
            Map<Long, List<CardMonthlyBenefitRule>> allRulesByService,
            UsageIndex usageIndex) {
        if (selectedServices.isEmpty()) {
            return SummaryAmounts.ZERO;
        }

        Map<Long, Set<Long>> groupMembers = new HashMap<>();
        allRulesByService.forEach((serviceId, serviceRules) -> {
            serviceRules.stream()
                    .map(CardMonthlyBenefitRule::getLimitPlanGroupId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .ifPresent(planGroupId -> groupMembers
                            .computeIfAbsent(planGroupId, ignored -> new LinkedHashSet<>())
                            .add(serviceId));
        });

        Map<Long, LimitContext> uniqueLimits = new LinkedHashMap<>();
        Map<Long, Integer> monetaryLimitCountByService = new HashMap<>();
        Map<Long, Integer> countLimitCountByService = new HashMap<>();
        for (SelectedService service : selectedServices) {
            for (CardMonthlyBenefitRule limit : service.limits) {
                LimitContext context = uniqueLimits.computeIfAbsent(
                        limit.getLimitId(), ignored -> new LimitContext(limit));
                context.services.add(service);
                if (limit.getLimitBasis() == LimitBasis.COUNT) {
                    countLimitCountByService.merge(service.definition.getServiceId(), 1, Integer::sum);
                } else {
                    monetaryLimitCountByService.merge(service.definition.getServiceId(), 1, Integer::sum);
                }
            }
        }
        if (monetaryLimitCountByService.values().stream().anyMatch(count -> count > 1)) {
            throw invalidData();
        }

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal potential = BigDecimal.ZERO;
        for (LimitContext context : uniqueLimits.values()) {
            CardMonthlyBenefitRule limit = context.rule;
            if (limit.getLimitBasis() == LimitBasis.COUNT) {
                boolean countOnly = context.services.stream().allMatch(service ->
                        monetaryLimitCountByService.getOrDefault(service.definition.getServiceId(), 0) == 0);
                if (!countOnly) {
                    continue;
                }
                if (context.services.stream().anyMatch(service ->
                        countLimitCountByService.getOrDefault(service.definition.getServiceId(), 0) > 1)) {
                    throw invalidData();
                }
                BigDecimal perTransaction = context.services.stream()
                        .map(service -> perTransactionKrw(service.definition))
                        .max(BigDecimal::compareTo)
                        .orElseThrow(this::invalidData);
                UsageTotals usage = limitUsage(limit, usageIndex, groupMembers);
                BigDecimal remainingCount = limit.getLimitValue()
                        .subtract(BigDecimal.valueOf(usage.count)).max(BigDecimal.ZERO);
                total = total.add(limit.getLimitValue().multiply(perTransaction));
                potential = potential.add(remainingCount.multiply(perTransaction));
                continue;
            }

            BigDecimal totalKrw = monetaryLimitKrw(context);
            UsageTotals usage = limitUsage(limit, usageIndex, groupMembers);
            BigDecimal remainingKrw = totalKrw.subtract(usage.received).max(BigDecimal.ZERO);
            BigDecimal countCapacity = countCapacity(context.services, usageIndex, groupMembers);
            total = total.add(totalKrw);
            potential = potential.add(countCapacity == null
                    ? remainingKrw
                    : remainingKrw.min(countCapacity));
        }

        BigDecimal received = selectedServices.stream()
                .map(service -> usageIndex.service
                        .getOrDefault(service.definition.getServiceId(), UsageTotals.ZERO).received)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SummaryAmounts(money(total), money(potential), money(received));
    }

    private BigDecimal countCapacity(
            Set<SelectedService> services,
            UsageIndex usageIndex,
            Map<Long, Set<Long>> groupMembers) {
        BigDecimal capacity = BigDecimal.ZERO;
        for (SelectedService service : services) {
            List<CardMonthlyBenefitRule> countLimits = service.limits.stream()
                    .filter(limit -> limit.getLimitBasis() == LimitBasis.COUNT)
                    .toList();
            if (countLimits.isEmpty()) {
                return null;
            }
            BigDecimal remainingCount = null;
            for (CardMonthlyBenefitRule countLimit : countLimits) {
                UsageTotals usage = limitUsage(countLimit, usageIndex, groupMembers);
                BigDecimal remaining = countLimit.getLimitValue()
                        .subtract(BigDecimal.valueOf(usage.count)).max(BigDecimal.ZERO);
                remainingCount = remainingCount == null ? remaining : remainingCount.min(remaining);
            }
            capacity = capacity.add(remainingCount.multiply(perTransactionKrw(service.definition)));
        }
        return capacity;
    }

    private BigDecimal monetaryLimitKrw(LimitContext context) {
        CardMonthlyBenefitRule limit = context.rule;
        if (limit.getLimitBasis() == LimitBasis.AMOUNT) {
            return limit.getLimitValue();
        }
        if (limit.getLimitBasis() != LimitBasis.POINT) {
            throw invalidData();
        }
        BigDecimal rate = context.services.iterator().next().definition.getKrwPerPoint();
        return limit.getLimitValue().multiply(validPointRate(rate));
    }

    private UsageTotals limitUsage(CardMonthlyBenefitRule limit, UsageIndex usageIndex) {
        return limitUsage(limit, usageIndex, null);
    }

    private UsageTotals limitUsage(
            CardMonthlyBenefitRule limit,
            UsageIndex usageIndex,
            Map<Long, Set<Long>> groupMembers) {
        if (!limit.isShared()) {
            return usageIndex.service.getOrDefault(limit.getServiceId(), UsageTotals.ZERO);
        }
        Set<Long> serviceIds = groupMembers == null
                ? usageIndex.planGroupServices.getOrDefault(limit.getLimitPlanGroupId(), Set.of())
                : groupMembers.getOrDefault(limit.getLimitPlanGroupId(), Set.of());
        UsageTotals total = new UsageTotals();
        serviceIds.forEach(serviceId -> total.add(
                usageIndex.service.getOrDefault(serviceId, UsageTotals.ZERO)));
        return total;
    }

    private UsageIndex createUsageIndex(
            List<CardMonthlyBenefitTargetUsage> usages,
            Map<Long, List<CardMonthlyBenefitRule>> rulesByService) {
        UsageIndex index = new UsageIndex();
        rulesByService.forEach((serviceId, serviceRules) -> {
            serviceRules.stream()
                    .map(CardMonthlyBenefitRule::getLimitPlanGroupId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .ifPresent(planGroupId -> index.planGroupServices
                            .computeIfAbsent(planGroupId, ignored -> new LinkedHashSet<>())
                            .add(serviceId));
        });
        for (CardMonthlyBenefitTargetUsage usage : usages) {
            if (usage.getServiceId() == null || usage.getTotalPaymentAmount() == null
                    || usage.getReceivedBenefitAmount() == null) {
                throw invalidData();
            }
            UsageTotals values = new UsageTotals(
                    usage.getTransactionCount(),
                    usage.getTotalPaymentAmount(),
                    usage.getReceivedBenefitAmount());
            index.service.computeIfAbsent(usage.getServiceId(), ignored -> new UsageTotals()).add(values);
            if (usage.getCategoryId() != null) {
                index.category.computeIfAbsent(
                        targetKey(usage.getServiceId(), usage.getCategoryId()),
                        ignored -> new UsageTotals()).add(values);
            }
            if (usage.getBrandId() != null) {
                index.brand.computeIfAbsent(
                        targetKey(usage.getServiceId(), usage.getBrandId()),
                        ignored -> new UsageTotals()).add(values);
            }
        }
        return index;
    }

    private void validateSharedPointCurrencies(List<SelectedService> services) {
        Map<Long, String> currencyByLimit = new HashMap<>();
        Map<Long, BigDecimal> rateByLimit = new HashMap<>();
        for (SelectedService service : services) {
            validatePointCurrency(service.definition);
            for (CardMonthlyBenefitRule limit : service.limits) {
                if (!limit.isShared() || limit.getLimitBasis() != LimitBasis.POINT) {
                    continue;
                }
                String previousCurrency = currencyByLimit.putIfAbsent(
                        limit.getLimitId(), service.definition.getPointCurrencyName());
                BigDecimal previousRate = rateByLimit.putIfAbsent(
                        limit.getLimitId(), service.definition.getKrwPerPoint());
                if ((previousCurrency != null
                        && !previousCurrency.equals(service.definition.getPointCurrencyName()))
                        || (previousRate != null
                        && previousRate.compareTo(service.definition.getKrwPerPoint()) != 0)) {
                    throw invalidData();
                }
            }
        }

    }

    private CardMonthlyBenefitLimitStatus itemStatus(
            List<CardMonthlyBenefitLimitResponse> limits) {
        return limits.stream().anyMatch(limit ->
                limit.getLimitStatus() == CardMonthlyBenefitLimitStatus.LIMIT_EXHAUSTED)
                ? CardMonthlyBenefitLimitStatus.LIMIT_EXHAUSTED
                : CardMonthlyBenefitLimitStatus.AVAILABLE;
    }

    private BigDecimal displayLimitValue(
            CardMonthlyBenefitRule definition,
            CardMonthlyBenefitRule limit,
            BigDecimal value) {
        if (limit.getLimitBasis() == LimitBasis.COUNT) {
            return whole(value);
        }
        if (definition.getBenefitType() == BenefitType.ACCUMULATE) {
            if (limit.getLimitBasis() == LimitBasis.AMOUNT) {
                return whole(value.divide(validPointRate(definition.getKrwPerPoint()), 12, RoundingMode.DOWN));
            }
            if (limit.getLimitBasis() == LimitBasis.POINT) {
                return whole(value);
            }
        }
        if (definition.getBenefitType() == BenefitType.CASHBACK
                && limit.getLimitBasis() == LimitBasis.AMOUNT) {
            return money(value);
        }
        throw invalidData();
    }

    private CardMonthlyBenefitUnit limitUnit(
            CardMonthlyBenefitRule definition,
            LimitBasis basis) {
        if (basis == LimitBasis.COUNT) {
            return CardMonthlyBenefitUnit.COUNT;
        }
        if (definition.getBenefitType() == BenefitType.ACCUMULATE) {
            return CardMonthlyBenefitUnit.POINT;
        }
        if (basis == LimitBasis.AMOUNT) {
            return CardMonthlyBenefitUnit.KRW;
        }
        throw invalidData();
    }

    private CardMonthlyBenefitUnit valueUnit(CardMonthlyBenefitRule definition) {
        if (definition.getValueType() == ValueType.RATE) {
            return CardMonthlyBenefitUnit.PERCENT;
        }
        return definition.getBenefitType() == BenefitType.ACCUMULATE
                ? CardMonthlyBenefitUnit.POINT
                : CardMonthlyBenefitUnit.KRW;
    }

    private String valueLabel(CardMonthlyBenefitRule definition) {
        return benefitValueLabelFormatter.formatValueWithAction(
                definition.getBenefitName(),
                definition.getBenefitType(),
                definition.getValueType(),
                definition.getValueNumber(),
                definition.getPointCurrencyName());
    }

    private BigDecimal perTransactionLimitValue(CardMonthlyBenefitRule definition) {
        return definition.getPerTransactionLimitAmount() == null
                ? null : whole(definition.getPerTransactionLimitAmount());
    }

    private String perTransactionLimitLabel(CardMonthlyBenefitRule definition) {
        BigDecimal value = perTransactionLimitValue(definition);
        if (value == null) {
            return null;
        }
        CardMonthlyBenefitUnit unit = definition.getBenefitType() == BenefitType.ACCUMULATE
                ? CardMonthlyBenefitUnit.POINT : CardMonthlyBenefitUnit.KRW;
        return "건당 최대 " + format(value) + unitSuffix(unit);
    }

    private BigDecimal receivedDisplayValue(
            CardMonthlyBenefitRule definition,
            BigDecimal receivedKrw) {
        if (definition.getBenefitType() == BenefitType.ACCUMULATE) {
            return whole(receivedKrw.divide(
                    validPointRate(definition.getKrwPerPoint()), 12, RoundingMode.DOWN));
        }
        return money(receivedKrw);
    }

    private String receivedLabel(CardMonthlyBenefitRule definition, BigDecimal receivedKrw) {
        BigDecimal value = receivedDisplayValue(definition, receivedKrw);
        String unit = definition.getBenefitType() == BenefitType.ACCUMULATE ? "P" : "원";
        String action = definition.getBenefitType() == BenefitType.ACCUMULATE ? " 적립" : " 할인";
        return "총 " + format(value) + unit + action;
    }

    private BigDecimal perTransactionKrw(CardMonthlyBenefitRule definition) {
        BigDecimal value = definition.getPerTransactionLimitAmount();
        if (value == null && definition.getValueType() == ValueType.FIXED) {
            value = definition.getValueNumber();
        }
        if (value == null || value.signum() <= 0) {
            throw invalidData();
        }
        return definition.getBenefitType() == BenefitType.ACCUMULATE
                ? value.multiply(validPointRate(definition.getKrwPerPoint()))
                : value;
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

    private String categoryDisplayName(
            CardMonthlyBenefitCategoryTarget target,
            String benefitName) {
        if (!Objects.equals(target.getCategoryName(), "편의점/마트")) {
            return target.getCategoryName();
        }
        if (benefitName.contains("편의점")) {
            return "편의점";
        }
        if (benefitName.contains("할인마트") || benefitName.contains("마트")) {
            return "마트";
        }
        return target.getCategoryName();
    }

    private String limitLabel(
            BigDecimal used,
            BigDecimal limit,
            CardMonthlyBenefitUnit unit) {
        return format(used) + unitSuffix(unit) + " / " + format(limit) + unitSuffix(unit);
    }

    private String unitSuffix(CardMonthlyBenefitUnit unit) {
        return switch (unit) {
            case PERCENT -> "%";
            case KRW -> "원";
            case POINT -> "P";
            case COUNT -> "회";
        };
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

    private void validatePointCurrency(CardMonthlyBenefitRule definition) {
        if (definition.getBenefitType() == BenefitType.ACCUMULATE
                && (definition.getPointCurrencyName() == null
                || definition.getPointCurrencyName().isBlank()
                || definition.getKrwPerPoint() == null
                || definition.getKrwPerPoint().signum() <= 0)) {
            throw invalidData();
        }
    }

    private BigDecimal validPointRate(BigDecimal rate) {
        if (rate == null || rate.signum() <= 0) {
            throw invalidData();
        }
        return rate;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(0, RoundingMode.DOWN);
    }

    private BigDecimal whole(BigDecimal value) {
        return value.setScale(0, RoundingMode.DOWN);
    }

    private String format(BigDecimal value) {
        return new DecimalFormat("#,##0.##").format(value);
    }

    private String targetKey(Long serviceId, Long targetId) {
        return serviceId + ":" + targetId;
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

    private static final class SelectedService {
        private final CardMonthlyBenefitRule definition;
        private final List<CardMonthlyBenefitRule> limits;

        private SelectedService(
                CardMonthlyBenefitRule definition,
                List<CardMonthlyBenefitRule> limits) {
            this.definition = definition;
            this.limits = limits;
        }
    }

    private static final class LimitContext {
        private final CardMonthlyBenefitRule rule;
        private final Set<SelectedService> services = new LinkedHashSet<>();

        private LimitContext(CardMonthlyBenefitRule rule) {
            this.rule = rule;
        }
    }

    private static final class UsageIndex {
        private final Map<Long, UsageTotals> service = new HashMap<>();
        private final Map<String, UsageTotals> category = new HashMap<>();
        private final Map<String, UsageTotals> brand = new HashMap<>();
        private final Map<Long, Set<Long>> planGroupServices = new HashMap<>();
    }

    private static final class UsageTotals {
        private static final UsageTotals ZERO = new UsageTotals();

        private long count;
        private BigDecimal payment = BigDecimal.ZERO;
        private BigDecimal received = BigDecimal.ZERO;

        private UsageTotals() {
        }

        private UsageTotals(long count, BigDecimal payment, BigDecimal received) {
            this.count = count;
            this.payment = payment;
            this.received = received;
        }

        private void add(UsageTotals other) {
            count += other.count;
            payment = payment.add(other.payment);
            received = received.add(other.received);
        }
    }

    private static final class SummaryAmounts {
        private static final SummaryAmounts ZERO = new SummaryAmounts(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        private final BigDecimal total;
        private final BigDecimal potential;
        private final BigDecimal received;

        private SummaryAmounts(BigDecimal total, BigDecimal potential, BigDecimal received) {
            this.total = total;
            this.potential = potential;
            this.received = received;
        }
    }
}
