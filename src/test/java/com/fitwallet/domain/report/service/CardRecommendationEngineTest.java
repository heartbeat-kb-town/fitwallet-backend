package com.fitwallet.domain.report.service;

import com.fitwallet.domain.report.dto.response.CardRecommendationRawResponse;
import com.fitwallet.domain.report.dto.response.CardRecommendationResponse;
import com.fitwallet.domain.report.dto.response.MonthlyCategorySpendRawResponse;
import com.fitwallet.domain.report.dto.response.PopularCardRawResponse;
import com.fitwallet.domain.report.mapper.BenefitReportMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardRecommendationEngineTest {

    private static final Long USER_ID = 1L;
    private static final String YEAR_MONTH = "2026-04";
    // 2026-04 기준 최근 3개월
    private static final String M1 = "2026-02";
    private static final String M2 = "2026-03";
    private static final String M3 = "2026-04";

    @Mock
    private BenefitReportMapper benefitReportMapper;

    @InjectMocks
    private CardRecommendationEngine engine;

    private MonthlyCategorySpendRawResponse spend(Long categoryId, String categoryName, String ym, long amount) {
        return MonthlyCategorySpendRawResponse.builder()
                .categoryId(categoryId)
                .categoryName(categoryName)
                .yearMonth(ym)
                .spendAmount(BigDecimal.valueOf(amount))
                .build();
    }

    /** 카테고리 하나를 3개월 동일 금액으로 채운다(중앙값 = 그 금액). */
    private List<MonthlyCategorySpendRawResponse> flatCategory(Long categoryId, String name, long monthlyAmount) {
        return List.of(
                spend(categoryId, name, M1, monthlyAmount),
                spend(categoryId, name, M2, monthlyAmount),
                spend(categoryId, name, M3, monthlyAmount));
    }

    /** 실적 무관 RATE 혜택 한 행(후보/보유 공용). */
    private CardRecommendationRawResponse rateCard(Long cardProductId, Long categoryId, String categoryName, long ratePercent) {
        return CardRecommendationRawResponse.builder()
                .cardProductId(cardProductId)
                .cardName("카드 " + cardProductId)
                .categoryId(categoryId)
                .categoryName(categoryName)
                .valueType("RATE")
                .discountRate(BigDecimal.valueOf(ratePercent))
                .minPrevMonthSpend(null)
                .build();
    }

    @Test
    void 전월실적_조건이_없는_혜택은_NPE_없이_추천된다() {
        when(benefitReportMapper.getMonthlyCategorySpends(USER_ID, M1, M3))
                .thenReturn(flatCategory(1L, "카페/디저트", 50000));

        // 실적 무관 혜택: tier가 없어 minPrevMonthSpend가 null로 내려온다
        CardRecommendationRawResponse card = CardRecommendationRawResponse.builder()
                .cardProductId(90L)
                .cardName("무조건 적립 카드")
                .categoryId(1L)
                .categoryName("카페/디저트")
                .valueType("RATE")
                .discountRate(BigDecimal.valueOf(1))
                .minPrevMonthSpend(null)
                .build();
        when(benefitReportMapper.getRecommendedCards(USER_ID, List.of(1L))).thenReturn(List.of(card));

        List<CardRecommendationResponse> result = engine.recommend(USER_ID, YEAR_MONTH);

        // 50000 * 1% = 500원
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getExpectedBenefit()).isEqualByComparingTo(BigDecimal.valueOf(500));
    }

    @Test
    void 예상_월_총지출이_전월실적_조건에_못_미치면_해당_카드는_제외된다() {
        when(benefitReportMapper.getMonthlyCategorySpends(USER_ID, M1, M3))
                .thenReturn(flatCategory(1L, "카페/디저트", 100000)); // 예상 월 총지출 10만원

        CardRecommendationRawResponse card = CardRecommendationRawResponse.builder()
                .cardProductId(45L)
                .cardName("카페 라이프 카드")
                .categoryId(1L)
                .categoryName("카페/디저트")
                .valueType("RATE")
                .discountRate(BigDecimal.valueOf(10))
                .minPrevMonthSpend(BigDecimal.valueOf(300000)) // 실적 30만원 필요
                .limitValue(BigDecimal.valueOf(15000))
                .limitBasis("AMOUNT")
                .build();
        when(benefitReportMapper.getRecommendedCards(USER_ID, List.of(1L))).thenReturn(List.of(card));

        List<CardRecommendationResponse> result = engine.recommend(USER_ID, YEAR_MONTH);

        assertThat(result).isEmpty();
    }

    @Test
    void 한도를_초과하면_한도값으로_예상혜택이_고정된다() {
        when(benefitReportMapper.getMonthlyCategorySpends(USER_ID, M1, M3))
                .thenReturn(flatCategory(1L, "카페/디저트", 500000)); // 예상 월 지출 50만원

        CardRecommendationRawResponse card = CardRecommendationRawResponse.builder()
                .cardProductId(45L)
                .cardName("카페 라이프 카드")
                .categoryId(1L)
                .categoryName("카페/디저트")
                .valueType("RATE")
                .discountRate(BigDecimal.valueOf(10))        // 10% → 원래는 5만원
                .minPrevMonthSpend(BigDecimal.valueOf(300000))
                .limitValue(BigDecimal.valueOf(15000))       // 한도 1.5만원 (원화)
                .limitBasis("AMOUNT")
                .build();
        when(benefitReportMapper.getRecommendedCards(USER_ID, List.of(1L))).thenReturn(List.of(card));

        List<CardRecommendationResponse> result = engine.recommend(USER_ID, YEAR_MONTH);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getExpectedBenefit()).isEqualByComparingTo(BigDecimal.valueOf(15000));
    }

    @Test
    void 포인트_적립_혜택은_krw_per_point를_곱해_원화로_환산된다() {
        when(benefitReportMapper.getMonthlyCategorySpends(USER_ID, M1, M3))
                .thenReturn(flatCategory(1L, "마트", 300000));

        // RATE 1% → raw = 300000 * 1 / 100 = 3000 (포인트), krw_per_point 1.5 → 4500원
        CardRecommendationRawResponse card = CardRecommendationRawResponse.builder()
                .cardProductId(50L)
                .cardName("마트 세이브 카드")
                .categoryId(1L)
                .categoryName("마트")
                .valueType("RATE")
                .discountRate(BigDecimal.valueOf(1))
                .pointCurrencyId(10L)
                .krwPerPoint(BigDecimal.valueOf(1.5))
                .minPrevMonthSpend(BigDecimal.valueOf(0))
                .build();
        when(benefitReportMapper.getRecommendedCards(USER_ID, List.of(1L))).thenReturn(List.of(card));

        List<CardRecommendationResponse> result = engine.recommend(USER_ID, YEAR_MONTH);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getExpectedBenefit()).isEqualByComparingTo(BigDecimal.valueOf(4500));
    }

    @Test
    void FIXED_타입은_지출액과_무관하게_value_number_그대로_사용한다() {
        when(benefitReportMapper.getMonthlyCategorySpends(USER_ID, M1, M3))
                .thenReturn(flatCategory(1L, "주유", 50000));

        CardRecommendationRawResponse card = CardRecommendationRawResponse.builder()
                .cardProductId(60L)
                .cardName("주유 적립 카드")
                .categoryId(1L)
                .categoryName("주유")
                .valueType("FIXED")
                .discountRate(BigDecimal.valueOf(80))     // 정액 80 (원화, point_currency_id 없음)
                .minPrevMonthSpend(BigDecimal.valueOf(0))
                .build();
        when(benefitReportMapper.getRecommendedCards(USER_ID, List.of(1L))).thenReturn(List.of(card));

        List<CardRecommendationResponse> result = engine.recommend(USER_ID, YEAR_MONTH);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getExpectedBenefit()).isEqualByComparingTo(BigDecimal.valueOf(80));
    }

    @Test
    void 한도가_POINT_기준이면_krw_per_point로_환산해서_비교한다() {
        when(benefitReportMapper.getMonthlyCategorySpends(USER_ID, M1, M3))
                .thenReturn(flatCategory(1L, "카페/디저트", 1000000));

        // RATE 10% → raw = 100000포인트, krw_per_point 1.0 → 100000원 (한도 전이면)
        // 한도 500포인트, limitBasis POINT → 500 * 1.0 = 500원으로 캡핑
        CardRecommendationRawResponse card = CardRecommendationRawResponse.builder()
                .cardProductId(70L)
                .cardName("포인트 한도 카드")
                .categoryId(1L)
                .categoryName("카페/디저트")
                .valueType("RATE")
                .discountRate(BigDecimal.valueOf(10))
                .pointCurrencyId(10L)
                .krwPerPoint(BigDecimal.valueOf(1.0))
                .minPrevMonthSpend(BigDecimal.valueOf(0))
                .limitValue(BigDecimal.valueOf(500))
                .limitBasis("POINT")
                .build();
        when(benefitReportMapper.getRecommendedCards(USER_ID, List.of(1L))).thenReturn(List.of(card));

        List<CardRecommendationResponse> result = engine.recommend(USER_ID, YEAR_MONTH);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getExpectedBenefit()).isEqualByComparingTo(BigDecimal.valueOf(500));
    }

    @Test
    void 포인트_혜택이어도_한도가_AMOUNT_기준이면_환산없이_그대로_비교한다() {
        when(benefitReportMapper.getMonthlyCategorySpends(USER_ID, M1, M3))
                .thenReturn(flatCategory(1L, "카페/디저트", 1000000));

        // RATE 10% → raw = 100000포인트, krw_per_point 2.0 → 200000원
        // 한도 15000 "원"(AMOUNT 기준) → 포인트 환산 없이 그대로 15000으로 캡핑
        CardRecommendationRawResponse card = CardRecommendationRawResponse.builder()
                .cardProductId(80L)
                .cardName("원화 한도 포인트 카드")
                .categoryId(1L)
                .categoryName("카페/디저트")
                .valueType("RATE")
                .discountRate(BigDecimal.valueOf(10))
                .pointCurrencyId(10L)
                .krwPerPoint(BigDecimal.valueOf(2.0))
                .minPrevMonthSpend(BigDecimal.valueOf(0))
                .limitValue(BigDecimal.valueOf(15000))
                .limitBasis("AMOUNT")
                .build();
        when(benefitReportMapper.getRecommendedCards(USER_ID, List.of(1L))).thenReturn(List.of(card));

        List<CardRecommendationResponse> result = engine.recommend(USER_ID, YEAR_MONTH);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getExpectedBenefit()).isEqualByComparingTo(BigDecimal.valueOf(15000));
    }

    @Test
    void 예상_월_지출은_평균이_아니라_중앙값이라_일회성_과소비에_안_휘둘린다() {
        // 카페: 평소 20만인데 마지막 달에 여행이 겹쳐 220만. 평균이면 ≈87만이지만 중앙값은 20만.
        when(benefitReportMapper.getMonthlyCategorySpends(USER_ID, M1, M3))
                .thenReturn(List.of(
                        spend(1L, "카페/디저트", M1, 200000),
                        spend(1L, "카페/디저트", M2, 200000),
                        spend(1L, "카페/디저트", M3, 2200000)));

        CardRecommendationRawResponse card = CardRecommendationRawResponse.builder()
                .cardProductId(90L)
                .cardName("카페 적립 카드")
                .categoryId(1L)
                .categoryName("카페/디저트")
                .valueType("RATE")
                .discountRate(BigDecimal.valueOf(10))
                .minPrevMonthSpend(null)
                .build();
        when(benefitReportMapper.getRecommendedCards(USER_ID, List.of(1L))).thenReturn(List.of(card));

        List<CardRecommendationResponse> result = engine.recommend(USER_ID, YEAR_MONTH);

        // 중앙값 20만 × 10% = 2만원 (평균 87만이었다면 8.7만이 나왔을 것)
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getExpectedBenefit()).isEqualByComparingTo(BigDecimal.valueOf(20000));
    }

    @Test
    void 거래가_없으면_인기_미보유_카드로_폴백한다() {
        when(benefitReportMapper.getMonthlyCategorySpends(USER_ID, M1, M3)).thenReturn(List.of());
        when(benefitReportMapper.getPopularUnownedCards(USER_ID, 2)).thenReturn(List.of(
                PopularCardRawResponse.builder().cardProductId(11L).cardName("인기 카드 A").cardImageUrl("a.png").build(),
                PopularCardRawResponse.builder().cardProductId(12L).cardName("인기 카드 B").cardImageUrl("b.png").build()));

        List<CardRecommendationResponse> result = engine.recommend(USER_ID, YEAR_MONTH);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CardRecommendationResponse::getCardName)
                .containsExactly("인기 카드 A", "인기 카드 B");
        // 예상 지출이 없어 혜택 금액은 비우고, 인기 기반임을 문구로 알린다
        assertThat(result.get(0).getExpectedBenefit()).isNull();
        assertThat(result.get(0).getDescription()).isEqualTo("많은 분들이 보유한 인기 카드예요.");
        // 후보 조회는 타지 않는다
        verify(benefitReportMapper, never()).getRecommendedCards(anyLong(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void 세_달_중_한_달만_쓴_카테고리는_중앙값이_0이라_제외되어_폴백된다() {
        // 카페는 마지막 달에만 30만 → 시계열 [0, 0, 30만] → 중앙값 0 → 프로필에서 제외 → 콜드스타트
        when(benefitReportMapper.getMonthlyCategorySpends(USER_ID, M1, M3))
                .thenReturn(List.of(spend(1L, "카페/디저트", M3, 300000)));
        when(benefitReportMapper.getPopularUnownedCards(USER_ID, 2)).thenReturn(List.of(
                PopularCardRawResponse.builder().cardProductId(11L).cardName("인기 카드 A").build()));

        List<CardRecommendationResponse> result = engine.recommend(USER_ID, YEAR_MONTH);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCardName()).isEqualTo("인기 카드 A");
        verify(benefitReportMapper, never()).getRecommendedCards(anyLong(), org.mockito.ArgumentMatchers.anyList());
    }

    // ── Phase 2: 보유 카드 대비 증분(한계 혜택) ──────────────────────────────

    @Test
    void 보유_카드가_이미_커버하는_카테고리는_증분만큼만_반영된다() {
        // 카페 예상 월 지출 10만. 보유 카드가 카페 5%(baseline 5000), 후보가 카페 8%(8000).
        // 증분 = 8000 - 5000 = 3000
        when(benefitReportMapper.getMonthlyCategorySpends(USER_ID, M1, M3))
                .thenReturn(flatCategory(1L, "카페/디저트", 100000));
        when(benefitReportMapper.getRecommendedCards(USER_ID, List.of(1L)))
                .thenReturn(List.of(rateCard(90L, 1L, "카페/디저트", 8)));
        when(benefitReportMapper.getOwnedCardBenefits(USER_ID, List.of(1L)))
                .thenReturn(List.of(rateCard(1L, 1L, "카페/디저트", 5)));

        List<CardRecommendationResponse> result = engine.recommend(USER_ID, YEAR_MONTH);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCardProductId()).isEqualTo(90L);
        assertThat(result.get(0).getExpectedBenefit()).isEqualByComparingTo(BigDecimal.valueOf(3000));
    }

    @Test
    void 보유_카드보다_못한_후보는_증분이_0이라_제외된다() {
        // 보유 카페 10%(baseline 10000), 후보 카페 5%(5000) → 증분 max(0, 5000-10000)=0 → 제외
        when(benefitReportMapper.getMonthlyCategorySpends(USER_ID, M1, M3))
                .thenReturn(flatCategory(1L, "카페/디저트", 100000));
        when(benefitReportMapper.getRecommendedCards(USER_ID, List.of(1L)))
                .thenReturn(List.of(rateCard(90L, 1L, "카페/디저트", 5)));
        when(benefitReportMapper.getOwnedCardBenefits(USER_ID, List.of(1L)))
                .thenReturn(List.of(rateCard(1L, 1L, "카페/디저트", 10)));

        List<CardRecommendationResponse> result = engine.recommend(USER_ID, YEAR_MONTH);

        assertThat(result).isEmpty();
    }

    @Test
    void 후보가_보유_카드의_빈틈_카테고리만_채우면_그_부분만_증분에_잡힌다() {
        // 카페·마트 각 10만. 보유는 카페 10%만(카페 baseline 10000, 마트 baseline 0).
        // 후보 X: 카페 10% + 마트 5% → 카페 증분 0, 마트 증분 5000 → 합 5000
        when(benefitReportMapper.getMonthlyCategorySpends(USER_ID, M1, M3))
                .thenReturn(List.of(
                        spend(1L, "카페/디저트", M1, 100000), spend(1L, "카페/디저트", M2, 100000), spend(1L, "카페/디저트", M3, 100000),
                        spend(2L, "마트", M1, 100000), spend(2L, "마트", M2, 100000), spend(2L, "마트", M3, 100000)));
        when(benefitReportMapper.getRecommendedCards(USER_ID, List.of(1L, 2L)))
                .thenReturn(List.of(
                        rateCard(90L, 1L, "카페/디저트", 10),
                        rateCard(90L, 2L, "마트", 5)));
        when(benefitReportMapper.getOwnedCardBenefits(USER_ID, List.of(1L, 2L)))
                .thenReturn(List.of(rateCard(1L, 1L, "카페/디저트", 10)));

        List<CardRecommendationResponse> result = engine.recommend(USER_ID, YEAR_MONTH);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getExpectedBenefit()).isEqualByComparingTo(BigDecimal.valueOf(5000));
    }

    @Test
    void 단독_혜택이_큰_카드보다_빈틈을_채우는_카드가_증분이_커서_먼저_추천된다() {
        // 카페·마트 각 30만. 보유는 카페 10%(카페 baseline 30000, 마트 baseline 0).
        // 후보 C: 카페 15% → 카페 benefit 45000, 증분 45000-30000 = 15000
        // 후보 D: 마트 10% → 마트 benefit 30000, 증분 30000-0    = 30000
        // 단독 금액은 C(45000) > D(30000)이지만 증분은 D(30000) > C(15000) → D가 먼저
        when(benefitReportMapper.getMonthlyCategorySpends(USER_ID, M1, M3))
                .thenReturn(List.of(
                        spend(1L, "카페/디저트", M1, 300000), spend(1L, "카페/디저트", M2, 300000), spend(1L, "카페/디저트", M3, 300000),
                        spend(2L, "마트", M1, 300000), spend(2L, "마트", M2, 300000), spend(2L, "마트", M3, 300000)));
        when(benefitReportMapper.getRecommendedCards(USER_ID, List.of(1L, 2L)))
                .thenReturn(List.of(
                        rateCard(100L, 1L, "카페/디저트", 15),   // C
                        rateCard(200L, 2L, "마트", 10)));         // D
        when(benefitReportMapper.getOwnedCardBenefits(USER_ID, List.of(1L, 2L)))
                .thenReturn(List.of(rateCard(1L, 1L, "카페/디저트", 10)));

        List<CardRecommendationResponse> result = engine.recommend(USER_ID, YEAR_MONTH);

        assertThat(result).extracting(CardRecommendationResponse::getCardProductId)
                .containsExactly(200L, 100L);
        assertThat(result.get(0).getExpectedBenefit()).isEqualByComparingTo(BigDecimal.valueOf(30000));
        assertThat(result.get(1).getExpectedBenefit()).isEqualByComparingTo(BigDecimal.valueOf(15000));
    }

    @Test
    void 실적을_못_채우는_보유_카드는_baseline에_기여하지_않는다() {
        // 예상 월 총지출 10만. 보유 카페 20%인데 실적 30만 필요 → 실적 미달이라 baseline 0.
        // 후보 카페 5%(5000)는 baseline 0 대비 증분 5000 그대로.
        when(benefitReportMapper.getMonthlyCategorySpends(USER_ID, M1, M3))
                .thenReturn(flatCategory(1L, "카페/디저트", 100000));
        when(benefitReportMapper.getRecommendedCards(USER_ID, List.of(1L)))
                .thenReturn(List.of(rateCard(90L, 1L, "카페/디저트", 5)));
        CardRecommendationRawResponse ownedHighTier = CardRecommendationRawResponse.builder()
                .cardProductId(1L).categoryId(1L).categoryName("카페/디저트")
                .valueType("RATE").discountRate(BigDecimal.valueOf(20))
                .minPrevMonthSpend(BigDecimal.valueOf(300000)) // 실적 30만 필요 → 예상 10만이라 미달
                .build();
        when(benefitReportMapper.getOwnedCardBenefits(USER_ID, List.of(1L)))
                .thenReturn(List.of(ownedHighTier));

        List<CardRecommendationResponse> result = engine.recommend(USER_ID, YEAR_MONTH);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getExpectedBenefit()).isEqualByComparingTo(BigDecimal.valueOf(5000));
    }

    // ── 카드 상세 페이지 URL 전달 (#325) ────────────────────────────────────

    @Test
    void 추천_카드는_카드사_상품_상세_URL을_그대로_내려준다() {
        when(benefitReportMapper.getMonthlyCategorySpends(USER_ID, M1, M3))
                .thenReturn(flatCategory(1L, "카페/디저트", 100000));

        CardRecommendationRawResponse card = CardRecommendationRawResponse.builder()
                .cardProductId(54L)
                .cardName("KB국민 톡톡M 카드")
                .cardImageUrl("09290_img.png")
                .detailUrl("https://card.kbcard.com/CRD/DVIEW/HCAMCXPRICAC0076?mainCC=a&cooperationcode=09290")
                .categoryId(1L)
                .categoryName("카페/디저트")
                .valueType("RATE")
                .discountRate(BigDecimal.valueOf(20))
                .minPrevMonthSpend(null)
                .build();
        when(benefitReportMapper.getRecommendedCards(USER_ID, List.of(1L))).thenReturn(List.of(card));

        List<CardRecommendationResponse> result = engine.recommend(USER_ID, YEAR_MONTH);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDetailUrl())
                .isEqualTo("https://card.kbcard.com/CRD/DVIEW/HCAMCXPRICAC0076?mainCC=a&cooperationcode=09290");
    }

    @Test
    void 콜드스타트_폴백에서도_상품_상세_URL이_함께_내려간다() {
        when(benefitReportMapper.getMonthlyCategorySpends(USER_ID, M1, M3)).thenReturn(List.of());
        when(benefitReportMapper.getPopularUnownedCards(USER_ID, 2)).thenReturn(List.of(
                PopularCardRawResponse.builder()
                        .cardProductId(44L).cardName("KB국민 국민행복체크카드")
                        .detailUrl("https://card.kbcard.com/x?cooperationcode=02066").build(),
                // URL이 아직 없는 카드는 null 그대로 나간다 — 읽는 쪽이 "이동할 곳 없음"으로 처리한다
                PopularCardRawResponse.builder()
                        .cardProductId(12L).cardName("URL 없는 카드").build()));

        List<CardRecommendationResponse> result = engine.recommend(USER_ID, YEAR_MONTH);

        assertThat(result).extracting(CardRecommendationResponse::getDetailUrl)
                .containsExactly("https://card.kbcard.com/x?cooperationcode=02066", null);
    }
}
