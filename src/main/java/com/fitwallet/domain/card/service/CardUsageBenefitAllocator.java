package com.fitwallet.domain.card.service;

import com.fitwallet.domain.benefit.dto.BenefitType;
import com.fitwallet.domain.benefit.dto.ValueType;
import com.fitwallet.domain.card.dto.CardUsageBenefitAllocation;
import com.fitwallet.domain.card.dto.CardUsageBenefitDefinition;
import com.fitwallet.domain.card.dto.CardUsageIntegratedTier;
import com.fitwallet.domain.card.dto.CardUsageTierBenefitGroup;
import com.fitwallet.domain.card.dto.CardUsageTierStructure;
import com.fitwallet.domain.card.dto.CardUsageTierType;
import com.fitwallet.domain.card.dto.response.CardUsageBenefitResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 통합 실적 구간별 적용 혜택을 판정하고 화면 표시용 혜택 값을 생성한다. */
@Component
public class CardUsageBenefitAllocator {

    public CardUsageBenefitAllocation allocate(
            CardUsageTierStructure tierStructure,
            List<CardUsageBenefitDefinition> benefits) {
        if (tierStructure == null || tierStructure.getTierType() == null
                || tierStructure.getTiers() == null || benefits == null) {
            throw new IllegalStateException("혜택을 배치할 카드 이용 실적 규칙이 없습니다.");
        }

        List<CardUsageBenefitDefinition> sortedBenefits = new ArrayList<>(benefits);
        sortedBenefits.forEach(this::validateBenefit);
        sortedBenefits.sort(Comparator.comparing(CardUsageBenefitDefinition::getBenefitId));

        if (tierStructure.getTierType() == CardUsageTierType.NO_REQUIREMENT) {
            return new CardUsageBenefitAllocation(
                    createApplicableBenefits(BigDecimal.ZERO, sortedBenefits),
                    List.of());
        }

        List<CardUsageTierBenefitGroup> groups = new ArrayList<>();
        for (CardUsageIntegratedTier tier : tierStructure.getTiers()) {
            groups.add(new CardUsageTierBenefitGroup(
                    tier,
                    createApplicableBenefits(tier.getMinimumAmount(), sortedBenefits)));
        }
        return new CardUsageBenefitAllocation(List.of(), List.copyOf(groups));
    }

    private List<CardUsageBenefitResponse> createApplicableBenefits(
            BigDecimal tierMinimumAmount,
            List<CardUsageBenefitDefinition> benefits) {
        Map<Long, CardUsageBenefitResponse> applicableBenefits = new LinkedHashMap<>();
        for (CardUsageBenefitDefinition benefit : benefits) {
            if (isApplicable(benefit, tierMinimumAmount)) {
                applicableBenefits.putIfAbsent(benefit.getBenefitId(), toResponse(benefit));
            }
        }
        return List.copyOf(applicableBenefits.values());
    }

    private boolean isApplicable(CardUsageBenefitDefinition benefit, BigDecimal tierMinimumAmount) {
        return tierMinimumAmount.compareTo(benefit.getMinimumAmount()) >= 0
                && (benefit.getMaximumAmount() == null
                    || tierMinimumAmount.compareTo(benefit.getMaximumAmount()) < 0);
    }

    private CardUsageBenefitResponse toResponse(CardUsageBenefitDefinition benefit) {
        return CardUsageBenefitResponse.builder()
                .benefitId(benefit.getBenefitId())
                .benefitName(benefit.getBenefitName())
                .benefitType(benefit.getBenefitType())
                .valueType(benefit.getValueType())
                .valueNumber(benefit.getValueNumber())
                .valueLabel(createValueLabel(benefit))
                .build();
    }

    private String createValueLabel(CardUsageBenefitDefinition benefit) {
        String label;
        if (benefit.getValueType() == ValueType.RATE) {
            label = formatPlain(benefit.getValueNumber()) + "%";
        } else if (benefit.getBenefitType() == BenefitType.CASHBACK) {
            label = formatThousands(benefit.getValueNumber()) + "원";
        } else {
            label = formatThousands(benefit.getValueNumber())
                    + " " + benefit.getPointCurrencyName();
        }

        if (benefit.getValueType() == ValueType.FIXED
                && benefit.getBenefitName().contains("주유")) {
            return "리터당 " + label;
        }
        return label;
    }

    private String formatThousands(BigDecimal value) {
        DecimalFormat format = new DecimalFormat(
                "#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(value);
    }

    private String formatPlain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private void validateBenefit(CardUsageBenefitDefinition benefit) {
        if (benefit == null || benefit.getBenefitId() == null || benefit.getBenefitName() == null
                || benefit.getBenefitType() == null || benefit.getValueType() == null
                || benefit.getValueNumber() == null || benefit.getMinimumAmount() == null) {
            throw new IllegalStateException("구간에 배치할 필수 혜택 정보가 없습니다.");
        }
        if (benefit.getBenefitType() == BenefitType.ACCUMULATE
                && (benefit.getPointCurrencyName() == null
                    || benefit.getPointCurrencyName().isBlank())) {
            throw new IllegalStateException(
                    "적립 혜택의 포인트 통화가 없습니다. benefitId=" + benefit.getBenefitId());
        }
    }
}
