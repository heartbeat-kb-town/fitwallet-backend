package com.fitwallet.domain.report.mapper;

import com.fitwallet.domain.report.dto.LossType;
import com.fitwallet.domain.report.dto.response.MissedSummaryResponse;
import com.fitwallet.domain.report.dto.response.MissedTransactionRawResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(locations = "classpath:root-context.xml")
@Transactional
class MissedBenefitMapperIntegrationTest {

    @Autowired
    private MissedBenefitMapper missedBenefitMapper;

    @Autowired
    private DataSource dataSource;

    @Test
    void 놓친혜택_요약은_앱미사용과_카드선택_손실을_분리해_집계한다() {
        MissedSummaryResponse result = missedBenefitMapper.getMissedSummary(1L, "2026-07");

        assertThat(result).isNotNull();
        assertThat(result.getAppUnusedAmount()).isNotNull();
        assertThat(result.getCardMismatchAmount()).isNotNull();
    }

    @Test
    void 놓친거래는_더_좋은_카드가_있었던_건만_조회되고_대안카드_차액이_채워진다() {
        List<MissedTransactionRawResponse> result =
                missedBenefitMapper.getMissedTransactions(1L, "2026-07", LossType.APP_UNUSED.getIsUsedApp());

        // better_user_card_id IS NOT NULL 필터라 모든 행은 대안 카드명과 놓친 금액이 있어야 한다
        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(tx -> {
            assertThat(tx.getAlternativeCardName()).isNotBlank();
            assertThat(tx.getDiffAmount()).isNotNull();
            assertThat(tx.getUsedCardName()).isNotBlank();
        });
    }

    @Test
    void 다른_유저의_놓친거래는_조회되지_않는다() {
        List<MissedTransactionRawResponse> result =
                missedBenefitMapper.getMissedTransactions(999L, "2026-07", LossType.APP_UNUSED.getIsUsedApp());

        assertThat(result).isEmpty();
    }

    @Test
    void 취소거래는_놓친혜택_요약과_상세에서_제외한다() {
        new JdbcTemplate(dataSource).update("""
                INSERT INTO payment_transaction
                    (user_card_id, store_id, amount, discount_amount, final_amount, paid_at,
                     is_used_app, better_user_card_id, alternative_discount_amount,
                     missed_amount, transaction_status)
                VALUES (1, 1, 10000, 0, 10000, '2099-01-15 10:00:00',
                        0, 2, 1500, 1500, 'CANCELED')
                """);

        MissedSummaryResponse summary = missedBenefitMapper.getMissedSummary(1L, "2099-01");
        List<MissedTransactionRawResponse> transactions = missedBenefitMapper
                .getMissedTransactions(1L, "2099-01", LossType.APP_UNUSED.getIsUsedApp());

        assertThat(summary.getAppUnusedAmount()).isZero();
        assertThat(summary.getCardMismatchAmount()).isZero();
        assertThat(transactions).isEmpty();
    }
}
