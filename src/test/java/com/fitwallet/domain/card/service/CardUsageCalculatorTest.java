package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.CardUsageBenefitAllocation;
import com.fitwallet.domain.card.dto.CardUsageRuleSet;
import com.fitwallet.domain.card.dto.CardUsageTierState;
import com.fitwallet.domain.card.dto.CardUsageTierStructure;
import com.fitwallet.domain.card.dto.CardUsageTierType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class CardUsageCalculatorTest {

    @Mock private CardUsageRuleNormalizer normalizer;
    @Mock private CardUsageTierIntegrator integrator;
    @Mock private CardUsageBenefitAllocator allocator;
    @Mock private CardUsageTierStateCalculator stateCalculator;

    @Test
    void tier상태만_필요해도_전체_계산_파이프라인을_순서대로_한번씩_실행한다() {
        CardUsageRuleSet ruleSet = new CardUsageRuleSet(List.of(), List.of());
        CardUsageTierStructure tierStructure =
                new CardUsageTierStructure(CardUsageTierType.NO_REQUIREMENT, List.of());
        CardUsageBenefitAllocation allocation =
                new CardUsageBenefitAllocation(List.of(), List.of());
        CardUsageTierState tierState = new CardUsageTierState(
                null, null, null, null, null, List.of());
        given(normalizer.normalize(7L, List.of())).willReturn(ruleSet);
        given(integrator.integrate(ruleSet)).willReturn(tierStructure);
        given(allocator.allocate(tierStructure, ruleSet.getBenefits())).willReturn(allocation);
        given(stateCalculator.calculate(new BigDecimal("100000"), tierStructure, allocation))
                .willReturn(tierState);
        CardUsageCalculator calculator = new CardUsageCalculator(
                normalizer, integrator, allocator, stateCalculator);

        CardUsageTierState result = calculator.calculateTierState(
                7L, new BigDecimal("100000"), List.of());

        assertThat(result).isSameAs(tierState);
        InOrder order = inOrder(normalizer, integrator, allocator, stateCalculator);
        order.verify(normalizer).normalize(7L, List.of());
        order.verify(integrator).integrate(ruleSet);
        order.verify(allocator).allocate(tierStructure, ruleSet.getBenefits());
        order.verify(stateCalculator).calculate(
                new BigDecimal("100000"), tierStructure, allocation);
        order.verifyNoMoreInteractions();
    }
}
