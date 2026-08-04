package com.fitwallet.domain.card.service;

import com.fitwallet.domain.benefit.dto.BenefitType;
import com.fitwallet.domain.card.dto.CardUsageBenefitDefinition;
import com.fitwallet.domain.card.dto.CardUsageBenefitRule;
import com.fitwallet.domain.card.dto.CardUsageRuleSet;
import com.fitwallet.domain.card.dto.CardUsageSourceTier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Mapper가 반환한 혜택·구간 평면 행의 중복을 제거하고 ERD 불변식을 검증한다. */
@Component
public class CardUsageRuleNormalizer {

    public CardUsageRuleSet normalize(Long cardProductId, List<CardUsageBenefitRule> rawRules) {
        if (rawRules == null) {
            throw invalid(cardProductId, "혜택 조회 결과가 null입니다.");
        }

        Map<Long, CardUsageBenefitDefinition> benefits = new LinkedHashMap<>();
        Map<Long, CardUsageSourceTier> sourceTiers = new LinkedHashMap<>();

        for (CardUsageBenefitRule rule : rawRules) {
            validateBenefit(cardProductId, rule);
            mergeBenefit(cardProductId, benefits, rule);
            mergeSourceTier(cardProductId, sourceTiers, rule);
        }

        List<CardUsageSourceTier> normalizedTiers = new ArrayList<>(sourceTiers.values());
        validateTierOwners(cardProductId, normalizedTiers);
        normalizedTiers.sort(Comparator.comparing(CardUsageSourceTier::getMinimumAmount));

        return new CardUsageRuleSet(
                List.copyOf(benefits.values()),
                List.copyOf(normalizedTiers));
    }

    private void validateBenefit(Long cardProductId, CardUsageBenefitRule rule) {
        if (rule == null || rule.getBenefitId() == null) {
            throw invalid(cardProductId, "혜택 ID가 없습니다.");
        }
        if (rule.getBenefitName() == null || rule.getBenefitName().isBlank()
                || rule.getBenefitType() == null || rule.getValueType() == null
                || rule.getValueNumber() == null || rule.getBenefitMinimumAmount() == null) {
            throw invalid(cardProductId, "필수 혜택 정보가 없습니다. benefitId=" + rule.getBenefitId());
        }
        if (rule.getValueNumber().signum() < 0 || rule.getBenefitMinimumAmount().signum() < 0) {
            throw invalid(cardProductId, "혜택 값 또는 최소 실적이 음수입니다. benefitId=" + rule.getBenefitId());
        }
        if (rule.getBenefitMaximumAmount() != null
                && rule.getBenefitMaximumAmount().compareTo(rule.getBenefitMinimumAmount()) <= 0) {
            throw invalid(cardProductId, "혜택 실적 상한이 하한보다 크지 않습니다. benefitId=" + rule.getBenefitId());
        }
        if (rule.getBenefitType() == BenefitType.ACCUMULATE
                && (rule.getPointCurrencyName() == null || rule.getPointCurrencyName().isBlank())) {
            throw invalid(cardProductId, "적립 혜택의 포인트 통화가 없습니다. benefitId=" + rule.getBenefitId());
        }
        if (rule.getBenefitType() == BenefitType.CASHBACK && rule.getPointCurrencyName() != null) {
            throw invalid(cardProductId, "캐시백 혜택에 포인트 통화가 연결되어 있습니다. benefitId=" + rule.getBenefitId());
        }
    }

    private void mergeBenefit(
            Long cardProductId,
            Map<Long, CardUsageBenefitDefinition> benefits,
            CardUsageBenefitRule rule) {
        CardUsageBenefitDefinition candidate = toBenefit(rule);
        CardUsageBenefitDefinition existing = benefits.putIfAbsent(rule.getBenefitId(), candidate);
        if (existing != null && !sameBenefit(existing, candidate)) {
            throw invalid(cardProductId, "같은 혜택 ID의 원본 정보가 다릅니다. benefitId=" + rule.getBenefitId());
        }
    }

    private void mergeSourceTier(
            Long cardProductId,
            Map<Long, CardUsageSourceTier> sourceTiers,
            CardUsageBenefitRule rule) {
        if (rule.getSourceTierId() == null) {
            if (rule.getSourceTierOrder() != null || rule.getTierMinimumAmount() != null
                    || rule.getTierMaximumAmount() != null) {
                throw invalid(cardProductId, "구간 ID 없이 구간 정보가 존재합니다. benefitId=" + rule.getBenefitId());
            }
            return;
        }
        if (rule.getSourceTierOrder() == null || rule.getTierMinimumAmount() == null) {
            throw invalid(cardProductId, "필수 구간 정보가 없습니다. tierId=" + rule.getSourceTierId());
        }
        if (rule.getSourceTierOrder() <= 0 || rule.getTierMinimumAmount().signum() < 0) {
            throw invalid(cardProductId, "구간 순서가 양수가 아니거나 최소 실적이 음수입니다. tierId="
                    + rule.getSourceTierId());
        }
        if (rule.getTierMaximumAmount() != null
                && rule.getTierMaximumAmount().compareTo(rule.getTierMinimumAmount()) <= 0) {
            throw invalid(cardProductId, "구간 상한이 하한보다 크지 않습니다. tierId=" + rule.getSourceTierId());
        }

        CardUsageSourceTier candidate = toSourceTier(rule);
        CardUsageSourceTier existing = sourceTiers.putIfAbsent(rule.getSourceTierId(), candidate);
        if (existing != null && !sameSourceTier(existing, candidate)) {
            throw invalid(cardProductId, "같은 구간 ID의 원본 정보가 다릅니다. tierId=" + rule.getSourceTierId());
        }
    }

    private void validateTierOwners(Long cardProductId, List<CardUsageSourceTier> sourceTiers) {
        Map<String, List<CardUsageSourceTier>> tiersByOwner = new HashMap<>();
        for (CardUsageSourceTier tier : sourceTiers) {
            tiersByOwner.computeIfAbsent(ownerKey(tier), ignored -> new ArrayList<>()).add(tier);
        }

        for (Map.Entry<String, List<CardUsageSourceTier>> entry : tiersByOwner.entrySet()) {
            List<CardUsageSourceTier> tiers = entry.getValue();
            tiers.sort(Comparator.comparing(CardUsageSourceTier::getSourceTierOrder));
            validateTierSequence(cardProductId, entry.getKey(), tiers);
        }
    }

    private void validateTierSequence(
            Long cardProductId,
            String owner,
            List<CardUsageSourceTier> tiers) {
        Set<Integer> orders = new HashSet<>();
        for (int index = 0; index < tiers.size(); index++) {
            CardUsageSourceTier current = tiers.get(index);
            if (!orders.add(current.getSourceTierOrder())) {
                throw invalid(cardProductId, "원본 구간 순서가 중복됩니다. owner=" + owner);
            }
            if (index == 0) {
                continue;
            }

            CardUsageSourceTier previous = tiers.get(index - 1);
            if (previous.getMinimumAmount().compareTo(current.getMinimumAmount()) >= 0) {
                throw invalid(cardProductId, "원본 구간 순서와 최소 실적 순서가 다릅니다. owner=" + owner);
            }
            if (previous.getMaximumAmount() == null
                    || previous.getMaximumAmount().compareTo(current.getMinimumAmount()) != 0) {
                throw invalid(cardProductId, "원본 구간 사이에 빈틈 또는 겹침이 있습니다. owner=" + owner);
            }
        }
        if (!tiers.isEmpty() && tiers.get(tiers.size() - 1).getMaximumAmount() != null) {
            throw invalid(cardProductId, "최종 원본 구간에 상한이 존재합니다. owner=" + owner);
        }
    }

    private CardUsageBenefitDefinition toBenefit(CardUsageBenefitRule rule) {
        return CardUsageBenefitDefinition.builder()
                .benefitId(rule.getBenefitId())
                .planGroupId(rule.getPlanGroupId())
                .benefitName(rule.getBenefitName())
                .benefitType(rule.getBenefitType())
                .valueType(rule.getValueType())
                .valueNumber(rule.getValueNumber())
                .minimumAmount(rule.getBenefitMinimumAmount())
                .maximumAmount(rule.getBenefitMaximumAmount())
                .pointCurrencyName(rule.getPointCurrencyName())
                .build();
    }

    private CardUsageSourceTier toSourceTier(CardUsageBenefitRule rule) {
        return CardUsageSourceTier.builder()
                .sourceTierId(rule.getSourceTierId())
                .planGroupId(rule.getPlanGroupId())
                .benefitId(rule.getPlanGroupId() == null ? rule.getBenefitId() : null)
                .sourceTierOrder(rule.getSourceTierOrder())
                .minimumAmount(rule.getTierMinimumAmount())
                .maximumAmount(rule.getTierMaximumAmount())
                .build();
    }

    private boolean sameBenefit(CardUsageBenefitDefinition left, CardUsageBenefitDefinition right) {
        return Objects.equals(left.getBenefitId(), right.getBenefitId())
                && Objects.equals(left.getPlanGroupId(), right.getPlanGroupId())
                && Objects.equals(left.getBenefitName(), right.getBenefitName())
                && left.getBenefitType() == right.getBenefitType()
                && left.getValueType() == right.getValueType()
                && sameAmount(left.getValueNumber(), right.getValueNumber())
                && sameAmount(left.getMinimumAmount(), right.getMinimumAmount())
                && sameAmount(left.getMaximumAmount(), right.getMaximumAmount())
                && Objects.equals(left.getPointCurrencyName(), right.getPointCurrencyName());
    }

    private boolean sameSourceTier(CardUsageSourceTier left, CardUsageSourceTier right) {
        return Objects.equals(left.getSourceTierId(), right.getSourceTierId())
                && Objects.equals(left.getPlanGroupId(), right.getPlanGroupId())
                && Objects.equals(left.getBenefitId(), right.getBenefitId())
                && Objects.equals(left.getSourceTierOrder(), right.getSourceTierOrder())
                && sameAmount(left.getMinimumAmount(), right.getMinimumAmount())
                && sameAmount(left.getMaximumAmount(), right.getMaximumAmount());
    }

    private boolean sameAmount(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    private String ownerKey(CardUsageSourceTier tier) {
        if (tier.getPlanGroupId() != null) {
            return "planGroupId=" + tier.getPlanGroupId();
        }
        return "benefitId=" + tier.getBenefitId();
    }

    private IllegalStateException invalid(Long cardProductId, String reason) {
        return new IllegalStateException(
                "카드 이용 실적 원본 데이터가 올바르지 않습니다. cardProductId="
                        + cardProductId + ", " + reason);
    }
}
