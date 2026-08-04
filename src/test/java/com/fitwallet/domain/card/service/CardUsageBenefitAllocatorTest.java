package com.fitwallet.domain.card.service;

import com.fitwallet.domain.benefit.dto.BenefitType;
import com.fitwallet.domain.benefit.dto.ValueType;
import com.fitwallet.domain.card.dto.CardUsageBenefitAllocation;
import com.fitwallet.domain.card.dto.CardUsageBenefitDefinition;
import com.fitwallet.domain.card.dto.CardUsageIntegratedTier;
import com.fitwallet.domain.card.dto.CardUsageTierStructure;
import com.fitwallet.domain.card.dto.CardUsageTierType;
import com.fitwallet.domain.card.dto.response.CardUsageBenefitResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CardUsageBenefitAllocatorTest {

    private CardUsageBenefitAllocator allocator;

    @BeforeEach
    void setUp() {
        allocator = new CardUsageBenefitAllocator();
    }

    @Test
    void 실적조건이_없으면_혜택을_defaultBenefits에_배치한다() {
        CardUsageBenefitAllocation allocation = allocator.allocate(
                structure(CardUsageTierType.NO_REQUIREMENT),
                List.of(rateBenefit(2L, "0"), rateBenefit(1L, "0")));

        assertThat(allocation.getTierBenefitGroups()).isEmpty();
        assertThat(allocation.getDefaultBenefits())
                .extracting(CardUsageBenefitResponse::getBenefitId)
                .containsExactly(1L, 2L);
    }

    @Test
    void 실적조건이_있으면_0원혜택도_적용되는_모든구간에_포함한다() {
        CardUsageBenefitAllocation allocation = allocator.allocate(
                structure(CardUsageTierType.SINGLE_TIER, "0", "300000"),
                List.of(rateBenefit(1L, "0"), rateBenefit(2L, "300000")));

        assertThat(allocation.getDefaultBenefits()).isEmpty();
        assertThat(allocation.getTierBenefitGroups().get(0).getBenefits())
                .extracting(CardUsageBenefitResponse::getBenefitId)
                .containsExactly(1L);
        assertThat(allocation.getTierBenefitGroups().get(1).getBenefits())
                .extracting(CardUsageBenefitResponse::getBenefitId)
                .containsExactly(1L, 2L);
    }

    @Test
    void 혜택상한은_미포함으로_판정한다() {
        CardUsageBenefitDefinition limited = rateBenefit(1L, "300000").toBuilder()
                .maximumAmount(new BigDecimal("500000"))
                .build();

        CardUsageBenefitAllocation allocation = allocator.allocate(
                structure(CardUsageTierType.MULTIPLE_TIERS, "0", "300000", "500000"),
                List.of(limited));

        assertThat(allocation.getTierBenefitGroups().get(1).getBenefits()).hasSize(1);
        assertThat(allocation.getTierBenefitGroups().get(2).getBenefits()).isEmpty();
    }

    @Test
    void 구간별_최종혜택은_benefitId로_중복을_재방어한다() {
        CardUsageBenefitDefinition first = rateBenefit(1L, "0");
        CardUsageBenefitDefinition duplicate = rateBenefit(1L, "0");

        CardUsageBenefitAllocation allocation = allocator.allocate(
                structure(CardUsageTierType.SINGLE_TIER, "0", "300000"),
                List.of(first, duplicate));

        assertThat(allocation.getTierBenefitGroups())
                .allSatisfy(group -> assertThat(group.getBenefits()).hasSize(1));
    }

    @Test
    void 혜택값_유형에_맞는_valueLabel을_생성한다() {
        List<CardUsageBenefitDefinition> benefits = List.of(
                benefit(1L, "일반 할인", BenefitType.CASHBACK, ValueType.RATE, "10.00", null),
                benefit(2L, "정액 할인", BenefitType.CASHBACK, ValueType.FIXED, "1000.00", null),
                benefit(3L, "포인트 적립", BenefitType.ACCUMULATE, ValueType.FIXED,
                        "1000.00", "마이신한포인트"),
                benefit(4L, "주유 할인", BenefitType.CASHBACK, ValueType.FIXED, "40.00", null),
                benefit(5L, "주유 적립", BenefitType.ACCUMULATE, ValueType.FIXED,
                        "80.00", "마이신한포인트"));

        CardUsageBenefitAllocation allocation = allocator.allocate(
                structure(CardUsageTierType.NO_REQUIREMENT), benefits);

        assertThat(allocation.getDefaultBenefits())
                .extracting(CardUsageBenefitResponse::getValueLabel)
                .containsExactly(
                        "10%",
                        "1,000원",
                        "1,000 마이신한포인트",
                        "리터당 40원",
                        "리터당 80 마이신한포인트");
    }

    @Test
    void RATE의_불필요한_소수점0을_제거한다() {
        CardUsageBenefitAllocation allocation = allocator.allocate(
                structure(CardUsageTierType.NO_REQUIREMENT),
                List.of(benefit(1L, "할인", BenefitType.CASHBACK,
                        ValueType.RATE, "1.20", null)));

        assertThat(allocation.getDefaultBenefits().get(0).getValueLabel()).isEqualTo("1.2%");
    }

    private CardUsageTierStructure structure(CardUsageTierType tierType, String... minimumAmounts) {
        List<CardUsageIntegratedTier> tiers = java.util.stream.IntStream
                .range(0, minimumAmounts.length)
                .mapToObj(index -> CardUsageIntegratedTier.builder()
                        .tierOrder(index)
                        .tierName(index + "구간")
                        .minimumAmount(new BigDecimal(minimumAmounts[index]))
                        .build())
                .toList();
        return new CardUsageTierStructure(tierType, tiers);
    }

    private CardUsageBenefitDefinition rateBenefit(Long benefitId, String minimumAmount) {
        return benefit(benefitId, "혜택 " + benefitId, BenefitType.CASHBACK,
                ValueType.RATE, "10.00", null).toBuilder()
                .minimumAmount(new BigDecimal(minimumAmount))
                .build();
    }

    private CardUsageBenefitDefinition benefit(
            Long benefitId,
            String benefitName,
            BenefitType benefitType,
            ValueType valueType,
            String valueNumber,
            String pointCurrencyName) {
        return CardUsageBenefitDefinition.builder()
                .benefitId(benefitId)
                .benefitName(benefitName)
                .benefitType(benefitType)
                .valueType(valueType)
                .valueNumber(new BigDecimal(valueNumber))
                .minimumAmount(BigDecimal.ZERO)
                .pointCurrencyName(pointCurrencyName)
                .build();
    }
}
