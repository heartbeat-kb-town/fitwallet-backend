package com.fitwallet.domain.card.mapper;

import com.fitwallet.domain.card.dto.CardType;
import com.fitwallet.domain.card.dto.CardTransactionStatus;
import com.fitwallet.domain.card.dto.CardUsageAmountSummary;
import com.fitwallet.domain.card.dto.CardUsageBenefitRule;
import com.fitwallet.domain.card.dto.CardUsageCardInfo;
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

/** 카드 이용 실적 원본 조회 SQL을 실제 MySQL 스키마와 시드 데이터로 검증한다. */
@SpringJUnitConfig(locations = "classpath:root-context.xml")
@Transactional
class CardUsageMapperIntegrationTest {

    private static final Long SEED_USER_ID = 1L;
    private static final Long NORI_USER_CARD_ID = 5L;
    private static final Long NORI_CARD_PRODUCT_ID = 43L;

    @Autowired
    private CardMapper cardMapper;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp(@Autowired DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void 사용자가_보유한_카드의_이용실적용_정보를_조회한다() {
        CardUsageCardInfo card = cardMapper.findUsageCardInfo(SEED_USER_ID, NORI_USER_CARD_ID);

        assertThat(card).isNotNull();
        assertThat(card.getCardProductId()).isEqualTo(NORI_CARD_PRODUCT_ID);
        assertThat(card.getCardName()).isEqualTo("KB국민 노리 체크카드");
        assertThat(card.getIssuerName()).isEqualTo("KB국민카드");
        assertThat(card.getCardType()).isEqualTo(CardType.DEBIT);
    }

    @Test
    void 다른_사용자의_카드는_이용실적용_정보로_조회하지_않는다() {
        assertThat(cardMapper.findUsageCardInfo(9999L, NORI_USER_CARD_ID)).isNull();
    }

    @Test
    void 삭제된_카드는_이용실적용_정보로_조회하지_않는다() {
        jdbcTemplate.update("UPDATE user_card SET is_deleted = 1 WHERE user_card_id = ?",
                NORI_USER_CARD_ID);

        assertThat(cardMapper.findUsageCardInfo(SEED_USER_ID, NORI_USER_CARD_ID)).isNull();
    }

    @Test
    void 조회기간의_실적인정금액과_미인정금액을_분리해_집계한다() {
        LocalDateTime startAt = LocalDateTime.of(2030, 1, 1, 0, 0);
        LocalDateTime endAt = LocalDateTime.of(2030, 2, 1, 0, 0);
        insertTransaction(new BigDecimal("10000.00"), startAt, true);
        insertTransaction(new BigDecimal("4000.00"), startAt.plusDays(1), false);
        insertTransaction(new BigDecimal("50000.00"), startAt.plusDays(2), true,
                CardTransactionStatus.CANCELED);
        insertTransaction(new BigDecimal("7000.00"), startAt.plusDays(3), false,
                CardTransactionStatus.CANCELED);
        insertTransaction(new BigDecimal("90000.00"), endAt, false);

        CardUsageAmountSummary summary = cardMapper.findUsageAmounts(
                SEED_USER_ID,
                NORI_USER_CARD_ID,
                CardUsagePeriodCondition.builder().startAt(startAt).endAt(endAt).build());

        assertThat(summary.getRecognizedAmount()).isEqualByComparingTo("10000.00");
        assertThat(summary.getExcludedAmount()).isEqualByComparingTo("4000.00");
    }

    @Test
    void 거래가_없는_기간의_실적금액은_모두_0이다() {
        LocalDateTime startAt = LocalDateTime.of(2040, 1, 1, 0, 0);

        CardUsageAmountSummary summary = cardMapper.findUsageAmounts(
                SEED_USER_ID,
                NORI_USER_CARD_ID,
                CardUsagePeriodCondition.builder()
                        .startAt(startAt)
                        .endAt(startAt.plusMonths(1))
                        .build());

        assertThat(summary.getRecognizedAmount()).isZero();
        assertThat(summary.getExcludedAmount()).isZero();
    }

    @Test
    void 플랜그룹_혜택에는_그룹의_공통구간만_연결한다() {
        List<CardUsageBenefitRule> rules = cardMapper.findUsageBenefitRules(NORI_CARD_PRODUCT_ID);

        assertThat(rules).hasSize(12);
        assertThat(rules).extracting(CardUsageBenefitRule::getBenefitId)
                .containsOnly(124L, 125L, 126L);
        assertThat(rules).allSatisfy(rule -> {
            assertThat(rule.getPlanGroupId()).isEqualTo(15L);
            assertThat(rule.getSourceTierId()).isBetween(131L, 134L);
            assertThat(rule.getTierMinimumAmount()).isNotNull();
        });
    }

    @Test
    void 플랜그룹이_없는_혜택에는_서비스의_개별구간만_연결한다() {
        List<CardUsageBenefitRule> rules = cardMapper.findUsageBenefitRules(36L);

        assertThat(rules).filteredOn(rule -> rule.getBenefitId().equals(108L))
                .hasSize(2)
                .allSatisfy(rule -> {
                    assertThat(rule.getPlanGroupId()).isNull();
                    assertThat(rule.getSourceTierId()).isIn(125L, 126L);
                });
    }

    private void insertTransaction(BigDecimal amount, LocalDateTime paidAt, boolean eligible) {
        insertTransaction(amount, paidAt, eligible, CardTransactionStatus.APPROVED);
    }

    private void insertTransaction(BigDecimal amount, LocalDateTime paidAt, boolean eligible,
                                   CardTransactionStatus transactionStatus) {
        jdbcTemplate.update("""
                INSERT INTO payment_transaction
                    (user_card_id, amount, final_amount, paid_at, is_eligible, transaction_status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, NORI_USER_CARD_ID, amount, amount, paidAt, eligible,
                transactionStatus.name());
    }
}
