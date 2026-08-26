package com.fitwallet.domain.report.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;

import com.fitwallet.domain.report.dto.response.CardRecommendationRawResponse;
import com.fitwallet.domain.report.dto.response.CategorySpendResponse;
import com.fitwallet.domain.report.dto.response.MissedSummaryResponse;
import com.fitwallet.domain.report.dto.response.PopularCardRawResponse;
import com.fitwallet.domain.report.dto.response.ReceivedBenefitSummaryResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(locations = "classpath:root-context.xml")
@Transactional
class BenefitReportMapperIntegrationTest {

    @Autowired
    private BenefitReportMapper benefitReportMapper;

    @Autowired
    private MissedBenefitMapper missedBenefitMapper;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(dataSource);
    }

    @Test
    void 시드_유저의_받은_혜택_요약을_조회한다() {
        ReceivedBenefitSummaryResponse result = benefitReportMapper.getReceivedBenefitSummary(1L, "2026-04");

        assertThat(result).isNotNull();
        assertThat(result.getTotalReceivedBenefit()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(result.getTotalDiscountAmount()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(result.getTotalPoint()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    void 취소거래는_받은혜택_놓친혜택_카테고리_지출에서_모두_제외한다() {
        jdbcTemplate().update("""
                INSERT INTO payment_transaction
                    (user_card_id, store_id, amount, discount_amount, final_amount, paid_at,
                     is_used_app, applied_benefit_service_id, better_user_card_id,
                     alternative_discount_amount, missed_amount, transaction_status)
                VALUES (1, 1, 10000, 1000, 9000, '2099-01-15 10:00:00',
                        0, 1, 2, 1500, 500, 'CANCELED')
                """);

        ReceivedBenefitSummaryResponse received = benefitReportMapper.getReceivedBenefitSummary(1L, "2099-01");
        assertThat(received.getTotalReceivedBenefit()).isZero();
        assertThat(received.getTotalDiscountAmount()).isZero();
        assertThat(received.getTotalPoint()).isZero();

        MissedSummaryResponse missed = missedBenefitMapper.getMissedSummary(1L, "2099-01");
        assertThat(missed.getAppUnusedAmount()).isZero();
        assertThat(missed.getCardMismatchAmount()).isZero();

        assertThat(benefitReportMapper.getCategorySpends(1L, "2099-01")).isEmpty();
    }

    /**
     * 시드 데이터의 point_currency는 전부 krw_per_point = 1.0000이라
     * "환산이 실제로 곱해지는지"를 숫자로 구분할 수 없다.
     * 그래서 krw_per_point가 1이 아닌 테스트 전용 포인트 화폐/혜택/결제 건을 직접 만들어,
     * 전/후 총액 차이로 환산 로직 자체를 검증한다. (@Transactional이라 테스트 후 롤백됨)
     *
     * 주의: getReceivedBenefitSummary()를 이 테스트 안에서 두 번 호출하면 안 된다.
     * @Transactional 테스트는 같은 SqlSession(=1차 캐시)을 공유해서, 같은 파라미터로
     * 두 번째 호출하면 JdbcTemplate으로 바꾼 데이터를 반영 못 하고 캐시된 첫 호출 결과를
     * 그대로 돌려준다(stale read). 그래서 "before"는 JdbcTemplate 원본 쿼리로 직접 재고,
     * mapper는 insert 이후 딱 한 번만 호출한다.
     */
    /**
     * 추천 후보 쿼리가 tier/limit 조인으로 서비스당 여러 행을 뱉으면
     * 서비스 레이어가 같은 예상 혜택을 중복 합산한다(#188). 매퍼가 (서비스, 카테고리)
     * 조합당 정확히 한 행만 돌려주는지 — 실 시드 데이터로 잠근다.
     */
    @Test
    void 추천_후보는_서비스와_카테고리_조합당_한_행만_반환한다() {
        Long userId = 1L;

        List<CategorySpendResponse> spends = benefitReportMapper.getCategorySpends(userId, "2026-07");
        assertThat(spends).isNotEmpty();

        List<Long> categoryIds = spends.stream()
                .map(CategorySpendResponse::getCategoryId)
                .distinct()
                .toList();

        List<CardRecommendationRawResponse> rows = benefitReportMapper.getRecommendedCards(userId, categoryIds);

        String inClause = categoryIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElseThrow();
        Integer distinctCombos = jdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM (" +
                        "  SELECT DISTINCT bs.service_id, c.category_id" +
                        "  FROM benefit_service bs" +
                        "  JOIN service_category sc ON bs.service_id = sc.service_id" +
                        "  JOIN category c ON sc.category_id = c.category_id" +
                        "  JOIN card_product cp ON bs.card_product_id = cp.card_product_id" +
                        "  WHERE c.category_id IN (" + inClause + ")" +
                        "  AND cp.card_product_id NOT IN (SELECT card_product_id FROM user_card WHERE user_id = ?)" +
                        ") t",
                Integer.class, userId
        );

        assertThat(rows).hasSize(distinctCombos);
    }

    @Test
    void 포인트로_받은_혜택은_krw_per_point만큼_환산되어_총액에_반영된다() {
        JdbcTemplate jdbc = jdbcTemplate();
        Long userId = 1L;
        String yearMonth = "2026-04";

        // "before"는 mapper가 아니라 같은 SQL을 직접 JdbcTemplate으로 조회 (세션 캐시 우회)
        BigDecimal before = jdbc.queryForObject(
                "SELECT COALESCE(SUM(" +
                        "  CASE WHEN bs.point_currency_id IS NOT NULL" +
                        "       THEN pt.discount_amount * pc.krw_per_point" +
                        "       ELSE pt.discount_amount" +
                        "  END" +
                        "), 0) " +
                        "FROM payment_transaction pt " +
                        "JOIN user_card uc ON pt.user_card_id = uc.user_card_id " +
                        "LEFT JOIN benefit_service bs ON pt.applied_benefit_service_id = bs.service_id " +
                        "LEFT JOIN point_currency pc ON bs.point_currency_id = pc.point_currency_id " +
                        "WHERE uc.user_id = ? AND DATE_FORMAT(pt.paid_at, '%Y-%m') = ?",
                BigDecimal.class, userId, yearMonth
        );
        // 총 포인트(환산 전 개수) before도 세션 캐시 우회를 위해 직접 조회
        BigDecimal beforePoint = jdbc.queryForObject(
                "SELECT COALESCE(SUM(CASE WHEN bs.point_currency_id IS NOT NULL" +
                        "       THEN pt.discount_amount ELSE 0 END), 0) " +
                        "FROM payment_transaction pt " +
                        "JOIN user_card uc ON pt.user_card_id = uc.user_card_id " +
                        "LEFT JOIN benefit_service bs ON pt.applied_benefit_service_id = bs.service_id " +
                        "WHERE uc.user_id = ? AND DATE_FORMAT(pt.paid_at, '%Y-%m') = ?",
                BigDecimal.class, userId, yearMonth
        );

        // 1) krw_per_point = 2.5000인 테스트 전용 포인트 화폐 생성
        jdbc.update(
                "INSERT INTO point_currency (currency_name, krw_per_point) VALUES (?, ?)",
                "테스트포인트_통합테스트", new BigDecimal("2.5000")
        );
        Long pointCurrencyId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        // 2) 그 포인트로 적립되는 테스트 전용 benefit_service 생성 (기존 card_product_id 재사용)
        jdbc.update(
                "INSERT INTO benefit_service " +
                        "(card_product_id, benefit_name, benefit_type, value_type, value_number, scope_type, point_currency_id) " +
                        "VALUES (?, ?, 'ACCUMULATE', 'RATE', ?, 'INDUSTRY', ?)",
                20L, "통합테스트용 포인트 적립", new BigDecimal("1.00"), pointCurrencyId
        );
        Long benefitServiceId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        // 3) discount_amount = 100.00 (포인트 개수, 원화 아님)인 결제 건 생성
        //    krw_per_point(2.5)를 곱하면 250원이 되어야 함
        int inserted = jdbc.update(
                "INSERT INTO payment_transaction " +
                        "(user_card_id, amount, discount_amount, final_amount, paid_at, is_used_app, applied_benefit_service_id) " +
                        "VALUES (?, ?, ?, ?, ?, 1, ?)",
                1L, new BigDecimal("10000.00"), new BigDecimal("100.00"), new BigDecimal("9900.00"),
                java.sql.Timestamp.valueOf("2026-04-15 10:00:00"), benefitServiceId
        );
        assertThat(inserted).isEqualTo(1);

        // mapper 호출은 insert 이후 이 한 번뿐 (같은 세션에서 두 번째 호출이 아니므로 캐시 영향 없음)
        ReceivedBenefitSummaryResponse after = benefitReportMapper.getReceivedBenefitSummary(userId, yearMonth);

        // 총 받은 혜택: 100포인트 × 2.5원/포인트 = 250원이 더해져야 함 (100원 그대로 더해지면 버그)
        assertThat(after.getTotalReceivedBenefit().subtract(before)).isEqualByComparingTo(new BigDecimal("250.00"));
        // 총 포인트: 환산 전 개수 100이 그대로 더해져야 함 (환산액 250이 섞이면 버그)
        assertThat(after.getTotalPoint().subtract(beforePoint)).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void 이번_달_카테고리_지출은_거래가_있는_카테고리만_돌려준다() {
        List<CategorySpendResponse> spends = benefitReportMapper.getCategorySpends(1L, "2026-07");

        assertThat(spends).isNotEmpty();
        assertThat(spends).allSatisfy(row -> {
            assertThat(row.getSpendAmount()).isGreaterThan(BigDecimal.ZERO);
            assertThat(row.getCategoryId()).isNotNull();
        });
        // 카테고리별로 한 행씩만 (GROUP BY category)
        assertThat(spends).extracting(CategorySpendResponse::getCategoryId).doesNotHaveDuplicates();
    }

    @Test
    void 보유_카드_혜택은_유저가_보유한_카드_상품만_돌려준다() {
        Long userId = 1L;

        // 시드 유저의 이번 달 지출 카테고리들
        List<CategorySpendResponse> spends = benefitReportMapper.getCategorySpends(userId, "2026-07");
        List<Long> categoryIds = spends.stream()
                .map(CategorySpendResponse::getCategoryId)
                .distinct()
                .toList();

        List<CardRecommendationRawResponse> owned = benefitReportMapper.getOwnedCardBenefits(userId, categoryIds);

        List<Long> ownedProductIds = jdbcTemplate().queryForList(
                "SELECT card_product_id FROM user_card WHERE user_id = ? AND is_deleted = 0",
                Long.class, userId);
        // 반환된 모든 행의 카드 상품은 실제 보유 상품이어야 한다
        assertThat(owned).allSatisfy(row ->
                assertThat(ownedProductIds).contains(row.getCardProductId()));
        // 요청한 카테고리 밖의 행은 없어야 한다
        assertThat(owned).allSatisfy(row ->
                assertThat(categoryIds).contains(row.getCategoryId()));
    }

    @Test
    void 인기_미보유_카드는_요청_개수_이하로_유저가_보유한_카드를_빼고_돌려준다() {
        Long userId = 1L;

        List<PopularCardRawResponse> popular = benefitReportMapper.getPopularUnownedCards(userId, 2);

        assertThat(popular).hasSizeLessThanOrEqualTo(2);

        List<Long> ownedProductIds = jdbcTemplate().queryForList(
                "SELECT card_product_id FROM user_card WHERE user_id = ? AND is_deleted = 0",
                Long.class, userId);
        assertThat(popular).extracting(PopularCardRawResponse::getCardProductId)
                .doesNotContainAnyElementsOf(ownedProductIds);
    }

    // ── card_product.detail_url 매핑 (#325) ─────────────────────────────────
    //
    // resultType 매핑은 SQL이 안 뽑은 컬럼을 조용히 버린다(§6). DTO에 detailUrl 필드가 있어도
    // 매퍼 XML이 cp.detail_url을 SELECT하지 않으면 응답에 늘 null이 실린다. 아래 두 테스트가
    // 그 침묵을 잡는다 — 컴파일도 통과하고 단위 테스트도 통과하는 종류의 실패다.

    @Test
    void 추천_후보_조회는_카드의_상품_상세_URL을_함께_가져온다() {
        Long userId = 1L;
        // V12가 URL을 채워 둔 미보유 카드. 시연에서 추천으로 노출되는 두 장 중 하나다.
        Long cardProductId = 54L;

        String expected = jdbcTemplate().queryForObject(
                "SELECT detail_url FROM card_product WHERE card_product_id = ?",
                String.class, cardProductId);
        assertThat(expected).as("V12가 %d번 카드에 URL을 채웠어야 한다", cardProductId).isNotNull();

        List<CardRecommendationRawResponse> candidates =
                benefitReportMapper.getRecommendedCards(userId, List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L));

        assertThat(candidates)
                .filteredOn(row -> cardProductId.equals(row.getCardProductId()))
                .isNotEmpty()
                .allSatisfy(row -> assertThat(row.getDetailUrl()).isEqualTo(expected));
    }

    @Test
    void 인기_미보유_카드_조회도_상품_상세_URL을_함께_가져온다() {
        // ⚠️ 시드 유저(1L)로는 이 쿼리가 항상 빈 결과다. 로컬 시드에 회원이 하나뿐이라
        // "누군가 보유한 카드 상품" 전부가 곧 시드 유저의 보유 카드이고, 쿼리가 그걸 통째로
        // 제외하기 때문이다. 콜드스타트 경로를 태우려면 보유 카드가 없는 사용자여야 한다.
        Long cardlessUserId = 9999L;

        // 인기 상위가 어느 카드일지는 보유 분포에 달려 있어 고정할 수 없다. 전 카드에 식별
        // 가능한 값을 심어 두고(클래스 레벨 @Transactional이 롤백한다) 그게 돌아오는지 본다.
        jdbcTemplate().update(
                "UPDATE card_product SET detail_url = CONCAT('https://example.test/', card_product_id)");

        List<PopularCardRawResponse> popular = benefitReportMapper.getPopularUnownedCards(cardlessUserId, 2);

        assertThat(popular).isNotEmpty();
        assertThat(popular).allSatisfy(card ->
                assertThat(card.getDetailUrl()).isEqualTo("https://example.test/" + card.getCardProductId()));
    }
}
