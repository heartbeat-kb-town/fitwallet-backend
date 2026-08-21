package com.fitwallet.domain.card.mapper;

import com.fitwallet.domain.benefit.dto.LimitBasis;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitBrandTarget;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitRule;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitTargetUsage;
import com.fitwallet.domain.card.dto.CardTransactionStatus;
import com.fitwallet.domain.card.dto.request.CardUsagePeriodCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
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

    /** service_id 53(편의점 할인)이 대상으로 삼는 브랜드 중 둘. 대상별 분리를 보려고 서로 다른 브랜드를 쓴다. */
    private static final Long GS25_STORE_ID = 46L;
    private static final Long GS25_BRAND_ID = 15L;
    private static final Long CU_STORE_ID = 49L;

    @Autowired
    private CardMapper cardMapper;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp(@Autowired DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

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

    /**
     * 시드가 닿지 않는 2099년 구간에 직접 넣어 검증한다. 시드 페르소나의 실제 구간을 쓰면
     * 단언값이 데모 거래 물량에 묶여, 데모 데이터를 늘릴 때마다 함께 깨진다.
     * <p>
     * 같은 서비스라도 가맹점 대상(브랜드)이 다르면 별도 행으로 갈라지는지도 함께 본다 —
     * GROUP BY 가 service_id 만이 아니라 category_id·brand_id 까지 묶기 때문이다.
     */
    @Test
    void 실제_혜택이_적용된_거래만_서비스와_대상별로_집계한다() {
        LocalDateTime startAt = LocalDateTime.of(2099, 1, 1, 0, 0);
        insertBenefitTransaction(GS25_STORE_ID, startAt.plusDays(1), "10000.00", "1000.00",
                CardTransactionStatus.APPROVED);
        insertBenefitTransaction(GS25_STORE_ID, startAt.plusDays(2), "13800.00", "1000.00",
                CardTransactionStatus.APPROVED);
        insertBenefitTransaction(CU_STORE_ID, startAt.plusDays(3), "20000.00", "2000.00",
                CardTransactionStatus.APPROVED);
        insertPlainTransaction(GS25_STORE_ID, startAt.plusDays(4));
        insertBenefitTransaction(GS25_STORE_ID, startAt.plusMonths(1), "90000.00", "9000.00",
                CardTransactionStatus.APPROVED);

        CardUsagePeriodCondition condition = CardUsagePeriodCondition.builder()
                .startAt(startAt)
                .endAt(startAt.plusMonths(1))
                .build();

        List<CardMonthlyBenefitTargetUsage> usages = cardMapper
                .findMonthlyBenefitTargetUsages(USER_ID, ALL_PASS_USER_CARD_ID, condition);

        assertThat(usages).hasSize(2);
        assertThat(usages).filteredOn(usage -> GS25_BRAND_ID.equals(usage.getBrandId()))
                .singleElement()
                .satisfies(usage -> {
                    assertThat(usage.getServiceId()).isEqualTo(53L);
                    assertThat(usage.getTransactionCount()).isEqualTo(2L);
                    assertThat(usage.getTotalPaymentAmount()).isEqualByComparingTo("23800.00");
                    assertThat(usage.getReceivedBenefitAmount()).isEqualByComparingTo("2000.00");
                });
    }

    @Test
    void 취소된_혜택적용_거래는_월간혜택_사용량에서_제외한다() {
        LocalDateTime startAt = LocalDateTime.of(2099, 1, 1, 0, 0);
        insertBenefitTransaction(GS25_STORE_ID, startAt.plusDays(1), "10000.00", "1000.00",
                CardTransactionStatus.APPROVED);
        insertBenefitTransaction(GS25_STORE_ID, startAt.plusDays(2), "50000.00", "5000.00",
                CardTransactionStatus.CANCELED);

        List<CardMonthlyBenefitTargetUsage> usages = cardMapper
                .findMonthlyBenefitTargetUsages(
                        USER_ID,
                        ALL_PASS_USER_CARD_ID,
                        CardUsagePeriodCondition.builder()
                                .startAt(startAt)
                                .endAt(startAt.plusMonths(1))
                                .build());

        assertThat(usages).singleElement().satisfies(usage -> {
            assertThat(usage.getTransactionCount()).isEqualTo(1L);
            assertThat(usage.getTotalPaymentAmount()).isEqualByComparingTo("10000.00");
            assertThat(usage.getReceivedBenefitAmount()).isEqualByComparingTo("1000.00");
        });
    }

    private void insertBenefitTransaction(Long storeId, LocalDateTime paidAt, String amount,
                                          String discountAmount,
                                          CardTransactionStatus transactionStatus) {
        BigDecimal paymentAmount = new BigDecimal(amount);
        BigDecimal benefitAmount = new BigDecimal(discountAmount);
        jdbcTemplate.update("""
                INSERT INTO payment_transaction
                    (user_card_id, store_id, amount, discount_amount, final_amount, paid_at,
                     applied_benefit_service_id, applied_tier_id, transaction_status)
                VALUES (?, ?, ?, ?, ?, ?, 53, 21, ?)
                """,
                ALL_PASS_USER_CARD_ID,
                storeId,
                paymentAmount,
                benefitAmount,
                paymentAmount.subtract(benefitAmount),
                paidAt,
                transactionStatus.name());
    }

    /** 혜택이 적용되지 않은 결제. applied_benefit_service_id 가 NULL 이라 집계에서 빠져야 한다. */
    private void insertPlainTransaction(Long storeId, LocalDateTime paidAt) {
        jdbcTemplate.update("""
                INSERT INTO payment_transaction
                    (user_card_id, store_id, amount, discount_amount, final_amount, paid_at,
                     transaction_status)
                VALUES (?, ?, 50000.00, 0.00, 50000.00, ?, 'APPROVED')
                """,
                ALL_PASS_USER_CARD_ID, storeId, paidAt);
    }
}
