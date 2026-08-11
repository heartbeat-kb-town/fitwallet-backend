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
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.fitwallet.domain.card.service.CardMonthlyBenefitCalculationContext.LimitUsageResult;
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

    List<CardMonthlyBrandBenefitResponse> createBrandBenefits(
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
            SelectedService service, UsageIndex usageIndex) {
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

    private String limitLabel(BigDecimal used, BigDecimal limit, CardMonthlyBenefitUnit unit) {
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
}
