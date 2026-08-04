package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.CardUsageBenefitDefinition;
import com.fitwallet.domain.card.dto.CardUsageIntegratedTier;
import com.fitwallet.domain.card.dto.CardUsageRuleSet;
import com.fitwallet.domain.card.dto.CardUsageSourceTier;
import com.fitwallet.domain.card.dto.CardUsageTierStructure;
import com.fitwallet.domain.card.dto.CardUsageTierType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CardUsageTierIntegratorTest {

    private CardUsageTierIntegrator integrator;

    @BeforeEach
    void setUp() {
        integrator = new CardUsageTierIntegrator();
    }

    @Test
    void 양수경계가_없으면_실적조건없음이고_구간도_없다() {
        CardUsageTierStructure structure = integrator.integrate(
                ruleSet(List.of(benefit(1L, "0.00")), List.of(sourceTier(1L, "0.0"))));

        assertThat(structure.getTierType()).isEqualTo(CardUsageTierType.NO_REQUIREMENT);
        assertThat(structure.getTiers()).isEmpty();
    }

    @Test
    void 양수경계가_하나면_단일구간이고_합성0구간을_앞에_둔다() {
        CardUsageTierStructure structure = integrator.integrate(
                ruleSet(List.of(benefit(1L, "300000.00")), List.of()));

        assertThat(structure.getTierType()).isEqualTo(CardUsageTierType.SINGLE_TIER);
        assertThat(structure.getTiers()).extracting(CardUsageIntegratedTier::getTierName)
                .containsExactly("0구간", "1구간");
        assertThat(structure.getTiers()).extracting(CardUsageIntegratedTier::getMinimumAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(BigDecimal.ZERO, new BigDecimal("300000"));
    }

    @Test
    void 혜택과_원본구간의_양수경계를_금액순으로_통합한다() {
        CardUsageTierStructure structure = integrator.integrate(ruleSet(
                List.of(benefit(1L, "500000"), benefit(2L, "300000.00")),
                List.of(sourceTier(1L, "200000"), sourceTier(2L, "1000000"))));

        assertThat(structure.getTierType()).isEqualTo(CardUsageTierType.MULTIPLE_TIERS);
        assertThat(structure.getTiers()).extracting(CardUsageIntegratedTier::getTierOrder)
                .containsExactly(0, 1, 2, 3, 4);
        assertThat(structure.getTiers()).extracting(CardUsageIntegratedTier::getMinimumAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(
                        BigDecimal.ZERO,
                        new BigDecimal("200000"),
                        new BigDecimal("300000"),
                        new BigDecimal("500000"),
                        new BigDecimal("1000000"));
    }

    @Test
    void scale이_다른_같은금액은_하나의_통합구간으로_만든다() {
        CardUsageTierStructure structure = integrator.integrate(ruleSet(
                List.of(benefit(1L, "300000.00")),
                List.of(sourceTier(1L, "300000"))));

        assertThat(structure.getTierType()).isEqualTo(CardUsageTierType.SINGLE_TIER);
        assertThat(structure.getTiers()).hasSize(2);
    }

    @Test
    void 혜택목록이_같아도_최소금액이_다르면_구간을_유지한다() {
        CardUsageTierStructure structure = integrator.integrate(ruleSet(
                List.of(benefit(1L, "300000")),
                List.of(sourceTier(1L, "300000"), sourceTier(2L, "500000"))));

        assertThat(structure.getTiers()).extracting(CardUsageIntegratedTier::getTierName)
                .containsExactly("0구간", "1구간", "2구간");
    }

    private CardUsageRuleSet ruleSet(
            List<CardUsageBenefitDefinition> benefits,
            List<CardUsageSourceTier> sourceTiers) {
        return new CardUsageRuleSet(benefits, sourceTiers);
    }

    private CardUsageBenefitDefinition benefit(Long benefitId, String minimumAmount) {
        return CardUsageBenefitDefinition.builder()
                .benefitId(benefitId)
                .minimumAmount(new BigDecimal(minimumAmount))
                .build();
    }

    private CardUsageSourceTier sourceTier(Long tierId, String minimumAmount) {
        return CardUsageSourceTier.builder()
                .sourceTierId(tierId)
                .minimumAmount(new BigDecimal(minimumAmount))
                .build();
    }
}
