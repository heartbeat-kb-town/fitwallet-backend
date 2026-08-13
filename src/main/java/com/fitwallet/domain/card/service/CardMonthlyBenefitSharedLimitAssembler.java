package com.fitwallet.domain.card.service;

import com.fitwallet.domain.benefit.dto.BenefitScopeType;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitBrandTarget;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitCategoryTarget;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitLimitStatus;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitRule;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitUnit;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitGroupCategoryResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitLimitResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitServiceResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitSharedLimitGroupResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitSharedLimitUsageResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitTargetResponse;
import com.fitwallet.domain.card.exception.CardErrorCode;
import com.fitwallet.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.fitwallet.domain.card.service.CardMonthlyBenefitCalculationContext.LimitUsageObservation;
import static com.fitwallet.domain.card.service.CardMonthlyBenefitCalculationContext.LimitUsageSnapshot;
import static com.fitwallet.domain.card.service.CardMonthlyBenefitCalculationContext.SelectedService;
import static com.fitwallet.domain.card.service.CardMonthlyBenefitCalculationContext.UsageIndex;
import static com.fitwallet.domain.card.service.CardMonthlyBenefitCalculationContext.UsageTotals;

@Component
@RequiredArgsConstructor
class CardMonthlyBenefitSharedLimitAssembler {

    private final CardMonthlyBenefitUsageCalculator usageCalculator;
    private final CardMonthlyBenefitDisplayFormatter displayFormatter;

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
            Long groupId = service.sharedLimitGroupId();
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
                String targetName = displayFormatter.categoryDisplayName(
                        target, service.definition.getBenefitName());
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

        BigDecimal sharedDisplayUsed = displayFormatter.displayLimitValue(
                service.definition, sharedLimit, serviceRawUsed);
        BigDecimal unattributedDisplayUsed = displayFormatter.displayLimitValue(
                service.definition, sharedLimit, unattributedRawUsed);
        CardMonthlyBenefitUnit sharedUnit = displayFormatter.limitUnit(
                service.definition, sharedLimit.getLimitBasis());
        CardMonthlyBenefitServiceResponse response = CardMonthlyBenefitServiceResponse.builder()
                .benefitServiceId(serviceId)
                .benefitName(service.definition.getBenefitName())
                .displayQualifier(displayFormatter.displayQualifier(service.definition.getBenefitName()))
                .scopeType(service.definition.getScopeType())
                .benefitType(service.definition.getBenefitType())
                .valueType(service.definition.getValueType())
                .valueNumber(service.definition.getValueNumber())
                .valueUnit(displayFormatter.valueUnit(service.definition))
                .pointCurrencyName(service.definition.getPointCurrencyName())
                .valueLabel(displayFormatter.valueLabel(service.definition))
                .perTransactionLimitValue(displayFormatter.perTransactionLimitValue(service.definition))
                .perTransactionLimitLabel(displayFormatter.perTransactionLimitLabel(service.definition))
                .transactionCount(serviceUsage.count)
                .totalPaymentAmount(displayFormatter.money(serviceUsage.payment))
                .receivedBenefitValue(displayFormatter.receivedDisplayValue(
                        service.definition, serviceUsage.received))
                .receivedBenefitLabel(displayFormatter.receivedLabel(
                        service.definition, serviceUsage.received))
                .sharedLimitUsedValue(sharedDisplayUsed)
                .sharedLimitUsedLabel(displayFormatter.usedLabel(sharedDisplayUsed, sharedUnit))
                .unattributedSharedLimitUsedValue(unattributedDisplayUsed)
                .unattributedSharedLimitUsedLabel(
                        displayFormatter.usedLabel(unattributedDisplayUsed, sharedUnit))
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
        CardMonthlyBenefitUnit unit = displayFormatter.limitUnit(
                definition, sharedLimit.getLimitBasis());
        BigDecimal displayUsed = displayFormatter.displayLimitValue(
                definition, sharedLimit, rawUsed);
        return CardMonthlyBenefitTargetResponse.builder()
                .scopeType(definition.getScopeType())
                .targetId(targetId)
                .targetName(targetName)
                .targetImageUrl(targetImageUrl)
                .categoryId(categoryId)
                .transactionCount(usage.count)
                .totalPaymentAmount(displayFormatter.money(usage.payment))
                .receivedBenefitValue(displayFormatter.receivedDisplayValue(
                        definition, usage.received))
                .receivedBenefitLabel(displayFormatter.receivedLabel(definition, usage.received))
                .sharedLimitUsedValue(displayUsed)
                .sharedLimitUsedLabel(displayFormatter.usedLabel(displayUsed, unit))
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
        CardMonthlyBenefitUnit unit = displayFormatter.limitUnit(
                service.definition, sharedLimit.getLimitBasis());
        BigDecimal displayUsed = displayFormatter.displayLimitValue(
                service.definition, sharedLimit, rawUsed);
        return CardMonthlyBenefitSharedLimitUsageResponse.builder()
                .benefitServiceId(service.definition.getServiceId())
                .scopeType(service.definition.getScopeType())
                .targetId(targetId)
                .targetName(targetName)
                .categoryId(categoryId)
                .displayQualifier(displayFormatter.displayQualifier(
                        service.definition.getBenefitName()))
                .unattributed(unattributed)
                .usedValue(displayUsed)
                .usedLabel(displayFormatter.usedLabel(displayUsed, unit))
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
