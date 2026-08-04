package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.CardUsageBenefitAllocation;
import com.fitwallet.domain.card.dto.CardUsageIntegratedTier;
import com.fitwallet.domain.card.dto.CardUsagePerformanceStatus;
import com.fitwallet.domain.card.dto.CardUsageTierBenefitGroup;
import com.fitwallet.domain.card.dto.CardUsageTierState;
import com.fitwallet.domain.card.dto.CardUsageTierStructure;
import com.fitwallet.domain.card.dto.CardUsageTierType;
import com.fitwallet.domain.card.dto.response.CardUsageTierResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CardUsageTierStateCalculatorTest {

    private CardUsageTierStateCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new CardUsageTierStateCalculator();
    }

    @Test
    void 실적조건이_없으면_구간상태값은_null이고_구간목록은_비어있다() {
        CardUsageTierState state = calculator.calculate(
                BigDecimal.ZERO,
                new CardUsageTierStructure(CardUsageTierType.NO_REQUIREMENT, List.of()),
                new CardUsageBenefitAllocation(List.of(), List.of()));

        assertThat(state.getPerformanceStatus()).isEqualTo(CardUsagePerformanceStatus.NO_REQUIREMENT);
        assertThat(state.getCurrentTier()).isNull();
        assertThat(state.getNextTier()).isNull();
        assertThat(state.getAmountUntilNextTier()).isNull();
        assertThat(state.getTierProgressRate()).isNull();
        assertThat(state.getTiers()).isEmpty();
    }

    @Test
    void 첫_양수구간_미만이면_0구간이_현재이고_실적미달이다() {
        CardUsageTierState state = calculate("130000", "0", "300000", "500000");

        assertThat(state.getPerformanceStatus()).isEqualTo(CardUsagePerformanceStatus.INSUFFICIENT);
        assertThat(state.getCurrentTier().getTierOrder()).isZero();
        assertThat(state.getNextTier().getTierOrder()).isEqualTo(1);
        assertThat(state.getAmountUntilNextTier()).isEqualByComparingTo("170000");
        assertThat(state.getTierProgressRate()).isEqualByComparingTo("43.3");
        assertThat(state.getTiers().get(0).getCurrent()).isTrue();
        assertThat(state.getTiers().get(0).getAchieved()).isFalse();
    }

    @Test
    void 경계금액과_정확히_같으면_다음구간으로_진입한다() {
        CardUsageTierState state = calculate("300000.00", "0", "300000", "500000");

        assertThat(state.getPerformanceStatus()).isEqualTo(CardUsagePerformanceStatus.ACHIEVED);
        assertThat(state.getCurrentTier().getTierOrder()).isEqualTo(1);
        assertThat(state.getNextTier().getTierOrder()).isEqualTo(2);
        assertThat(state.getAmountUntilNextTier()).isEqualByComparingTo("200000");
        assertThat(state.getTierProgressRate()).isEqualByComparingTo("0.0");
    }

    @Test
    void 현재구간과_다음구간_사이의_상대진행률을_계산한다() {
        CardUsageTierState state = calculate("400000", "0", "300000", "500000");

        assertThat(state.getTierProgressRate()).isEqualByComparingTo("50.0");
        assertThat(state.getAmountUntilNextTier()).isEqualByComparingTo("100000");
    }

    @Test
    void 진행률은_소수첫째자리에서_HALF_UP한다() {
        CardUsageTierState state = calculate("1", "0", "6");

        assertThat(state.getTierProgressRate()).isEqualByComparingTo("16.7");
    }

    @Test
    void 최고구간이면_다음구간과_남은금액은_null이고_진행률은_100이다() {
        CardUsageTierState state = calculate("1200000", "0", "300000", "500000", "1000000");

        assertThat(state.getCurrentTier().getTierOrder()).isEqualTo(3);
        assertThat(state.getCurrentTier().getMaximumAmount()).isNull();
        assertThat(state.getNextTier()).isNull();
        assertThat(state.getAmountUntilNextTier()).isNull();
        assertThat(state.getTierProgressRate()).isEqualByComparingTo("100.0");
    }

    @Test
    void 양수구간은_인정금액이_최소금액_이상이면_달성이다() {
        CardUsageTierState state = calculate("600000", "0", "300000", "500000", "1000000");

        assertThat(state.getTiers()).extracting(CardUsageTierResponse::getAchieved)
                .containsExactly(false, true, true, false);
        assertThat(state.getTiers()).extracting(CardUsageTierResponse::getCurrent)
                .containsExactly(false, false, true, false);
    }

    @Test
    void 각구간의_상한은_다음구간의_최소금액이고_최고구간은_null이다() {
        CardUsageTierState state = calculate("0", "0", "300000", "500000");

        assertThat(state.getTiers()).extracting(CardUsageTierResponse::getMaximumAmount)
                .containsExactly(new BigDecimal("300000"), new BigDecimal("500000"), null);
    }

    private CardUsageTierState calculate(String recognizedAmount, String... minimumAmounts) {
        List<CardUsageIntegratedTier> tiers = new ArrayList<>();
        List<CardUsageTierBenefitGroup> groups = new ArrayList<>();
        for (int index = 0; index < minimumAmounts.length; index++) {
            CardUsageIntegratedTier tier = CardUsageIntegratedTier.builder()
                    .tierOrder(index)
                    .tierName(index + "구간")
                    .minimumAmount(new BigDecimal(minimumAmounts[index]))
                    .build();
            tiers.add(tier);
            groups.add(new CardUsageTierBenefitGroup(tier, List.of()));
        }

        CardUsageTierType tierType = minimumAmounts.length == 2
                ? CardUsageTierType.SINGLE_TIER
                : CardUsageTierType.MULTIPLE_TIERS;
        return calculator.calculate(
                new BigDecimal(recognizedAmount),
                new CardUsageTierStructure(tierType, tiers),
                new CardUsageBenefitAllocation(List.of(), groups));
    }
}
