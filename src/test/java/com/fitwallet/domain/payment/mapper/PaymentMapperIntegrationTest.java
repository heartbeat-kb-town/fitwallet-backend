package com.fitwallet.domain.payment.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(locations = "classpath:root-context.xml")
@Transactional
class PaymentMapperIntegrationTest {

    private static final Long SEED_USER_ID = 1L;
    private static final Long SEED_CREDIT_CARD_ID = 2L;
    private static final Long SEED_ACCUMULATE_DEBIT_CARD_ID = 3L;
    private static final Long SEED_CASHBACK_DEBIT_CARD_ID = 5L;
    private static final Long SEED_STORE_ID = 20L;
    private static final Long CASHBACK_SERVICE_ID = 124L;
    private static final Long ACCUMULATE_SERVICE_ID = 72L;

    @Autowired
    private PaymentMapper paymentMapper;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp(@Autowired DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void 캐시백_체크카드는_할인반영금액만큼_잔액을_차감한다() {
        BigDecimal before = findBalance(SEED_CASHBACK_DEBIT_CARD_ID);
        Long paymentSessionId = insertPayment(
                SEED_CASHBACK_DEBIT_CARD_ID,
                CASHBACK_SERVICE_ID,
                new BigDecimal("35000"),
                new BigDecimal("33600"));

        int updatedRows = paymentMapper.updateDebitCardBalanceAfterPayment(
                SEED_USER_ID, SEED_CASHBACK_DEBIT_CARD_ID, paymentSessionId);

        assertThat(updatedRows).isOne();
        assertThat(findBalance(SEED_CASHBACK_DEBIT_CARD_ID))
                .isEqualByComparingTo(before.subtract(new BigDecimal("33600")));
    }

    @Test
    void 적립_체크카드는_혜택금액을_빼지_않고_승인금액_전액을_차감한다() {
        BigDecimal before = findBalance(SEED_ACCUMULATE_DEBIT_CARD_ID);
        Long paymentSessionId = insertPayment(
                SEED_ACCUMULATE_DEBIT_CARD_ID,
                ACCUMULATE_SERVICE_ID,
                new BigDecimal("35000"),
                new BigDecimal("33600"));

        int updatedRows = paymentMapper.updateDebitCardBalanceAfterPayment(
                SEED_USER_ID, SEED_ACCUMULATE_DEBIT_CARD_ID, paymentSessionId);

        assertThat(updatedRows).isOne();
        assertThat(findBalance(SEED_ACCUMULATE_DEBIT_CARD_ID))
                .isEqualByComparingTo(before.subtract(new BigDecimal("35000")));
    }

    @Test
    void 신용카드는_잔액차감_대상에서_제외한다() {
        int updatedRows = paymentMapper.updateDebitCardBalanceAfterPayment(
                SEED_USER_ID, SEED_CREDIT_CARD_ID, 2L);

        assertThat(updatedRows).isZero();
        assertThat(findBalance(SEED_CREDIT_CARD_ID)).isNull();
    }

    private Long insertPayment(
            Long userCardId,
            Long benefitServiceId,
            BigDecimal amount,
            BigDecimal finalAmount) {
        String suffix = userCardId.toString();
        String paymentId = "balance-test-payment-" + suffix;
        paymentMapper.insertScannedPaymentSession(
                userCardId,
                "balance-test-session-" + suffix,
                paymentId,
                SEED_STORE_ID,
                amount,
                LocalDateTime.now().plusMinutes(5));
        Long paymentSessionId = jdbcTemplate.queryForObject(
                "SELECT payment_session_id FROM payment_session WHERE payment_id = ?",
                Long.class,
                paymentId);
        paymentMapper.insertPaymentTransaction(
                userCardId,
                SEED_STORE_ID,
                paymentSessionId,
                amount,
                amount.subtract(finalAmount),
                finalAmount,
                LocalDateTime.now(),
                benefitServiceId,
                null,
                null,
                null,
                null);
        return paymentSessionId;
    }

    private BigDecimal findBalance(Long userCardId) {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM user_card WHERE user_card_id = ?",
                BigDecimal.class,
                userCardId);
    }
}
