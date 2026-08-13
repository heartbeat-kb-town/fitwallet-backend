package com.fitwallet.domain.card.service;

import com.fitwallet.domain.benefit.dto.BenefitScopeType;
import com.fitwallet.domain.benefit.dto.BenefitType;
import com.fitwallet.domain.benefit.dto.LimitBasis;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitBrandTarget;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitCategoryTarget;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitLimitStatus;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitRule;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitUnit;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitLimitResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitGroupCategoryResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitServiceResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitSharedLimitGroupResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitSharedLimitUsageResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitTargetResponse;
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

import static com.fitwallet.domain.card.service.CardMonthlyBenefitCalculationContext.LimitUsageResult;
import static com.fitwallet.domain.card.service.CardMonthlyBenefitCalculationContext.LimitUsageObservation;
import static com.fitwallet.domain.card.service.CardMonthlyBenefitCalculationContext.LimitUsageSnapshot;
import static com.fitwallet.domain.card.service.CardMonthlyBenefitCalculationContext.LimitUsageSnapshotBuilder;
import static com.fitwallet.domain.card.service.CardMonthlyBenefitCalculationContext.SelectedService;
import static com.fitwallet.domain.card.service.CardMonthlyBenefitCalculationContext.UsageIndex;
import static com.fitwallet.domain.card.service.CardMonthlyBenefitCalculationContext.UsageTotals;

@Component
@RequiredArgsConstructor
public class CardMonthlyBenefitItemAssembler {

    private final CardMonthlyBenefitUsageCalculator usageCalculator;
    private final CardBenefitValueLabelFormatter benefitValueLabelFormatter;

    List<CardMonthlyCategoryBenefitResponse> createCategoryBenefits(
            List<SelectedService> selectedServices,
            List<CardMonthlyBenefitCategoryTarget> targets,
            UsageIndex usageIndex,
            LimitUsageSnapshotBuilder snapshotBuilder) {
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
            List<CardMonthlyBenefitLimitResponse> limits = createLimitResponses(
                    service, usageIndex, snapshotBuilder);
            CardMonthlyBenefitLimitStatus itemStatus = itemStatus(limits);
            for (CardMonthlyBenefitCategoryTarget target : serviceTargets) {
                UsageTotals usage = usageIndex.category.getOrDefault(
                        targetKey(service.definition.getServiceId(), target.getCategoryId()), UsageTotals.ZERO);
                responses.add(CardMonthlyCategoryBenefitResponse.builder()
                        .limitGroupId(limitGroupId(service))
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

    List<CardMonthlyBrandBenefitResponse> createBrandBenefits(
            List<SelectedService> selectedServices,
            List<CardMonthlyBenefitBrandTarget> targets,
            UsageIndex usageIndex,
            LimitUsageSnapshotBuilder snapshotBuilder) {
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
            List<CardMonthlyBenefitLimitResponse> limits = createLimitResponses(
                    service, usageIndex, snapshotBuilder);
            CardMonthlyBenefitLimitStatus itemStatus = itemStatus(limits);
            for (CardMonthlyBenefitBrandTarget target : serviceTargets) {
                UsageTotals usage = usageIndex.brand.getOrDefault(
                        targetKey(service.definition.getServiceId(), target.getBrandId()), UsageTotals.ZERO);
                responses.add(CardMonthlyBrandBenefitResponse.builder()
                        .limitGroupId(limitGroupId(service))
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

    List<CardMonthlyBenefitSharedLimitGroupResponse> createSharedLimitGroups(
            List<SelectedService> selectedServices,
            List<CardMonthlyBenefitCategoryTarget> categoryTargets,
            List<CardMonthlyBenefitBrandTarget> brandTargets,
            UsageIndex usageIndex,
            LimitUsageSnapshot snapshot) {
        Map<Long, List<CardMonthlyBenefitCategoryTarget>> categoriesByService = new HashMap<>();
        categoryTargets.forEach(target -> categoriesByService
                .computeIfAbsent(target.getServiceId(), ignored -> new ArrayList<>()).add(target));
        Map<Long, List<CardMonthlyBenefitBrandTarget>> brandsByService = new HashMap<>();
        brandTargets.forEach(target -> brandsByService
                .computeIfAbsent(target.getServiceId(), ignored -> new ArrayList<>()).add(target));

        Map<Long, List<SelectedService>> servicesByGroup = new LinkedHashMap<>();
        for (SelectedService service : selectedServices) {
            Long groupId = limitGroupId(service);
            if (groupId != null) {
                servicesByGroup.computeIfAbsent(groupId, ignored -> new ArrayList<>()).add(service);
            }
        }

        List<CardMonthlyBenefitSharedLimitGroupResponse> groups = new ArrayList<>();
        for (Map.Entry<Long, List<SelectedService>> groupEntry : servicesByGroup.entrySet()) {
            Long groupId = groupEntry.getKey();
            List<SelectedService> services = groupEntry.getValue();
            Map<Long, CardMonthlyBenefitRule> sharedLimits = new LinkedHashMap<>();
            for (SelectedService service : services) {
                service.limits.stream()
                        .filter(CardMonthlyBenefitRule::isShared)
                        .forEach(limit -> sharedLimits.putIfAbsent(limit.getLimitId(), limit));
            }
            if (sharedLimits.size() != 1) {
                throw invalidData();
            }

            CardMonthlyBenefitRule sharedLimit = sharedLimits.values().iterator().next();
            LimitUsageObservation sharedObservation = snapshot.get(sharedLimit.getLimitId());
            List<CardMonthlyBenefitServiceResponse> serviceResponses = new ArrayList<>();
            List<CardMonthlyBenefitSharedLimitUsageResponse> breakdown = new ArrayList<>();
            BigDecimal serviceUsedTotal = BigDecimal.ZERO;
            for (SelectedService service : services) {
                SharedServiceAssembly assembly = createSharedService(
                        service,
                        sharedLimit,
                        categoriesByService.getOrDefault(service.definition.getServiceId(), List.of()),
                        brandsByService.getOrDefault(service.definition.getServiceId(), List.of()),
                        usageIndex,
                        snapshot);
                serviceResponses.add(assembly.response);
                breakdown.addAll(assembly.breakdown);
                serviceUsedTotal = serviceUsedTotal.add(assembly.rawSharedLimitUsed);
            }
            if (serviceUsedTotal.compareTo(sharedObservation.result.rawUsed) != 0) {
                throw invalidData();
            }

            CardMonthlyBenefitLimitStatus groupStatus = serviceResponses.stream()
                    .anyMatch(service -> service.getServiceLimitStatus()
                            == CardMonthlyBenefitLimitStatus.AVAILABLE)
                    ? CardMonthlyBenefitLimitStatus.AVAILABLE
                    : CardMonthlyBenefitLimitStatus.LIMIT_EXHAUSTED;
            groups.add(CardMonthlyBenefitSharedLimitGroupResponse.builder()
                    .limitGroupId(groupId)
                    .categories(groupCategories(services, categoriesByService, brandsByService))
                    .sharedMonthlyLimit(sharedObservation.response)
                    .groupLimitStatus(groupStatus)
                    .usageBreakdown(List.copyOf(breakdown))
                    .benefitServices(List.copyOf(serviceResponses))
                    .build());
        }
        groups.sort(Comparator.comparing(CardMonthlyBenefitSharedLimitGroupResponse::getLimitGroupId));
        return List.copyOf(groups);
    }

    private SharedServiceAssembly createSharedService(
            SelectedService service,
            CardMonthlyBenefitRule sharedLimit,
            List<CardMonthlyBenefitCategoryTarget> categoryTargets,
            List<CardMonthlyBenefitBrandTarget> brandTargets,
            UsageIndex usageIndex,
            LimitUsageSnapshot snapshot) {
        Long serviceId = service.definition.getServiceId();
        UsageTotals serviceUsage = usageIndex.service.getOrDefault(serviceId, UsageTotals.ZERO);
        List<CardMonthlyBenefitTargetResponse> targetResponses = new ArrayList<>();
        List<CardMonthlyBenefitSharedLimitUsageResponse> breakdown = new ArrayList<>();
        UsageTotals attributedUsage = new UsageTotals();
        BigDecimal attributedRawUsed = BigDecimal.ZERO;

        if (service.definition.getScopeType() == BenefitScopeType.INDUSTRY) {
            if (categoryTargets.isEmpty()) {
                throw invalidData();
            }
            for (CardMonthlyBenefitCategoryTarget target : categoryTargets) {
                UsageTotals usage = usageIndex.category.getOrDefault(
                        targetKey(serviceId, target.getCategoryId()), UsageTotals.ZERO);
                BigDecimal rawUsed = usageCalculator.calculateLimitUsageValue(
                        service.definition, sharedLimit, usage);
                attributedUsage.add(usage);
                attributedRawUsed = attributedRawUsed.add(rawUsed);
                String targetName = categoryDisplayName(target, service.definition.getBenefitName());
                targetResponses.add(createSharedTarget(
                        service.definition, sharedLimit, target.getCategoryId(), targetName,
                        target.getCategoryImageUrl(), target.getCategoryId(), usage, rawUsed));
                breakdown.add(createBreakdown(
                        service, sharedLimit, target.getCategoryId(), targetName,
                        target.getCategoryId(), false, rawUsed));
            }
        } else if (service.definition.getScopeType() == BenefitScopeType.BRAND) {
            if (brandTargets.isEmpty()) {
                throw invalidData();
            }
            for (CardMonthlyBenefitBrandTarget target : brandTargets) {
                UsageTotals usage = usageIndex.brand.getOrDefault(
                        targetKey(serviceId, target.getBrandId()), UsageTotals.ZERO);
                BigDecimal rawUsed = usageCalculator.calculateLimitUsageValue(
                        service.definition, sharedLimit, usage);
                attributedUsage.add(usage);
                attributedRawUsed = attributedRawUsed.add(rawUsed);
                targetResponses.add(createSharedTarget(
                        service.definition, sharedLimit, target.getBrandId(), target.getBrandName(),
                        target.getBrandImageUrl(), target.getCategoryId(), usage, rawUsed));
                breakdown.add(createBreakdown(
                        service, sharedLimit, target.getBrandId(), target.getBrandName(),
                        target.getCategoryId(), false, rawUsed));
            }
        } else {
            throw invalidData();
        }

        validateAttributedUsage(serviceUsage, attributedUsage);
        UsageTotals unattributedUsage = subtract(serviceUsage, attributedUsage);
        BigDecimal serviceRawUsed = usageCalculator.calculateLimitUsageValue(
                service.definition, sharedLimit, serviceUsage);
        BigDecimal unattributedRawUsed = usageCalculator.calculateLimitUsageValue(
                service.definition, sharedLimit, unattributedUsage);
        if (attributedRawUsed.add(unattributedRawUsed).compareTo(serviceRawUsed) != 0) {
            throw invalidData();
        }
        if (unattributedRawUsed.signum() > 0) {
            breakdown.add(createBreakdown(
                    service, sharedLimit, null, null, null, true, unattributedRawUsed));
        }

        List<CardMonthlyBenefitLimitResponse> serviceLimits = service.limits.stream()
                .filter(limit -> !limit.isShared())
                .map(limit -> snapshot.get(limit.getLimitId()).response)
                .sorted(Comparator.comparing(response -> response.getLimitBasis().ordinal()))
                .toList();
        CardMonthlyBenefitLimitResponse sharedLimitResponse = snapshot.get(sharedLimit.getLimitId()).response;
        CardMonthlyBenefitLimitStatus serviceStatus = sharedLimitResponse.getLimitStatus()
                == CardMonthlyBenefitLimitStatus.LIMIT_EXHAUSTED
                || serviceLimits.stream().anyMatch(limit -> limit.getLimitStatus()
                        == CardMonthlyBenefitLimitStatus.LIMIT_EXHAUSTED)
                ? CardMonthlyBenefitLimitStatus.LIMIT_EXHAUSTED
                : CardMonthlyBenefitLimitStatus.AVAILABLE;

        BigDecimal sharedDisplayUsed = displayLimitValue(
                service.definition, sharedLimit, serviceRawUsed);
        BigDecimal unattributedDisplayUsed = displayLimitValue(
                service.definition, sharedLimit, unattributedRawUsed);
        CardMonthlyBenefitUnit sharedUnit = limitUnit(
                service.definition, sharedLimit.getLimitBasis());
        CardMonthlyBenefitServiceResponse response = CardMonthlyBenefitServiceResponse.builder()
                .benefitServiceId(serviceId)
                .benefitName(service.definition.getBenefitName())
                .displayQualifier(displayQualifier(service.definition.getBenefitName()))
                .scopeType(service.definition.getScopeType())
                .benefitType(service.definition.getBenefitType())
                .valueType(service.definition.getValueType())
                .valueNumber(service.definition.getValueNumber())
                .valueUnit(valueUnit(service.definition))
                .pointCurrencyName(service.definition.getPointCurrencyName())
                .valueLabel(valueLabel(service.definition))
                .perTransactionLimitValue(perTransactionLimitValue(service.definition))
                .perTransactionLimitLabel(perTransactionLimitLabel(service.definition))
                .transactionCount(serviceUsage.count)
                .totalPaymentAmount(money(serviceUsage.payment))
                .receivedBenefitValue(receivedDisplayValue(service.definition, serviceUsage.received))
                .receivedBenefitLabel(receivedLabel(service.definition, serviceUsage.received))
                .sharedLimitUsedValue(sharedDisplayUsed)
                .sharedLimitUsedLabel(usedLabel(sharedDisplayUsed, sharedUnit))
                .unattributedSharedLimitUsedValue(unattributedDisplayUsed)
                .unattributedSharedLimitUsedLabel(usedLabel(unattributedDisplayUsed, sharedUnit))
                .serviceMonthlyLimits(List.copyOf(serviceLimits))
                .serviceLimitStatus(serviceStatus)
                .targets(List.copyOf(targetResponses))
                .build();
        return new SharedServiceAssembly(response, List.copyOf(breakdown), serviceRawUsed);
    }

    private CardMonthlyBenefitTargetResponse createSharedTarget(
            CardMonthlyBenefitRule definition,
            CardMonthlyBenefitRule sharedLimit,
            Long targetId,
            String targetName,
            String targetImageUrl,
            Long categoryId,
            UsageTotals usage,
            BigDecimal rawUsed) {
        CardMonthlyBenefitUnit unit = limitUnit(definition, sharedLimit.getLimitBasis());
        BigDecimal displayUsed = displayLimitValue(definition, sharedLimit, rawUsed);
        return CardMonthlyBenefitTargetResponse.builder()
                .scopeType(definition.getScopeType())
                .targetId(targetId)
                .targetName(targetName)
                .targetImageUrl(targetImageUrl)
                .categoryId(categoryId)
                .transactionCount(usage.count)
                .totalPaymentAmount(money(usage.payment))
                .receivedBenefitValue(receivedDisplayValue(definition, usage.received))
                .receivedBenefitLabel(receivedLabel(definition, usage.received))
                .sharedLimitUsedValue(displayUsed)
                .sharedLimitUsedLabel(usedLabel(displayUsed, unit))
                .build();
    }

    private CardMonthlyBenefitSharedLimitUsageResponse createBreakdown(
            SelectedService service,
            CardMonthlyBenefitRule sharedLimit,
            Long targetId,
            String targetName,
            Long categoryId,
            boolean unattributed,
            BigDecimal rawUsed) {
        CardMonthlyBenefitUnit unit = limitUnit(service.definition, sharedLimit.getLimitBasis());
        BigDecimal displayUsed = displayLimitValue(service.definition, sharedLimit, rawUsed);
        return CardMonthlyBenefitSharedLimitUsageResponse.builder()
                .benefitServiceId(service.definition.getServiceId())
                .scopeType(service.definition.getScopeType())
                .targetId(targetId)
                .targetName(targetName)
                .categoryId(categoryId)
                .displayQualifier(displayQualifier(service.definition.getBenefitName()))
                .unattributed(unattributed)
                .usedValue(displayUsed)
                .usedLabel(usedLabel(displayUsed, unit))
                .build();
    }

    private List<CardMonthlyBenefitGroupCategoryResponse> groupCategories(
            List<SelectedService> services,
            Map<Long, List<CardMonthlyBenefitCategoryTarget>> categoriesByService,
            Map<Long, List<CardMonthlyBenefitBrandTarget>> brandsByService) {
        Map<Long, CardMonthlyBenefitGroupCategoryResponse> categories = new LinkedHashMap<>();
        for (SelectedService service : services) {
            Long serviceId = service.definition.getServiceId();
            if (service.definition.getScopeType() == BenefitScopeType.INDUSTRY) {
                for (CardMonthlyBenefitCategoryTarget target
                        : categoriesByService.getOrDefault(serviceId, List.of())) {
                    categories.putIfAbsent(target.getCategoryId(),
                            CardMonthlyBenefitGroupCategoryResponse.builder()
                                    .categoryId(target.getCategoryId())
                                    .categoryName(target.getCategoryName())
                                    .categoryImageUrl(target.getCategoryImageUrl())
                                    .build());
                }
            } else if (service.definition.getScopeType() == BenefitScopeType.BRAND) {
                for (CardMonthlyBenefitBrandTarget target
                        : brandsByService.getOrDefault(serviceId, List.of())) {
                    if (target.getCategoryId() == null || target.getCategoryName() == null) {
                        throw invalidData();
                    }
                    categories.putIfAbsent(target.getCategoryId(),
                            CardMonthlyBenefitGroupCategoryResponse.builder()
                                    .categoryId(target.getCategoryId())
                                    .categoryName(target.getCategoryName())
                                    .categoryImageUrl(target.getCategoryImageUrl())
                                    .build());
                }
            }
        }
        if (categories.isEmpty()) {
            throw invalidData();
        }
        return categories.values().stream()
                .sorted(Comparator.comparing(CardMonthlyBenefitGroupCategoryResponse::getCategoryId))
                .toList();
    }

    private List<CardMonthlyBenefitLimitResponse> createLimitResponses(
            SelectedService service,
            UsageIndex usageIndex,
            LimitUsageSnapshotBuilder snapshotBuilder) {
        List<CardMonthlyBenefitLimitResponse> responses = new ArrayList<>();
        for (CardMonthlyBenefitRule limit : service.limits) {
            LimitUsageResult result = usageCalculator.calculateItemLimitResult(
                    service.definition, limit, usageIndex);
            CardMonthlyBenefitUnit unit = limitUnit(service.definition, limit.getLimitBasis());
            BigDecimal displayLimit = displayLimitValue(service.definition, limit, limit.getLimitValue());
            BigDecimal displayUsed = displayLimitValue(service.definition, limit, result.rawUsed);
            BigDecimal displayRemaining = displayLimitValue(service.definition, limit, result.rawRemaining);
            CardMonthlyBenefitLimitStatus status = result.rawRemaining.signum() == 0
                    ? CardMonthlyBenefitLimitStatus.LIMIT_EXHAUSTED
                    : CardMonthlyBenefitLimitStatus.AVAILABLE;
            CardMonthlyBenefitLimitResponse response = CardMonthlyBenefitLimitResponse.builder()
                    .limitId(limit.getLimitId())
                    .limitBasis(limit.getLimitBasis())
                    .limitValue(displayLimit)
                    .usedValue(displayUsed)
                    .remainingValue(displayRemaining)
                    .limitUnit(unit)
                    .limitLabel(limitLabel(displayUsed, displayLimit, unit))
                    .limitStatus(status)
                    .shared(limit.isShared())
                    .build();
            responses.add(response);
            snapshotBuilder.record(service, limit, result, response);
        }
        responses.sort(Comparator.comparing(response -> response.getLimitBasis().ordinal()));
        return List.copyOf(responses);
    }

    private CardMonthlyBenefitLimitStatus itemStatus(List<CardMonthlyBenefitLimitResponse> limits) {
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
            CardMonthlyBenefitRule definition, LimitBasis basis) {
        if (basis == LimitBasis.COUNT) {
            return CardMonthlyBenefitUnit.COUNT;
        }
        if (definition.getBenefitType() == BenefitType.ACCUMULATE) {
            return CardMonthlyBenefitUnit.POINT;
        }
        if (definition.getBenefitType() == BenefitType.CASHBACK && basis == LimitBasis.AMOUNT) {
            return CardMonthlyBenefitUnit.KRW;
        }
        throw invalidData();
    }

    private CardMonthlyBenefitUnit valueUnit(CardMonthlyBenefitRule definition) {
        if (definition.getValueType() == com.fitwallet.domain.benefit.dto.ValueType.RATE) {
            return CardMonthlyBenefitUnit.PERCENT;
        }
        return definition.getBenefitType() == BenefitType.ACCUMULATE
                ? CardMonthlyBenefitUnit.POINT : CardMonthlyBenefitUnit.KRW;
    }

    private String valueLabel(CardMonthlyBenefitRule definition) {
        return benefitValueLabelFormatter.formatValueWithAction(
                definition.getBenefitName(), definition.getBenefitType(),
                definition.getValueType(), definition.getValueNumber(),
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
            CardMonthlyBenefitRule definition, BigDecimal receivedKrw) {
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

    private String categoryDisplayName(
            CardMonthlyBenefitCategoryTarget target, String benefitName) {
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

    private Long limitGroupId(SelectedService service) {
        Set<Long> groupIds = new LinkedHashSet<>();
        for (CardMonthlyBenefitRule limit : service.limits) {
            if (!limit.isShared()) {
                continue;
            }
            if (service.definition.getServicePlanGroupId() == null
                    || !Objects.equals(service.definition.getServicePlanGroupId(),
                    limit.getLimitPlanGroupId())) {
                throw invalidData();
            }
            groupIds.add(limit.getLimitPlanGroupId());
        }
        if (groupIds.size() > 1) {
            throw invalidData();
        }
        return groupIds.isEmpty() ? null : groupIds.iterator().next();
    }

    private void validateAttributedUsage(UsageTotals serviceUsage, UsageTotals attributedUsage) {
        if (attributedUsage.count > serviceUsage.count
                || attributedUsage.payment.compareTo(serviceUsage.payment) > 0
                || attributedUsage.received.compareTo(serviceUsage.received) > 0) {
            throw invalidData();
        }
    }

    private UsageTotals subtract(UsageTotals total, UsageTotals subtrahend) {
        return new UsageTotals(
                total.count - subtrahend.count,
                total.payment.subtract(subtrahend.payment),
                total.received.subtract(subtrahend.received));
    }

    private String displayQualifier(String benefitName) {
        if (benefitName == null || benefitName.isBlank()) {
            throw invalidData();
        }
        String compact = benefitName.replace(" ", "");
        if (compact.contains("기본혜택")) {
            return null;
        }
        if (compact.contains("추가혜택")) {
            return compact.contains("주말") ? "주말 추가혜택" : "추가혜택";
        }
        if (benefitName.contains("더해드림")) {
            return "더해드림";
        }
        if (benefitName.contains("챙겨드림")) {
            return "챙겨드림";
        }
        if (benefitName.startsWith("특별 ")) {
            return qualifierPrefix(benefitName);
        }
        if (benefitName.startsWith("일반 ")) {
            return qualifierPrefix(benefitName);
        }
        int separatorIndex = benefitName.indexOf(" - ");
        return separatorIndex > 0 ? benefitName.substring(0, separatorIndex).trim() : null;
    }

    private String qualifierPrefix(String benefitName) {
        int dashIndex = benefitName.indexOf(" - ");
        int parenthesisIndex = benefitName.indexOf(" (");
        int endIndex = benefitName.length();
        if (dashIndex > 0) {
            endIndex = Math.min(endIndex, dashIndex);
        }
        if (parenthesisIndex > 0) {
            endIndex = Math.min(endIndex, parenthesisIndex);
        }
        return benefitName.substring(0, endIndex).trim();
    }

    private String limitLabel(BigDecimal used, BigDecimal limit, CardMonthlyBenefitUnit unit) {
        return format(used) + unitSuffix(unit) + " / " + format(limit) + unitSuffix(unit);
    }

    private String usedLabel(BigDecimal used, CardMonthlyBenefitUnit unit) {
        return format(used) + unitSuffix(unit);
    }

    private String unitSuffix(CardMonthlyBenefitUnit unit) {
        return switch (unit) {
            case PERCENT -> "%";
            case KRW -> "원";
            case POINT -> "P";
            case COUNT -> "회";
        };
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

    private BusinessException invalidData() {
        return new BusinessException(CardErrorCode.INVALID_CARD_MONTHLY_BENEFIT_DATA);
    }

    private static final class SharedServiceAssembly {
        private final CardMonthlyBenefitServiceResponse response;
        private final List<CardMonthlyBenefitSharedLimitUsageResponse> breakdown;
        private final BigDecimal rawSharedLimitUsed;

        private SharedServiceAssembly(
                CardMonthlyBenefitServiceResponse response,
                List<CardMonthlyBenefitSharedLimitUsageResponse> breakdown,
                BigDecimal rawSharedLimitUsed) {
            this.response = response;
            this.breakdown = breakdown;
            this.rawSharedLimitUsed = rawSharedLimitUsed;
        }
    }
}
