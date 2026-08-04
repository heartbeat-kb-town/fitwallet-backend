package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.CardUsageBenefitDefinition;
import com.fitwallet.domain.card.dto.CardUsageIntegratedTier;
import com.fitwallet.domain.card.dto.CardUsageRuleSet;
import com.fitwallet.domain.card.dto.CardUsageSourceTier;
import com.fitwallet.domain.card.dto.CardUsageTierStructure;
import com.fitwallet.domain.card.dto.CardUsageTierType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

/** 혜택과 원본 구간의 최소 실적 경계를 카드상품 단위 구간으로 통합한다. */
@Component
public class CardUsageTierIntegrator {

    public CardUsageTierStructure integrate(CardUsageRuleSet ruleSet) {
        if (ruleSet == null || ruleSet.getBenefits() == null || ruleSet.getSourceTiers() == null) {
            throw new IllegalStateException("정규화된 카드 이용 실적 규칙이 없습니다.");
        }

        NavigableSet<BigDecimal> positiveBoundaries = new TreeSet<>();
        for (CardUsageBenefitDefinition benefit : ruleSet.getBenefits()) {
            addPositiveBoundary(positiveBoundaries, benefit.getMinimumAmount());
        }
        for (CardUsageSourceTier tier : ruleSet.getSourceTiers()) {
            addPositiveBoundary(positiveBoundaries, tier.getMinimumAmount());
        }

        CardUsageTierType tierType = resolveTierType(positiveBoundaries.size());
        if (tierType == CardUsageTierType.NO_REQUIREMENT) {
            return new CardUsageTierStructure(tierType, List.of());
        }

        List<CardUsageIntegratedTier> tiers = new ArrayList<>(positiveBoundaries.size() + 1);
        tiers.add(createTier(0, BigDecimal.ZERO));

        int tierOrder = 1;
        for (BigDecimal minimumAmount : positiveBoundaries) {
            tiers.add(createTier(tierOrder, minimumAmount));
            tierOrder++;
        }
        return new CardUsageTierStructure(tierType, List.copyOf(tiers));
    }

    private void addPositiveBoundary(NavigableSet<BigDecimal> boundaries, BigDecimal amount) {
        if (amount == null) {
            throw new IllegalStateException("통합할 최소 실적 금액이 없습니다.");
        }
        if (amount.signum() < 0) {
            throw new IllegalStateException("통합할 최소 실적 금액이 음수입니다. amount=" + amount);
        }
        if (amount.signum() > 0) {
            boundaries.add(amount);
        }
    }

    private CardUsageTierType resolveTierType(int positiveBoundaryCount) {
        if (positiveBoundaryCount == 0) {
            return CardUsageTierType.NO_REQUIREMENT;
        }
        if (positiveBoundaryCount == 1) {
            return CardUsageTierType.SINGLE_TIER;
        }
        return CardUsageTierType.MULTIPLE_TIERS;
    }

    private CardUsageIntegratedTier createTier(int tierOrder, BigDecimal minimumAmount) {
        return CardUsageIntegratedTier.builder()
                .tierOrder(tierOrder)
                .tierName(tierOrder + "구간")
                .minimumAmount(minimumAmount)
                .build();
    }
}
