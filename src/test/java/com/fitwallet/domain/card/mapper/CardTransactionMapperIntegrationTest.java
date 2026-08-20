package com.fitwallet.domain.card.mapper;

import com.fitwallet.domain.card.dto.CardTransactionCardInfo;
import com.fitwallet.domain.card.dto.CardTransactionStatus;
import com.fitwallet.domain.card.dto.CardType;
import com.fitwallet.domain.card.dto.request.CardTransactionSearchCondition;
import com.fitwallet.domain.card.dto.response.CardTransactionItemResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 카드별 결제 내역 Mapper 통합 테스트. docker compose로 띄운 실제 MySQL을 사용한다.
 * 테스트에서 추가하거나 변경한 데이터는 각 테스트 종료 시 롤백된다.
 */
@SpringJUnitConfig(locations = "classpath:root-context.xml")
@Transactional
class CardTransactionMapperIntegrationTest {

    private static final Long SEED_USER_ID = 1L;
    private static final Long SEED_CREDIT_CARD_ID = 1L;
    private static final Long SEED_STORE_ID = 1L;

    @Autowired
    private CardMapper cardMapper;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp(@Autowired DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void 결제내역용_카드_정보를_조회한다() {
        CardTransactionCardInfo card =
                cardMapper.findTransactionCardInfo(SEED_USER_ID, SEED_CREDIT_CARD_ID);

        assertThat(card).isNotNull();
        assertThat(card.getCardId()).isEqualTo(SEED_CREDIT_CARD_ID);
        assertThat(card.getCardProductId()).isNotNull();
        assertThat(card.getCardName()).isNotBlank();
        assertThat(card.getIssuerName()).isNotBlank();
        assertThat(card.getCardType()).isEqualTo(CardType.CREDIT);
        assertThat(card.getMaskedRearNumber()).hasSize(4);
    }

    @Test
    void 존재하지_않거나_다른_사용자의_카드는_조회되지_않는다() {
        assertThat(cardMapper.findTransactionCardInfo(SEED_USER_ID, 9999L)).isNull();
        assertThat(cardMapper.findTransactionCardInfo(9999L, SEED_CREDIT_CARD_ID)).isNull();
    }

    @Test
    void 소프트_삭제된_카드는_조회되지_않는다() {
        jdbcTemplate.update(
                "UPDATE user_card SET is_deleted = 1 WHERE user_card_id = ?",
                SEED_CREDIT_CARD_ID);

        assertThat(cardMapper.findTransactionCardInfo(SEED_USER_ID, SEED_CREDIT_CARD_ID)).isNull();
    }

    @Test
    void 승인취소를_포함해_가장_오래된_거래시각을_조회한다() {
        LocalDateTime oldestPaidAt = LocalDateTime.of(2000, 1, 15, 10, 0);
        insertTransaction(
                oldestPaidAt,
                SEED_STORE_ID,
                "100.00",
                "100.00",
                false,
                CardTransactionStatus.CANCELED);
        insertTransaction(
                oldestPaidAt.plusMonths(1),
                SEED_STORE_ID,
                "200.00",
                "200.00",
                true);

        assertThat(cardMapper.findOldestTransactionPaidAt(
                SEED_USER_ID, SEED_CREDIT_CARD_ID)).isEqualTo(oldestPaidAt);
    }

    @Test
    void 존재하지_않는_카드의_최초거래시각은_null이다() {
        assertThat(cardMapper.findOldestTransactionPaidAt(SEED_USER_ID, 9999L)).isNull();
    }

    @Test
    void 시작시각은_포함하고_종료시각은_제외하여_amount를_합산한다() {
        LocalDateTime startAt = LocalDateTime.of(2099, 1, 1, 0, 0);
        LocalDateTime endAt = LocalDateTime.of(2099, 2, 1, 0, 0);

        insertTransaction(startAt.minusSeconds(1), SEED_STORE_ID, "900.00", "1.00", true);
        insertTransaction(startAt, SEED_STORE_ID, "100.00", "10.00", true);
        insertTransaction(startAt.plusDays(1), SEED_STORE_ID, "200.00", "20.00", false);
        insertTransaction(startAt.plusDays(2), SEED_STORE_ID,
                "500.00", "500.00", true, CardTransactionStatus.CANCELED);
        insertTransaction(endAt, SEED_STORE_ID, "800.00", "1.00", true);

        BigDecimal amount = cardMapper.sumTransactionAmount(
                SEED_USER_ID,
                SEED_CREDIT_CARD_ID,
                condition(startAt, endAt, null, null, 20));

        assertThat(amount).isEqualByComparingTo("300.00");
    }

    @Test
    void 조회기간에_결제가_없으면_합계는_0이다() {
        BigDecimal amount = cardMapper.sumTransactionAmount(
                SEED_USER_ID,
                SEED_CREDIT_CARD_ID,
                condition(
                        LocalDateTime.of(2098, 1, 1, 0, 0),
                        LocalDateTime.of(2098, 2, 1, 0, 0),
                        null,
                        null,
                        20));

        assertThat(amount).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void 결제예정금액은_조회기간의_승인거래_finalAmount만_합산한다() {
        LocalDateTime startAt = LocalDateTime.of(2097, 1, 1, 0, 0);
        LocalDateTime endAt = LocalDateTime.of(2097, 2, 1, 0, 0);

        insertTransaction(startAt.minusSeconds(1), SEED_STORE_ID, "900.00", "1.00", true);
        insertTransaction(startAt, SEED_STORE_ID, "100.00", "90.00", true);
        insertTransaction(startAt.plusDays(1), SEED_STORE_ID, "200.00", "180.00", false);
        insertTransaction(startAt.plusDays(2), SEED_STORE_ID,
                "500.00", "450.00", true, CardTransactionStatus.CANCELED);
        insertTransaction(endAt, SEED_STORE_ID, "800.00", "1.00", true);

        BigDecimal amount = cardMapper.sumScheduledPaymentAmount(
                SEED_USER_ID,
                SEED_CREDIT_CARD_ID,
                condition(startAt, endAt, null, null, 20));

        assertThat(amount).isEqualByComparingTo("270.00");
    }

    @Test
    void 기존_적립거래의_최종결제금액은_승인금액으로_보정된다() {
        Integer inconsistentRows = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM payment_transaction pt
                         JOIN benefit_service bs
                           ON bs.service_id = pt.applied_benefit_service_id
                WHERE bs.benefit_type = 'ACCUMULATE'
                  AND pt.final_amount <> pt.amount
                """, Integer.class);

        assertThat(inconsistentRows).isZero();
    }

    @Test
    void 결제내역은_최신순이며_limit만큼_조회한다() {
        LocalDateTime startAt = LocalDateTime.of(2099, 3, 1, 0, 0);
        LocalDateTime firstPaidAt = startAt.plusDays(1);
        LocalDateTime secondPaidAt = startAt.plusDays(2);
        LocalDateTime thirdPaidAt = startAt.plusDays(3);

        insertTransaction(firstPaidAt, SEED_STORE_ID, "100.00", "100.00", true);
        insertTransaction(secondPaidAt, SEED_STORE_ID, "200.00", "200.00", true);
        insertTransaction(thirdPaidAt, SEED_STORE_ID,
                "300.00", "300.00", true, CardTransactionStatus.CANCELED);

        List<CardTransactionItemResponse> transactions = cardMapper.findTransactions(
                SEED_USER_ID,
                SEED_CREDIT_CARD_ID,
                condition(startAt, startAt.plusMonths(1), null, null, 2));

        assertThat(transactions).hasSize(2);
        assertThat(transactions)
                .extracting(CardTransactionItemResponse::getPaidAt)
                .containsExactly(thirdPaidAt, secondPaidAt);
        assertThat(transactions)
                .extracting(CardTransactionItemResponse::getTransactionStatus)
                .containsExactly(CardTransactionStatus.CANCELED, CardTransactionStatus.APPROVED);
        assertThat(transactions.get(0).getPerformanceIncluded()).isFalse();
    }

    @Test
    void 결제시각이_같으면_ID_내림차순으로_정렬하고_복합커서를_적용한다() {
        LocalDateTime startAt = LocalDateTime.of(2099, 4, 1, 0, 0);
        LocalDateTime paidAt = startAt.plusDays(1);

        insertTransaction(paidAt, SEED_STORE_ID, "100.00", "100.00", true);
        insertTransaction(paidAt, SEED_STORE_ID, "200.00", "200.00", true);
        insertTransaction(paidAt, SEED_STORE_ID, "300.00", "300.00", true);

        List<Long> transactionIds = jdbcTemplate.queryForList(
                "SELECT payment_transaction_id FROM payment_transaction "
                        + "WHERE user_card_id = ? AND paid_at = ? "
                        + "ORDER BY payment_transaction_id DESC",
                Long.class,
                SEED_CREDIT_CARD_ID,
                Timestamp.valueOf(paidAt));

        List<CardTransactionItemResponse> firstResult = cardMapper.findTransactions(
                SEED_USER_ID,
                SEED_CREDIT_CARD_ID,
                condition(startAt, startAt.plusMonths(1), null, null, 10));

        assertThat(firstResult)
                .extracting(CardTransactionItemResponse::getTransactionId)
                .containsExactlyElementsOf(transactionIds);

        List<CardTransactionItemResponse> cursorResult = cardMapper.findTransactions(
                SEED_USER_ID,
                SEED_CREDIT_CARD_ID,
                condition(startAt, startAt.plusMonths(1), paidAt, transactionIds.get(1), 10));

        assertThat(cursorResult)
                .extracting(CardTransactionItemResponse::getTransactionId)
                .containsExactly(transactionIds.get(2));
    }

    @Test
    void 가맹점이_없는_결제도_null_표시정보와_함께_조회한다() {
        LocalDateTime startAt = LocalDateTime.of(2099, 5, 1, 0, 0);
        LocalDateTime paidAt = startAt.plusDays(1);
        insertTransaction(paidAt, null, "35000.00", "30000.00", false);

        List<CardTransactionItemResponse> transactions = cardMapper.findTransactions(
                SEED_USER_ID,
                SEED_CREDIT_CARD_ID,
                condition(startAt, startAt.plusMonths(1), null, null, 10));

        assertThat(transactions).singleElement().satisfies(transaction -> {
            assertThat(transaction.getStoreName()).isNull();
            assertThat(transaction.getCategoryName()).isNull();
            assertThat(transaction.getCategoryImageUrl()).isNull();
            assertThat(transaction.getPaymentAmount()).isEqualByComparingTo("35000.00");
            assertThat(transaction.getPerformanceIncluded()).isFalse();
        });
    }

    private void insertTransaction(LocalDateTime paidAt, Long storeId,
                                   String amount, String finalAmount, boolean eligible) {
        insertTransaction(paidAt, storeId, amount, finalAmount,
                eligible, CardTransactionStatus.APPROVED);
    }

    private void insertTransaction(LocalDateTime paidAt, Long storeId,
                                   String amount, String finalAmount, boolean eligible,
                                   CardTransactionStatus transactionStatus) {
        jdbcTemplate.update(
                "INSERT INTO payment_transaction "
                        + "(user_card_id, store_id, amount, discount_amount, final_amount, paid_at, "
                        + "is_eligible, transaction_status) "
                        + "VALUES (?, ?, ?, 0, ?, ?, ?, ?)",
                SEED_CREDIT_CARD_ID,
                storeId,
                new BigDecimal(amount),
                new BigDecimal(finalAmount),
                Timestamp.valueOf(paidAt),
                eligible,
                transactionStatus.name());
    }

    private CardTransactionSearchCondition condition(
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime cursorPaidAt,
            Long cursorTransactionId,
            int limit) {
        return CardTransactionSearchCondition.builder()
                .startAt(startAt)
                .endAt(endAt)
                .cursorPaidAt(cursorPaidAt)
                .cursorTransactionId(cursorTransactionId)
                .limit(limit)
                .build();
    }
}
