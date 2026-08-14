package com.fitwallet.domain.report.mapper;

import com.fitwallet.domain.report.dto.response.CardSummaryResponse;
import com.fitwallet.domain.report.dto.response.CategoryTransactionRawResponse;
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
class CardBenefitMapperIntegrationTest {

    @Autowired
    private CardBenefitMapper cardBenefitMapper;

    @Autowired
    private DataSource dataSource;

    @Test
    void 시드_유저_소유의_카드_요약을_조회한다() {
        CardSummaryResponse result = cardBenefitMapper.getCardSummary(1L, 1L, "2026-04");

        assertThat(result).isNotNull();
        assertThat(result.getCardName()).isNotBlank();
    }

    @Test
    void 다른_유저_소유의_카드를_조회하면_결과가_없다() {
        CardSummaryResponse result = cardBenefitMapper.getCardSummary(999L, 1L, "2026-04");

        assertThat(result).isNull();
    }

    @Test
    void 시드_유저의_카테고리별_결제건_상세를_조회한다() {
        List<CategoryTransactionRawResponse> result =
                cardBenefitMapper.getCategoryTransactions(1L, 1L, "2026-04");

        assertThat(result).isNotNull();
    }

    @Test
    void 다른_유저_소유의_카드로_결제건을_조회하면_결과가_없다() {
        List<CategoryTransactionRawResponse> result =
                cardBenefitMapper.getCategoryTransactions(999L, 1L, "2026-04");

        assertThat(result).isEmpty();
    }

    @Test
    void 상세는_혜택_받은_결제만_조회되고_benefitType이_채워진다() {
        List<CategoryTransactionRawResponse> result =
                cardBenefitMapper.getCategoryTransactions(1L, 1L, "2026-07");

        // 혜택 없는 결제(applied_benefit_service_id IS NULL)는 제외되므로
        // 돌아온 모든 행은 benefitType과 benefitAmount가 반드시 있어야 한다
        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(tx -> {
            assertThat(tx.getBenefitType()).isNotNull();
            assertThat(tx.getBenefitAmount()).isNotNull();
        });
    }

    @Test
    void 카드_요약은_할인과_포인트를_분리해_집계한다() {
        CardSummaryResponse result = cardBenefitMapper.getCardSummary(1L, 1L, "2026-07");

        assertThat(result).isNotNull();
        assertThat(result.getTotalDiscount()).isNotNull();
        assertThat(result.getTotalPoint()).isNotNull();
        assertThat(result.getTotalSpend()).isNotNull();
    }

    @Test
    void 취소거래는_카드별_혜택요약과_상세에서_제외한다() {
        new JdbcTemplate(dataSource).update("""
                INSERT INTO payment_transaction
                    (user_card_id, store_id, amount, discount_amount, final_amount, paid_at,
                     applied_benefit_service_id, transaction_status)
                VALUES (1, 1, 10000, 1000, 9000, '2099-01-15 10:00:00', 1, 'CANCELED')
                """);

        CardSummaryResponse summary = cardBenefitMapper.getCardSummary(1L, 1L, "2099-01");
        List<CategoryTransactionRawResponse> transactions =
                cardBenefitMapper.getCategoryTransactions(1L, 1L, "2099-01");

        assertThat(summary).isNotNull();
        assertThat(summary.getTotalDiscount()).isZero();
        assertThat(summary.getTotalPoint()).isZero();
        assertThat(summary.getTotalSpend()).isZero();
        assertThat(transactions).isEmpty();
    }
}
