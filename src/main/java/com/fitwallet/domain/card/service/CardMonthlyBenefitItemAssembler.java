package com.fitwallet.domain.card.service;

import com.fitwallet.domain.benefit.dto.BenefitScopeType;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitBrandTarget;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitCategoryTarget;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitLimitStatus;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitRule;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitUnit;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitLimitResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyBrandBenefitResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyCategoryBenefitResponse;
import com.fitwallet.domain.card.exception.CardErrorCode;
import com.fitwallet.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.fitwallet.domain.card.service.CardMonthlyBenefitCalculationContext.LimitUsageResult;
import static com.fitwallet.domain.card.service.CardMonthlyBenefitCalculationContext.LimitUsageSnapshotBuilder;
import static com.fitwallet.domain.card.service.CardMonthlyBenefitCalculationContext.SelectedService;
import static com.fitwallet.domain.card.service.CardMonthlyBenefitCalculationContext.UsageIndex;
import static com.fitwallet.domain.card.service.CardMonthlyBenefitCalculationContext.UsageTotals;

@Component
@RequiredArgsConstructor
public class CardMonthlyBenefitItemAssembler {

    private final CardMonthlyBenefitUsageCalculator usageCalculator;
    private final CardMonthlyBenefitDisplayFormatter displayFormatter;

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
                        targetKey(service.definition.getServiceId(), target.getCategoryId()),
                        UsageTotals.ZERO);
                responses.add(CardMonthlyCategoryBenefitResponse.builder()
                        .limitGroupId(service.sharedLimitGroupId())
                        .benefitServiceId(service.definition.getServiceId())
                        .categoryId(target.getCategoryId())
                        .categoryName(target.getCategoryName())
                        .categoryImageUrl(target.getCategoryImageUrl())
                        .displayName(displayFormatter.categoryDisplayName(
                                target, service.definition.getBenefitName()))
                        .benefitType(service.definition.getBenefitType())
                        .valueType(service.definition.getValueType())
                        .valueNumber(service.definition.getValueNumber())
                        .valueUnit(displayFormatter.valueUnit(service.definition))
                        .pointCurrencyName(service.definition.getPointCurrencyName())
                        .valueLabel(displayFormatter.valueLabel(service.definition))
                        .perTransactionLimitValue(
                                displayFormatter.perTransactionLimitValue(service.definition))
                        .perTransactionLimitLabel(
                                displayFormatter.perTransactionLimitLabel(service.definition))
                        .transactionCount(usage.count)
                        .totalPaymentAmount(displayFormatter.money(usage.payment))
                        .receivedBenefitValue(displayFormatter.receivedDisplayValue(
                                service.definition, usage.received))
                        .receivedBenefitLabel(displayFormatter.receivedLabel(
                                service.definition, usage.received))
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
                        targetKey(service.definition.getServiceId(), target.getBrandId()),
                        UsageTotals.ZERO);
                responses.add(CardMonthlyBrandBenefitResponse.builder()
                        .limitGroupId(service.sharedLimitGroupId())
                        .benefitServiceId(service.definition.getServiceId())
                        .brandId(target.getBrandId())
                        .brandName(target.getBrandName())
                        .brandImageUrl(target.getBrandImageUrl())
                        .displayName(target.getBrandName())
                        .benefitType(service.definition.getBenefitType())
                        .valueType(service.definition.getValueType())
                        .valueNumber(service.definition.getValueNumber())
                        .valueUnit(displayFormatter.valueUnit(service.definition))
                        .pointCurrencyName(service.definition.getPointCurrencyName())
                        .valueLabel(displayFormatter.valueLabel(service.definition))
                        .perTransactionLimitValue(
                                displayFormatter.perTransactionLimitValue(service.definition))
                        .perTransactionLimitLabel(
                                displayFormatter.perTransactionLimitLabel(service.definition))
                        .transactionCount(usage.count)
                        .totalPaymentAmount(displayFormatter.money(usage.payment))
                        .receivedBenefitValue(displayFormatter.receivedDisplayValue(
                                service.definition, usage.received))
                        .receivedBenefitLabel(displayFormatter.receivedLabel(
                                service.definition, usage.received))
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
            UsageIndex usageIndex,
            LimitUsageSnapshotBuilder snapshotBuilder) {
        List<CardMonthlyBenefitLimitResponse> responses = new ArrayList<>();
        for (CardMonthlyBenefitRule limit : service.limits) {
            LimitUsageResult result = usageCalculator.calculateItemLimitResult(
                    service.definition, limit, usageIndex);
            CardMonthlyBenefitUnit unit = displayFormatter.limitUnit(
                    service.definition, limit.getLimitBasis());
            BigDecimal displayLimit = displayFormatter.displayLimitValue(
                    service.definition, limit, limit.getLimitValue());
            BigDecimal displayUsed = displayFormatter.displayLimitValue(
                    service.definition, limit, result.rawUsed);
            BigDecimal displayRemaining = displayFormatter.displayLimitValue(
                    service.definition, limit, result.rawRemaining);
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
                    .limitLabel(displayFormatter.limitLabel(displayUsed, displayLimit, unit))
                    .limitStatus(status)
                    .shared(limit.isShared())
                    .build();
            responses.add(response);
            snapshotBuilder.record(service, limit, result, response);
        }
        responses.sort(Comparator.comparing(response -> response.getLimitBasis().ordinal()));
        return List.copyOf(responses);
    }

    private CardMonthlyBenefitLimitStatus itemStatus(
            List<CardMonthlyBenefitLimitResponse> limits) {
        return limits.stream().anyMatch(limit ->
                limit.getLimitStatus() == CardMonthlyBenefitLimitStatus.LIMIT_EXHAUSTED)
                ? CardMonthlyBenefitLimitStatus.LIMIT_EXHAUSTED
                : CardMonthlyBenefitLimitStatus.AVAILABLE;
    }

    private String targetKey(Long serviceId, Long targetId) {
        return serviceId + ":" + targetId;
    }

    private BusinessException invalidData() {
        return new BusinessException(CardErrorCode.INVALID_CARD_MONTHLY_BENEFIT_DATA);
    }
}
