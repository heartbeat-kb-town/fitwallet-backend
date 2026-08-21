package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.CardMonthlyBenefitLimitStatus;
import com.fitwallet.domain.card.dto.CardTransactionStatus;
import com.fitwallet.domain.card.dto.CardUsagePerformanceStatus;
import com.fitwallet.domain.card.dto.request.CardTransactionSearchRequest;
import com.fitwallet.domain.card.dto.request.CardUsageSearchRequest;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionDetailResponse;
import com.fitwallet.domain.card.dto.response.CardUsageDetailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/** 시스템 기준일과 NOW() 기반 로컬 시드로 카드 월별 조회 전체 조합을 검증한다. */
@SpringJUnitConfig(locations = "classpath:root-context.xml")
@Transactional
class CardMonthlyBenefitServiceIntegrationTest {

    @Autowired
    private CardService cardService;

    @Autowired
    private Clock clock;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp(@Autowired DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void 올패스카드의_공유금액한도와_개별횟수한도를_함께_계산한다() {
        CardMonthlyBenefitResponse response = cardService.getCardMonthlyBenefit(1L, 2L);

        LocalDate today = LocalDate.now(clock);
        assertThat(response.getYearMonth()).isEqualTo(YearMonth.from(today).toString());
        assertThat(response.getAsOfDate()).isEqualTo(today);
        assertThat(response.getPerformance().getStatus())
                .isEqualTo(CardUsagePerformanceStatus.ACHIEVED);
        // 한도값은 참조 데이터라 고정이지만, 사용액은 시드 거래 물량에 딸려 움직인다
        // (V906 이 NOW() 기준으로 넣는 거래가 적재 월에 따라 이 구간에 들어오기도 하고 아니기도 하다).
        // 그래서 절대값 대신 세 값이 서로 맞물리는지를 본다 — 이 테스트가 확인해야 하는 것도
        // "공유 금액한도와 개별 횟수한도가 함께 계산되는가"이지 특정 금액이 아니다.
        BigDecimal totalLimit = response.getMonthlySummary().getTotalBenefitLimit();
        BigDecimal received = response.getMonthlySummary().getReceivedBenefitAmount();
        BigDecimal potential = response.getMonthlySummary().getPotentialBenefitAmount();

        assertThat(totalLimit).isPositive();
        assertThat(received).isPositive();
        assertThat(potential).isEqualByComparingTo(totalLimit.subtract(received));
        assertThat(response.getMonthlySummary().getPotentialBenefitRate())
                .isEqualByComparingTo(potential.multiply(BigDecimal.valueOf(100))
                        .divide(totalLimit, 1, RoundingMode.HALF_UP));

        // V905 가 더한 시연용 혜택(902 카페·906 병원)은 INDUSTRY 스코프라 카테고리 쪽으로 간다.
        assertThat(response.getCategoryBenefits()).hasSize(2);
        assertThat(response.getCategoryBenefits())
                .extracting(benefit -> benefit.getBenefitServiceId())
                .containsExactlyInAnyOrder(902L, 906L);

        assertThat(response.getBrandBenefits()).hasSize(6);
        assertThat(response.getBrandBenefits()).allSatisfy(benefit -> {
            assertThat(benefit.getLimitGroupId()).isEqualTo(10L);
            assertThat(benefit.getMonthlyLimits()).hasSize(2);
            assertThat(benefit.getItemLimitStatus())
                    .isEqualTo(CardMonthlyBenefitLimitStatus.AVAILABLE);
        });

        assertThat(response.getBrandBenefits())
                .filteredOn(benefit -> benefit.getBenefitServiceId().equals(53L))
                .flatExtracting(benefit -> benefit.getMonthlyLimits())
                .anySatisfy(limit -> {
                    assertThat(limit.getLimitId()).isNotNull();
                    assertThat(limit.isShared()).isTrue();
                    assertThat(limit.getUsedValue()).isEqualByComparingTo(received);
                    assertThat(limit.getRemainingValue()).isEqualByComparingTo(potential);
                });

        assertThat(response.getSharedLimitGroups()).singleElement().satisfies(group -> {
            assertThat(group.getLimitGroupId()).isEqualTo(10L);
            assertThat(group.getCategories()).isNotEmpty();
            assertThat(group.getSharedMonthlyLimit().getLimitId()).isNotNull();
            assertThat(group.getSharedMonthlyLimit().getUsedValue()).isEqualByComparingTo(received);
            assertThat(group.getUsageBreakdown()).isNotEmpty();
            assertThat(group.getBenefitServices()).hasSize(4);
            assertThat(group.getBenefitServices()).allSatisfy(service ->
                    assertThat(service.getTargets()).isNotEmpty());
            // 53·54 는 그룹 금액한도 위에 자기 tier 의 횟수한도를 하나씩 더 갖는다.
            // 902·906 은 그룹 한도만 쓰므로 개별 월 한도가 없다 — 여기가 두 한도가
            // 함께 계산되는지 갈라 보는 지점이다.
            assertThat(group.getBenefitServices())
                    .filteredOn(service -> service.getBenefitServiceId().equals(53L)
                            || service.getBenefitServiceId().equals(54L))
                    .hasSize(2)
                    .allSatisfy(service ->
                            assertThat(service.getServiceMonthlyLimits()).hasSize(1));
            assertThat(group.getBenefitServices())
                    .filteredOn(service -> service.getBenefitServiceId().equals(902L)
                            || service.getBenefitServiceId().equals(906L))
                    .hasSize(2)
                    .allSatisfy(service ->
                            assertThat(service.getServiceMonthlyLimits()).isEmpty());
        });
    }

    @Test
    void 보유카드_전체의_전월통합구간은_이용실적조회와_동일하다() {
        String previousYearMonth = YearMonth.from(LocalDate.now(clock))
                .minusMonths(1)
                .toString();
        CardUsageSearchRequest previousMonthRequest = new CardUsageSearchRequest();
        ReflectionTestUtils.setField(previousMonthRequest, "yearMonth", previousYearMonth);

        for (long cardId = 1L; cardId <= 5L; cardId++) {
            CardMonthlyBenefitResponse monthlyBenefit =
                    cardService.getCardMonthlyBenefit(1L, cardId);
            CardUsageDetailResponse previousMonthUsage =
                    cardService.getCardUsage(1L, cardId, previousMonthRequest);

            assertThat(monthlyBenefit.getPerformance().getStatus())
                    .as("cardId=%s 전월 실적 상태", cardId)
                    .isEqualTo(previousMonthUsage.getPerformanceStatus());
            assertThat(monthlyBenefit.getPerformance().getCurrentTier())
                    .as("cardId=%s 전월 통합 구간", cardId)
                    .usingRecursiveComparison()
                    .isEqualTo(previousMonthUsage.getCurrentTier());
            assertThat(monthlyBenefit.getCategoryBenefits()).isNotNull();
            assertThat(monthlyBenefit.getBrandBenefits()).isNotNull();
            assertThat(monthlyBenefit.getSharedLimitGroups()).isNotNull();
        }
    }

    @Test
    void 당월_거래내역과_이용실적을_조회하고_승인취소는_합계에서_제외한다() {
        LocalDate today = LocalDate.now(clock);
        YearMonth currentYearMonth = YearMonth.from(today);
        LocalDateTime startAt = currentYearMonth.atDay(1).atStartOfDay();
        LocalDateTime endAt = today.plusDays(1).atStartOfDay();
        LocalDateTime canceledAt = today.atTime(12, 0);

        jdbcTemplate.update("""
                INSERT INTO payment_transaction
                    (user_card_id, store_id, amount, discount_amount, final_amount, paid_at,
                     is_eligible, transaction_status)
                VALUES (5, 1, 987654.00, 0.00, 987654.00, ?, 1, 'CANCELED')
                """, canceledAt);

        CardTransactionSearchRequest transactionRequest = new CardTransactionSearchRequest();
        ReflectionTestUtils.setField(
                transactionRequest, "yearMonth", currentYearMonth.toString());
        ReflectionTestUtils.setField(transactionRequest, "size", 100);
        CardTransactionDetailResponse transactions =
                cardService.getCardTransactions(1L, 5L, transactionRequest);

        CardUsageSearchRequest usageRequest = new CardUsageSearchRequest();
        ReflectionTestUtils.setField(usageRequest, "yearMonth", currentYearMonth.toString());
        CardUsageDetailResponse usage = cardService.getCardUsage(1L, 5L, usageRequest);

        BigDecimal approvedAmount = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(amount), 0)
                FROM payment_transaction
                WHERE user_card_id = 5
                  AND transaction_status = 'APPROVED'
                  AND paid_at >= ?
                  AND paid_at < ?
                """, BigDecimal.class, startAt, endAt);

        assertThat(transactions.getYearMonth()).isEqualTo(currentYearMonth.toString());
        assertThat(transactions.getAvailableYearMonths()).startsWith(currentYearMonth.toString());
        assertThat(transactions.getTransactions().getContent())
                .anySatisfy(transaction -> {
                    assertThat(transaction.getPaymentAmount())
                            .isEqualByComparingTo("987654.00");
                    assertThat(transaction.getTransactionStatus())
                            .isEqualTo(CardTransactionStatus.CANCELED);
                });
        assertThat(transactions.getPaymentSummary().getAmount())
                .isEqualByComparingTo(approvedAmount);
        assertThat(usage.getYearMonth()).isEqualTo(currentYearMonth.toString());
        assertThat(usage.getAvailableYearMonths()).startsWith(currentYearMonth.toString());
    }
}
