package com.fitwallet.domain.card.mapper;

import com.fitwallet.domain.benefit.dto.LimitBasis;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitBrandTarget;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitRule;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitTargetUsage;
import com.fitwallet.domain.card.dto.request.CardUsagePeriodCondition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 카드 월간 혜택 평면 조회 SQL을 실제 MySQL 시드 데이터로 검증한다. */
@SpringJUnitConfig(locations = "classpath:root-context.xml")
@Transactional
class CardMonthlyBenefitMapperIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long ALL_PASS_USER_CARD_ID = 2L;
    private static final Long ALL_PASS_CARD_PRODUCT_ID = 15L;

    @Autowired
    private CardMapper cardMapper;

    @Test
    void 공유_금액한도와_서비스별_횟수한도를_함께_조회한다() {
        List<CardMonthlyBenefitRule> rules = cardMapper
                .findMonthlyBenefitRules(ALL_PASS_CARD_PRODUCT_ID);

        assertThat(rules).filteredOn(rule -> rule.getServiceId().equals(53L))
                .extracting(
                        CardMonthlyBenefitRule::getServicePlanGroupId,
                        CardMonthlyBenefitRule::getLimitPlanGroupId,
                        CardMonthlyBenefitRule::getTierId,
                        CardMonthlyBenefitRule::getLimitBasis,
                        CardMonthlyBenefitRule::isShared)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(10L, 10L, 21L, LimitBasis.AMOUNT, true),
                        org.assertj.core.groups.Tuple.tuple(10L, 10L, 22L, LimitBasis.AMOUNT, true),
                        org.assertj.core.groups.Tuple.tuple(10L, 10L, 23L, LimitBasis.AMOUNT, true),
                        org.assertj.core.groups.Tuple.tuple(10L, null, 57L, LimitBasis.COUNT, false));
    }

    @Test
    void 브랜드_혜택대상을_서비스별로_일괄_조회한다() {
        List<CardMonthlyBenefitBrandTarget> targets = cardMapper
                .findMonthlyBenefitBrandTargets(ALL_PASS_CARD_PRODUCT_ID);

        assertThat(targets).filteredOn(target -> target.getServiceId().equals(53L))
                .allSatisfy(target -> {
                    assertThat(target.getCategoryId()).isNotNull();
                    assertThat(target.getCategoryName()).isNotBlank();
                })
                .extracting(CardMonthlyBenefitBrandTarget::getBrandId)
                .containsExactly(15L, 16L, 17L);
    }

    @Test
    void 실제_혜택이_적용된_거래만_서비스와_대상별로_집계한다() {
        CardUsagePeriodCondition condition = CardUsagePeriodCondition.builder()
                .startAt(LocalDateTime.of(2026, 7, 1, 0, 0))
                .endAt(LocalDateTime.of(2026, 7, 24, 0, 0))
                .build();

        List<CardMonthlyBenefitTargetUsage> usages = cardMapper
                .findMonthlyBenefitTargetUsages(USER_ID, ALL_PASS_USER_CARD_ID, condition);

        assertThat(usages).filteredOn(usage -> usage.getServiceId().equals(53L))
                .singleElement()
                .satisfies(usage -> {
                    assertThat(usage.getTransactionCount()).isEqualTo(1L);
                    assertThat(usage.getTotalPaymentAmount()).isEqualByComparingTo("23800.00");
                    assertThat(usage.getReceivedBenefitAmount()).isEqualByComparingTo("1000.00");
                });
    }
}
