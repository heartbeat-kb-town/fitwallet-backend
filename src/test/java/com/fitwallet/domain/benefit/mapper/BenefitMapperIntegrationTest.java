package com.fitwallet.domain.benefit.mapper;

import com.fitwallet.domain.benefit.dto.LimitBasis;
import com.fitwallet.domain.benefit.dto.LimitPeriod;
import com.fitwallet.domain.benefit.dto.response.BenefitCandidateResponse;
import com.fitwallet.domain.benefit.dto.response.BenefitLimitResponse;
import com.fitwallet.domain.benefit.dto.response.BenefitPrevMonthSpendResponse;
import com.fitwallet.domain.benefit.dto.response.BenefitStoreResponse;
import com.fitwallet.domain.benefit.dto.response.BenefitUsageResponse;
import com.fitwallet.domain.benefit.dto.response.BenefitUserCardResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mapper 통합 테스트. docker compose로 띄운 실제 MySQL과 시드 데이터를 사용한다.
 * <p>
 * 클래스 레벨 {@code @Transactional}이 테스트마다 롤백하므로,
 * 데이터를 바꾸는 테스트를 써도 다음 테스트에 영향을 주지 않는다.
 * <p>
 * 존재하지 않는 id는 {@code CardMapperIntegrationTest}와 동일하게 {@code 9999L}을 쓴다
 * (시드의 실제 최대 id들보다 충분히 크다).
 */
@SpringJUnitConfig(locations = "classpath:root-context.xml")
@Transactional
class BenefitMapperIntegrationTest {

    /** 시드 데모 페르소나. user_card 5건을 갖고 있다. */
    private static final Long SEED_USER_ID = 1L;

    @Autowired
    private BenefitMapper benefitMapper;

    /**
     * 테스트 데이터를 흔들기 위한 용도. DataSource가 트랜잭션에 묶여 있어
     * 여기서 바꾼 값도 테스트 종료 시 함께 롤백된다.
     */
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp(@Autowired DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void 존재하는_가맹점을_조회한다() {
        BenefitStoreResponse store = benefitMapper.findStore(20L);

        assertThat(store).isNotNull();
        assertThat(store.getStoreName()).isEqualTo("스타벅스 세종대점");
        assertThat(store.getCategoryId()).isEqualTo(1L);
        assertThat(store.getBrandId()).isEqualTo(11L);
    }

    @Test
    void 존재하지_않는_가맹점은_null을_반환한다() {
        assertThat(benefitMapper.findStore(9999L)).isNull();
    }

    @Test
    void 사용자의_카드를_표시순서대로_조회한다() {
        List<BenefitUserCardResponse> cards = benefitMapper.findUserCards(SEED_USER_ID);

        assertThat(cards).hasSize(5)
                .isSortedAccordingTo(Comparator.comparing(BenefitUserCardResponse::getDisplayOrder));
    }

    @Test
    void 소프트_삭제된_카드는_목록에서_제외된다() {
        jdbcTemplate.update("UPDATE user_card SET is_deleted = 1 WHERE user_card_id = 1");

        assertThat(benefitMapper.findUserCards(SEED_USER_ID)).hasSize(4)
                .extracting(BenefitUserCardResponse::getUserCardId)
                .doesNotContain(1L);
    }

    @Test
    void 보유_카드가_없는_사용자는_빈_리스트를_반환한다() {
        assertThat(benefitMapper.findUserCards(9999L)).isEmpty();
    }

    @Test
    void 지난달_거래가_없는_카드는_결과에서_빠진다() {
        List<BenefitPrevMonthSpendResponse> spends =
                benefitMapper.findPrevMonthSpends(List.of(1L, 9999L));

        assertThat(spends).extracting(BenefitPrevMonthSpendResponse::getUserCardId)
                .contains(1L)
                .doesNotContain(9999L);
    }

    /**
     * 시드 거래가 한 건도 없는 {@code user_card 6}으로 검증한다(V904가 이 카드의 결제를 전부 지웠다).
     * 시드 페르소나의 카드를 쓰면 단언값이 시드 물량에 묶여, 데모 데이터를 늘릴 때마다 함께 깨진다.
     * <p>
     * 날짜는 쿼리와 같은 기준(직전 달)으로 맞춰야 하므로 SQL에서 {@code NOW()} 기준으로 만든다.
     */
    @Test
    void 실적미인정과_취소거래는_전월실적_합계에서_제외된다() {
        insertPrevMonthTransaction(50000, 1, "APPROVED");
        insertPrevMonthTransaction(30000, 0, "APPROVED");
        insertPrevMonthTransaction(70000, 1, "CANCELED");

        List<BenefitPrevMonthSpendResponse> spends = benefitMapper.findPrevMonthSpends(List.of(6L));

        assertThat(spends).singleElement()
                .satisfies(s -> assertThat(s.getPrevMonthSpend()).isEqualByComparingTo("50000.00"));
    }

    private void insertPrevMonthTransaction(int amount, int isEligible, String status) {
        jdbcTemplate.update("INSERT INTO payment_transaction "
                + "(user_card_id, amount, discount_amount, final_amount, paid_at, is_used_app, "
                + "is_eligible, transaction_status) "
                + "VALUES (6, ?, 0, ?, "
                + "DATE_FORMAT(NOW() - INTERVAL 1 MONTH, '%Y-%m-05 10:00:00'), 0, ?, ?)",
                amount, amount, isEligible, status);
    }

    @Test
    void BRAND_스코프는_service_brand에_매칭되는_브랜드일_때만_나온다() {
        List<BenefitCandidateResponse> matched =
                benefitMapper.findCandidates(47L, new BigDecimal("350000"), 11L, null);
        assertThat(matched).extracting(BenefitCandidateResponse::getServiceId).contains(133L);

        List<BenefitCandidateResponse> unmatched =
                benefitMapper.findCandidates(47L, new BigDecimal("350000"), 9999L, null);
        assertThat(unmatched).extracting(BenefitCandidateResponse::getServiceId).doesNotContain(133L);
    }

    @Test
    void INDUSTRY_스코프는_service_category에_매칭되는_업종일_때만_나온다() {
        List<BenefitCandidateResponse> matched =
                benefitMapper.findCandidates(47L, new BigDecimal("350000"), null, 4L);
        assertThat(matched).extracting(BenefitCandidateResponse::getServiceId).contains(134L);

        List<BenefitCandidateResponse> unmatched =
                benefitMapper.findCandidates(47L, new BigDecimal("350000"), null, 9999L);
        assertThat(unmatched).extracting(BenefitCandidateResponse::getServiceId).doesNotContain(134L);
    }

    @Test
    void 전월실적이_구간_밖이면_결과는_있지만_tierOk가_false다() {
        List<BenefitCandidateResponse> candidates =
                benefitMapper.findCandidates(47L, new BigDecimal("100000"), 11L, null);

        assertThat(candidates).filteredOn(c -> c.getServiceId().equals(133L))
                .singleElement()
                .satisfies(c -> assertThat(c.getTierOk()).isFalse());
    }

    @Test
    void 스코프에_걸리는_혜택이_없으면_빈_리스트다() {
        List<BenefitCandidateResponse> candidates =
                benefitMapper.findCandidates(47L, new BigDecimal("350000"), 9999L, 9999L);

        assertThat(candidates).isEmpty();
    }

    @Test
    void 건당_조건_컬럼을_SELECT에_포함한다() {
        // resultType 매핑은 SQL이 안 뽑은 컬럼을 조용히 버린다 — DTO에 필드만 있으면 항상 null이다.
        // 시드 service_id=1(TIME 할인 - 편의점)은 per_tx_limit_amount=1000, min_tx_amount=0이다.
        List<BenefitCandidateResponse> candidates =
                benefitMapper.findCandidates(1L, new BigDecimal("350000"), null, 2L);

        assertThat(candidates).filteredOn(c -> c.getServiceId().equals(1L))
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.getPerTxLimitAmount()).isEqualByComparingTo("1000.00");
                    assertThat(c.getMinTxAmount()).isEqualByComparingTo("0.00");
                });
    }

    @Test
    void ACCUMULATE_행은_포인트_정보가_CASHBACK_행은_null이다() {
        List<BenefitCandidateResponse> accumulate =
                benefitMapper.findCandidates(20L, new BigDecimal("250000"), 15L, 1L);
        assertThat(accumulate).isNotEmpty()
                .allSatisfy(c -> {
                    assertThat(c.getCurrencyName()).isEqualTo("마이신한포인트");
                    assertThat(c.getKrwPerPoint()).isEqualByComparingTo("1.0000");
                });

        List<BenefitCandidateResponse> cashback =
                benefitMapper.findCandidates(47L, new BigDecimal("350000"), 11L, 4L);
        assertThat(cashback).isNotEmpty()
                .allSatisfy(c -> {
                    assertThat(c.getCurrencyName()).isNull();
                    assertThat(c.getKrwPerPoint()).isNull();
                });
    }

    @Test
    void planGroupId로_묶인_tier의_한도를_조회한다() {
        List<BenefitLimitResponse> limits = benefitMapper.findLimits(10L, null, new BigDecimal("350000"));

        assertThat(limits).extracting(BenefitLimitResponse::getTierId).containsOnly(21L);
        assertThat(limits).singleElement().satisfies(l -> {
            assertThat(l.getLimitBasis()).isEqualTo(LimitBasis.AMOUNT);
            assertThat(l.getLimitPeriod()).isEqualTo(LimitPeriod.MONTH);
            assertThat(l.getLimitValue()).isEqualByComparingTo("30000.00");
        });
    }

    @Test
    void serviceId로_직결된_tier의_한도를_조회한다() {
        List<BenefitLimitResponse> limits = benefitMapper.findLimits(null, 73L, new BigDecimal("250000"));

        assertThat(limits).extracting(BenefitLimitResponse::getTierId).containsOnly(69L);
        assertThat(limits).extracting(BenefitLimitResponse::getLimitPeriod)
                .containsExactly(LimitPeriod.MONTH);
    }

    @Test
    void 매칭되는_결제가_없으면_사용량이_0이다() {
        BenefitUsageResponse usage =
                benefitMapper.findUsage(1L, 163L, LocalDateTime.of(2099, 1, 1, 0, 0));

        assertThat(usage.getUsedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(usage.getUsedCount()).isZero();
    }

    /**
     * 시드가 닿지 않는 2099년 구간에 직접 넣어 검증한다. 시드 페르소나의 실제 구간을 쓰면
     * 단언값이 데모 거래 물량에 묶인다 — {@code findUsage}가 확인해야 하는 것은 합계의
     * 절대값이 아니라 "취소 거래가 빠지는가"다.
     */
    @Test
    void AMOUNT_기준_소진량을_집계한다() {
        insertFutureUsage(10000, 3500, "APPROVED");
        insertFutureUsage(10000, 9000, "CANCELED");

        BenefitUsageResponse usage =
                benefitMapper.findUsage(1L, 163L, LocalDateTime.of(2099, 1, 1, 0, 0));

        assertThat(usage.getUsedAmount()).isEqualByComparingTo("3500.00");
    }

    private void insertFutureUsage(int amount, int discount, String status) {
        jdbcTemplate.update("INSERT INTO payment_transaction "
                + "(user_card_id, amount, discount_amount, final_amount, paid_at, "
                + "applied_tier_id, transaction_status) "
                + "VALUES (1, ?, ?, ?, '2099-01-20 10:00:00', 163, ?)",
                amount, discount, amount - discount, status);
    }

    /** AMOUNT 쪽과 같은 이유로 2099년 구간을 쓴다 — 건수도 시드 물량에 묶이면 안 된다. */
    @Test
    void COUNT_기준_소진량을_집계한다() {
        insertFutureUsage(5000, 500, "APPROVED");
        insertFutureUsage(6000, 600, "APPROVED");
        insertFutureUsage(7000, 700, "CANCELED");

        BenefitUsageResponse usage =
                benefitMapper.findUsage(1L, 163L, LocalDateTime.of(2099, 1, 1, 0, 0));

        assertThat(usage.getUsedCount()).isEqualTo(2L);
    }
}
