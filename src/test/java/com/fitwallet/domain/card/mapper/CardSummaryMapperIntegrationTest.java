package com.fitwallet.domain.card.mapper;

import com.fitwallet.domain.card.dto.CardListSortType;
import com.fitwallet.domain.card.dto.CardSummaryCardInfo;
import com.fitwallet.domain.card.dto.CardTransactionStatus;
import com.fitwallet.domain.card.dto.CardType;
import com.fitwallet.domain.card.dto.request.CardRecentTransactionSearchCondition;
import com.fitwallet.domain.card.dto.request.CardTransactionSearchCondition;
import com.fitwallet.domain.card.dto.response.CardListResponse;
import com.fitwallet.domain.card.dto.response.CardSummaryTransactionResponse;
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

/** 내 카드 탭 요약 Mapper 통합 테스트. */
@SpringJUnitConfig(locations = "classpath:root-context.xml")
@Transactional
class CardSummaryMapperIntegrationTest {

    private static final Long SEED_USER_ID = 1L;
    private static final Long SEED_CREDIT_CARD_ID = 1L;
    private static final Long SEED_DEBIT_CARD_ID = 5L;

    @Autowired
    private CardMapper cardMapper;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp(@Autowired DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void 요약용_체크카드_정보에_카드와_은행_잔액을_함께_채운다() {
        CardSummaryCardInfo card =
                cardMapper.findSummaryCardInfo(SEED_USER_ID, SEED_DEBIT_CARD_ID);

        assertThat(card).isNotNull();
        assertThat(card.getCardId()).isEqualTo(SEED_DEBIT_CARD_ID);
        assertThat(card.getCardProductId()).isNotNull();
        assertThat(card.getCardName()).isNotBlank();
        assertThat(card.getIssuerName()).isNotBlank();
        assertThat(card.getCardType()).isEqualTo(CardType.DEBIT);
        assertThat(card.getBankName()).isEqualTo("KB국민은행");
        assertThat(card.getBalance()).isEqualByComparingTo("1150000.00");
    }

    @Test
    void 다른_사용자거나_삭제된_카드는_요약용으로_조회되지_않는다() {
        jdbcTemplate.update(
                "UPDATE user_card SET is_deleted = 1 WHERE user_card_id = ?",
                SEED_CREDIT_CARD_ID);

        assertThat(cardMapper.findSummaryCardInfo(SEED_USER_ID, SEED_CREDIT_CARD_ID)).isNull();
        assertThat(cardMapper.findSummaryCardInfo(9999L, SEED_DEBIT_CARD_ID)).isNull();
    }

    @Test
    void 최근_이용내역은_시작을_포함하고_종료를_제외하여_최신순으로_조회한다() {
        LocalDateTime startAt = LocalDateTime.of(2099, 8, 4, 0, 0);
        LocalDateTime endAt = LocalDateTime.of(2099, 8, 6, 0, 0);
        LocalDateTime samePaidAt = startAt.plusDays(1).plusHours(10);

        insertTransaction(startAt.minusSeconds(1), "900.00", 1L);
        insertTransaction(startAt, "100.00", 1L);
        insertTransaction(samePaidAt, "200.00", null);
        insertTransaction(SEED_CREDIT_CARD_ID, samePaidAt, "300.00", 1L,
                CardTransactionStatus.CANCELED);
        insertTransaction(endAt, "800.00", 1L);

        List<CardSummaryTransactionResponse> transactions = cardMapper.findRecentTransactions(
                SEED_USER_ID,
                SEED_CREDIT_CARD_ID,
                CardRecentTransactionSearchCondition.builder()
                        .startAt(startAt)
                        .endAt(endAt)
                        .build());

        assertThat(transactions).hasSize(3);
        assertThat(transactions).extracting(CardSummaryTransactionResponse::getPaymentAmount)
                .containsExactly(
                        new BigDecimal("300.00"),
                        new BigDecimal("200.00"),
                        new BigDecimal("100.00"));
        assertThat(transactions).extracting(CardSummaryTransactionResponse::getTransactionStatus)
                .containsExactly(
                        CardTransactionStatus.CANCELED,
                        CardTransactionStatus.APPROVED,
                        CardTransactionStatus.APPROVED);
        assertThat(transactions.get(1).getStoreName()).isNull();
        assertThat(transactions.get(1).getCategoryName()).isNull();
        assertThat(transactions.get(1).getCategoryImageUrl()).isNull();
    }

    @Test
    void 최근사용순은_최근결제_동률이면_표시순서와_카드ID로_결정한다() {
        LocalDateTime latestPaidAt = LocalDateTime.of(2099, 9, 1, 12, 0);
        insertTransaction(SEED_CREDIT_CARD_ID, latestPaidAt, "100.00", 1L);
        insertTransaction(2L, latestPaidAt, "200.00", 1L);
        insertTransaction(4L, latestPaidAt.plusDays(1), "300.00", 1L,
                CardTransactionStatus.CANCELED);
        jdbcTemplate.update(
                "INSERT INTO user_card "
                        + "(user_id, card_product_id, first4, last4, expiry_date, display_order, is_deleted) "
                        + "VALUES (?, ?, '1234', '5678', '2030-12-31', 99, 0)",
                SEED_USER_ID,
                1L);

        List<CardListResponse> cards = cardMapper.findByUserId(
                SEED_USER_ID,
                CardListSortType.RECENTLY_USED,
                CardTransactionSearchCondition.builder()
                        .startAt(LocalDateTime.of(2000, 1, 1, 0, 0))
                        .endAt(LocalDateTime.of(2100, 1, 1, 0, 0))
                        .build());

        assertThat(cards).extracting(CardListResponse::getUserCardId)
                .startsWith(SEED_CREDIT_CARD_ID, 2L);
        assertThat(cards.get(cards.size() - 1).getCardProductId()).isEqualTo(1L);
    }

    private void insertTransaction(LocalDateTime paidAt, String amount, Long storeId) {
        insertTransaction(SEED_CREDIT_CARD_ID, paidAt, amount, storeId);
    }

    private void insertTransaction(Long userCardId, LocalDateTime paidAt,
                                   String amount, Long storeId) {
        insertTransaction(userCardId, paidAt, amount, storeId, CardTransactionStatus.APPROVED);
    }

    private void insertTransaction(Long userCardId, LocalDateTime paidAt,
                                   String amount, Long storeId,
                                   CardTransactionStatus transactionStatus) {
        jdbcTemplate.update(
                "INSERT INTO payment_transaction "
                        + "(user_card_id, store_id, amount, discount_amount, final_amount, paid_at, "
                        + "transaction_status) "
                        + "VALUES (?, ?, ?, 0, ?, ?, ?)",
                userCardId,
                storeId,
                new BigDecimal(amount),
                new BigDecimal(amount),
                Timestamp.valueOf(paidAt),
                transactionStatus.name());
    }
}
