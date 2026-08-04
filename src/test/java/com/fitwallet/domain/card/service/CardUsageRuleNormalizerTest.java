package com.fitwallet.domain.card.service;

import com.fitwallet.domain.benefit.dto.BenefitType;
import com.fitwallet.domain.benefit.dto.ValueType;
import com.fitwallet.domain.card.dto.CardUsageBenefitRule;
import com.fitwallet.domain.card.dto.CardUsageRuleSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class CardUsageRuleNormalizerTest {

    private CardUsageRuleNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new CardUsageRuleNormalizer();
    }

    @Test
    void 같은_플랜그룹의_반복된_혜택과_구간을_각각_중복제거한다() {
        List<CardUsageBenefitRule> rules = List.of(
                rule(1L, 10L, 101L, 1, "300000.00", "500000.00"),
                rule(1L, 10L, 102L, 2, "500000.00", null),
                rule(2L, 10L, 101L, 1, "300000.0", "500000.0"),
                rule(2L, 10L, 102L, 2, "500000.0", null));

        CardUsageRuleSet normalized = normalizer.normalize(43L, rules);

        assertThat(normalized.getBenefits()).hasSize(2);
        assertThat(normalized.getSourceTiers()).hasSize(2)
                .extracting(tier -> tier.getMinimumAmount().stripTrailingZeros())
                .containsExactly(new BigDecimal("3E+5"), new BigDecimal("5E+5"));
    }

    @Test
    void 같은_혜택_ID의_본체정보가_다르면_실패한다() {
        CardUsageBenefitRule first = rule(1L, null, null, null, null, null);
        CardUsageBenefitRule different = CardUsageBenefitRule.builder()
                .benefitId(1L)
                .benefitName("다른 혜택")
                .benefitType(BenefitType.CASHBACK)
                .valueType(ValueType.RATE)
                .valueNumber(new BigDecimal("10"))
                .benefitMinimumAmount(BigDecimal.ZERO)
                .build();

        assertThatThrownBy(() -> normalizer.normalize(1L, List.of(first, different)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("benefitId=1");
    }

    @Test
    void 같은_구간_ID의_금액_scale만_다르면_같은_구간으로_본다() {
        CardUsageRuleSet normalized = normalizer.normalize(1L, List.of(
                rule(1L, 10L, 101L, 1, "300000.00", null),
                rule(2L, 10L, 101L, 1, "300000", null)));

        assertThat(normalized.getSourceTiers()).hasSize(1);
    }

    @Test
    void 같은_owner의_구간에_빈틈이_있으면_실패한다() {
        assertThatThrownBy(() -> normalizer.normalize(1L, List.of(
                rule(1L, 10L, 101L, 1, "300000", "500000"),
                rule(1L, 10L, 102L, 2, "600000", null))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("빈틈 또는 겹침");
    }

    @Test
    void 같은_owner의_구간이_겹치면_실패한다() {
        assertThatThrownBy(() -> normalizer.normalize(1L, List.of(
                rule(1L, 10L, 101L, 1, "300000", "700000"),
                rule(1L, 10L, 102L, 2, "500000", null))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("빈틈 또는 겹침");
    }

    @Test
    void 원본_구간순서와_최소금액순서가_다르면_실패한다() {
        assertThatThrownBy(() -> normalizer.normalize(1L, List.of(
                rule(1L, 10L, 101L, 1, "500000", null),
                rule(1L, 10L, 102L, 2, "300000", "500000"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("최소 실적 순서");
    }

    @Test
    void 최종_원본구간에_상한이_있으면_실패한다() {
        assertThatThrownBy(() -> normalizer.normalize(1L, List.of(
                rule(1L, 10L, 101L, 1, "300000", "500000"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("최종 원본 구간에 상한");
    }

    @Test
    void 적립혜택에_포인트통화가_없으면_실패한다() {
        CardUsageBenefitRule rule = CardUsageBenefitRule.builder()
                .benefitId(1L)
                .benefitName("포인트 적립")
                .benefitType(BenefitType.ACCUMULATE)
                .valueType(ValueType.RATE)
                .valueNumber(BigDecimal.ONE)
                .benefitMinimumAmount(BigDecimal.ZERO)
                .build();

        assertThatThrownBy(() -> normalizer.normalize(1L, List.of(rule)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("포인트 통화");
    }

    private CardUsageBenefitRule rule(
            Long benefitId,
            Long planGroupId,
            Long tierId,
            Integer tierOrder,
            String tierMinimumAmount,
            String tierMaximumAmount) {
        return CardUsageBenefitRule.builder()
                .benefitId(benefitId)
                .planGroupId(planGroupId)
                .benefitName("혜택 " + benefitId)
                .benefitType(BenefitType.CASHBACK)
                .valueType(ValueType.RATE)
                .valueNumber(new BigDecimal("10.00"))
                .benefitMinimumAmount(BigDecimal.ZERO)
                .sourceTierId(tierId)
                .sourceTierOrder(tierOrder)
                .tierMinimumAmount(decimal(tierMinimumAmount))
                .tierMaximumAmount(decimal(tierMaximumAmount))
                .build();
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
