package com.fitwallet.domain.card.service;

import com.fitwallet.domain.benefit.dto.BenefitType;
import com.fitwallet.domain.benefit.dto.LimitBasis;
import com.fitwallet.domain.benefit.dto.ValueType;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitRule;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitTargetUsage;
import com.fitwallet.domain.card.exception.CardErrorCode;
import com.fitwallet.global.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.fitwallet.domain.card.service.CardMonthlyBenefitCalculationContext.LimitUsageResult;
import static com.fitwallet.domain.card.service.CardMonthlyBenefitCalculationContext.SelectedService;
import static com.fitwallet.domain.card.service.CardMonthlyBenefitCalculationContext.SummaryAmounts;
import static com.fitwallet.domain.card.service.CardMonthlyBenefitCalculationContext.UsageIndex;
import static com.fitwallet.domain.card.service.CardMonthlyBenefitCalculationContext.UsageTotals;

@Component
public class CardMonthlyBenefitUsageCalculator {

    UsageIndex createUsageIndex(
            List<CardMonthlyBenefitTargetUsage> usages,
            Map<Long, List<CardMonthlyBenefitRule>> rulesByService) {
        UsageIndex index = new UsageIndex();
        rulesByService.forEach((serviceId, serviceRules) -> {
            serviceRules.stream()
                    .map(CardMonthlyBenefitRule::getServicePlanGroupId)
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

    void validatePointCurrencies(List<SelectedService> services) {
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

    SummaryAmounts calculateSummaryAmounts(
            List<SelectedService> selectedServices,
            Map<Long, List<CardMonthlyBenefitRule>> allRulesByService,
            UsageIndex usageIndex) {
        if (selectedServices.isEmpty()) {
            return SummaryAmounts.ZERO;
        }
        Map<Long, Set<Long>> groupMembers = new HashMap<>();
        allRulesByService.forEach((serviceId, serviceRules) -> serviceRules.stream()
                .map(CardMonthlyBenefitRule::getLimitPlanGroupId)
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(planGroupId -> groupMembers
                        .computeIfAbsent(planGroupId, ignored -> new LinkedHashSet<>())
                        .add(serviceId)));

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
                UsageTotals usage = calculateSummaryLimitUsage(limit, usageIndex, groupMembers);
                BigDecimal remainingCount = limit.getLimitValue()
                        .subtract(BigDecimal.valueOf(usage.count)).max(BigDecimal.ZERO);
                total = total.add(limit.getLimitValue().multiply(perTransaction));
                potential = potential.add(remainingCount.multiply(perTransaction));
                continue;
            }
            BigDecimal totalKrw = monetaryLimitKrw(context);
            UsageTotals usage = calculateSummaryLimitUsage(limit, usageIndex, groupMembers);
            BigDecimal remainingKrw = totalKrw.subtract(usage.received).max(BigDecimal.ZERO);
            BigDecimal countCapacity = countCapacity(context.services, usageIndex, groupMembers);
            total = total.add(totalKrw);
            potential = potential.add(countCapacity == null
                    ? remainingKrw : remainingKrw.min(countCapacity));
        }
        BigDecimal received = selectedServices.stream()
                .map(service -> usageIndex.service
                        .getOrDefault(service.definition.getServiceId(), UsageTotals.ZERO).received)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SummaryAmounts(money(total), money(potential), money(received));
    }

    LimitUsageResult calculateItemLimitResult(
            CardMonthlyBenefitRule definition,
            CardMonthlyBenefitRule limit,
            UsageIndex usageIndex) {
        UsageTotals usage = calculateItemLimitUsage(limit, usageIndex);
        BigDecimal rawUsed = calculateRawLimitUsageValue(definition, limit, usage);
        BigDecimal rawRemaining = limit.getLimitValue().subtract(rawUsed).max(BigDecimal.ZERO);
        return new LimitUsageResult(rawUsed, rawRemaining);
    }

    BigDecimal calculateLimitUsageValue(
            CardMonthlyBenefitRule definition,
            CardMonthlyBenefitRule limit,
            UsageTotals usage) {
        return calculateRawLimitUsageValue(definition, limit, usage);
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
                UsageTotals usage = calculateSummaryLimitUsage(countLimit, usageIndex, groupMembers);
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

    private UsageTotals calculateItemLimitUsage(
            CardMonthlyBenefitRule limit, UsageIndex usageIndex) {
        return calculateLimitUsage(limit, usageIndex, null);
    }

    private UsageTotals calculateSummaryLimitUsage(
            CardMonthlyBenefitRule limit,
            UsageIndex usageIndex,
            Map<Long, Set<Long>> groupMembers) {
        return calculateLimitUsage(limit, usageIndex, groupMembers);
    }

    private UsageTotals calculateLimitUsage(
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

    private BigDecimal calculateRawLimitUsageValue(
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

    private BigDecimal perTransactionKrw(CardMonthlyBenefitRule definition) {
        BigDecimal value = definition.getPerTransactionLimitAmount();
        if (value == null && definition.getValueType() == ValueType.FIXED) {
            value = definition.getValueNumber();
        }
        if (value == null || value.signum() <= 0) {
            throw invalidData();
        }
        return definition.getBenefitType() == BenefitType.ACCUMULATE
                ? value.multiply(validPointRate(definition.getKrwPerPoint())) : value;
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

    private String targetKey(Long serviceId, Long targetId) {
        return serviceId + ":" + targetId;
    }

    private BusinessException invalidData() {
        return new BusinessException(CardErrorCode.INVALID_CARD_MONTHLY_BENEFIT_DATA);
    }

    private static final class LimitContext {
        private final CardMonthlyBenefitRule rule;
        private final Set<SelectedService> services = new LinkedHashSet<>();

        private LimitContext(CardMonthlyBenefitRule rule) {
            this.rule = rule;
        }
    }
}
