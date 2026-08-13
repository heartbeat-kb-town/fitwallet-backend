package com.fitwallet.domain.report.service;

import com.fitwallet.domain.report.dto.response.*;
import com.fitwallet.domain.report.mapper.BenefitReportMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultBenefitReportServiceTest {

    @Mock
    private BenefitReportMapper benefitReportMapper;

    @InjectMocks
    private DefaultBenefitReportService benefitReportService;

    @Test
    void 받은_혜택과_놓친_혜택_총액을_함께_조회한다() {
        Long userId = 1L;
        String yearMonth = "2026-04";

        when(benefitReportMapper.getTotalReceivedBenefit(userId, yearMonth))
                .thenReturn(BigDecimal.valueOf(24500));
        when(benefitReportMapper.getTotalMissedBenefit(userId, yearMonth))
                .thenReturn(BigDecimal.valueOf(6200));
        when(benefitReportMapper.getCategoryBenefits(userId, yearMonth))
                .thenReturn(List.of());
        when(benefitReportMapper.getTopSpendingCategories(userId, yearMonth, 2))
                .thenReturn(List.of());

        BenefitSummaryResponse response = benefitReportService.getBenefitSummary(userId, yearMonth);

        assertThat(response.getTotalReceivedBenefit()).isEqualByComparingTo(BigDecimal.valueOf(24500));
        assertThat(response.getTotalMissedBenefit()).isEqualByComparingTo(BigDecimal.valueOf(6200));
    }

    @Test
    void 상위_지출_카테고리가_없으면_추천을_조회하지_않고_빈_목록을_반환한다() {
        Long userId = 1L;
        String yearMonth = "2026-04";

        when(benefitReportMapper.getTotalReceivedBenefit(userId, yearMonth)).thenReturn(BigDecimal.ZERO);
        when(benefitReportMapper.getTotalMissedBenefit(userId, yearMonth)).thenReturn(BigDecimal.ZERO);
        when(benefitReportMapper.getCategoryBenefits(userId, yearMonth)).thenReturn(List.of());
        when(benefitReportMapper.getTopSpendingCategories(userId, yearMonth, 2)).thenReturn(List.of());

        BenefitSummaryResponse response = benefitReportService.getBenefitSummary(userId, yearMonth);

        assertThat(response.getRecommendations()).isEmpty();
        // 빈 categoryIds로 매퍼를 호출하면 IN () 문법 오류가 나므로 아예 호출하지 않아야 한다
        verify(benefitReportMapper, never()).getRecommendedCards(anyLong(), anyList());
    }

    @Test
    void 전월실적_조건이_없는_혜택은_NPE_없이_추천된다() {
        Long userId = 1L;
        String yearMonth = "2026-04";

        CategorySpendResponse category = CategorySpendResponse.builder()
                .categoryId(1L)
                .categoryName("카페/디저트")
                .spendAmount(BigDecimal.valueOf(50000))
                .build();

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

        when(benefitReportMapper.getTotalReceivedBenefit(userId, yearMonth)).thenReturn(BigDecimal.ZERO);
        when(benefitReportMapper.getTotalMissedBenefit(userId, yearMonth)).thenReturn(BigDecimal.ZERO);
        when(benefitReportMapper.getCategoryBenefits(userId, yearMonth)).thenReturn(List.of());
        when(benefitReportMapper.getTopSpendingCategories(userId, yearMonth, 2))
                .thenReturn(List.of(category));
        when(benefitReportMapper.getRecommendedCards(userId, List.of(1L)))
                .thenReturn(List.of(card));

        BenefitSummaryResponse response = benefitReportService.getBenefitSummary(userId, yearMonth);

        // 50000 * 1% = 500원
        assertThat(response.getRecommendations()).hasSize(1);
        assertThat(response.getRecommendations().get(0).getExpectedBenefit())
                .isEqualByComparingTo(BigDecimal.valueOf(500));
    }

    @Test
    void 전월실적_조건을_못_채우면_해당_카드는_추천에서_제외된다() {
        Long userId = 1L;
        String yearMonth = "2026-04";

        CategorySpendResponse category = CategorySpendResponse.builder()
                .categoryId(1L)
                .categoryName("카페/디저트")
                .spendAmount(BigDecimal.valueOf(100000))
                .build();

        CardRecommendationRawResponse card = CardRecommendationRawResponse.builder()
                .cardProductId(45L)
                .cardName("카페 라이프 카드")
                .categoryId(1L)
                .categoryName("카페/디저트")
                .valueType("RATE")
                .discountRate(BigDecimal.valueOf(10))
                .minPrevMonthSpend(BigDecimal.valueOf(300000))
                .limitValue(BigDecimal.valueOf(15000))
                .limitBasis("AMOUNT")
                .build();

        when(benefitReportMapper.getTotalReceivedBenefit(userId, yearMonth)).thenReturn(BigDecimal.ZERO);
        when(benefitReportMapper.getTotalMissedBenefit(userId, yearMonth)).thenReturn(BigDecimal.ZERO);
        when(benefitReportMapper.getCategoryBenefits(userId, yearMonth)).thenReturn(List.of());
        when(benefitReportMapper.getTopSpendingCategories(userId, yearMonth, 2))
                .thenReturn(List.of(category));
        when(benefitReportMapper.getRecommendedCards(userId, List.of(1L)))
                .thenReturn(List.of(card));

        BenefitSummaryResponse response = benefitReportService.getBenefitSummary(userId, yearMonth);

        assertThat(response.getRecommendations()).isEmpty();
    }

    @Test
    void 한도를_초과하면_한도값으로_예상혜택이_고정된다() {
        Long userId = 1L;
        String yearMonth = "2026-04";

        CategorySpendResponse category = CategorySpendResponse.builder()
                .categoryId(1L)
                .categoryName("카페/디저트")
                .spendAmount(BigDecimal.valueOf(500000))   // 지출 50만원
                .build();

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

        when(benefitReportMapper.getTotalReceivedBenefit(userId, yearMonth)).thenReturn(BigDecimal.ZERO);
        when(benefitReportMapper.getTotalMissedBenefit(userId, yearMonth)).thenReturn(BigDecimal.ZERO);
        when(benefitReportMapper.getCategoryBenefits(userId, yearMonth)).thenReturn(List.of());
        when(benefitReportMapper.getTopSpendingCategories(userId, yearMonth, 2))
                .thenReturn(List.of(category));
        when(benefitReportMapper.getRecommendedCards(userId, List.of(1L)))
                .thenReturn(List.of(card));

        BenefitSummaryResponse response = benefitReportService.getBenefitSummary(userId, yearMonth);

        assertThat(response.getRecommendations()).hasSize(1);
        assertThat(response.getRecommendations().get(0).getExpectedBenefit())
                .isEqualByComparingTo(BigDecimal.valueOf(15000));
    }

    @Test
    void 포인트_적립_혜택은_krw_per_point를_곱해_원화로_환산된다() {
        Long userId = 1L;
        String yearMonth = "2026-04";

        CategorySpendResponse category = CategorySpendResponse.builder()
                .categoryId(1L)
                .categoryName("마트")
                .spendAmount(BigDecimal.valueOf(300000))
                .build();

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

        when(benefitReportMapper.getTotalReceivedBenefit(userId, yearMonth)).thenReturn(BigDecimal.ZERO);
        when(benefitReportMapper.getTotalMissedBenefit(userId, yearMonth)).thenReturn(BigDecimal.ZERO);
        when(benefitReportMapper.getCategoryBenefits(userId, yearMonth)).thenReturn(List.of());
        when(benefitReportMapper.getTopSpendingCategories(userId, yearMonth, 2))
                .thenReturn(List.of(category));
        when(benefitReportMapper.getRecommendedCards(userId, List.of(1L)))
                .thenReturn(List.of(card));

        BenefitSummaryResponse response = benefitReportService.getBenefitSummary(userId, yearMonth);

        assertThat(response.getRecommendations()).hasSize(1);
        assertThat(response.getRecommendations().get(0).getExpectedBenefit())
                .isEqualByComparingTo(BigDecimal.valueOf(4500));
    }

    @Test
    void FIXED_타입은_지출액과_무관하게_value_number_그대로_사용한다() {
        Long userId = 1L;
        String yearMonth = "2026-04";

        CategorySpendResponse category = CategorySpendResponse.builder()
                .categoryId(1L)
                .categoryName("주유")
                .spendAmount(BigDecimal.valueOf(50000))   // 얼마를 쓰든
                .build();

        CardRecommendationRawResponse card = CardRecommendationRawResponse.builder()
                .cardProductId(60L)
                .cardName("주유 적립 카드")
                .categoryId(1L)
                .categoryName("주유")
                .valueType("FIXED")
                .discountRate(BigDecimal.valueOf(80))     // 정액 80 (원화, point_currency_id 없음)
                .minPrevMonthSpend(BigDecimal.valueOf(0))
                .build();

        when(benefitReportMapper.getTotalReceivedBenefit(userId, yearMonth)).thenReturn(BigDecimal.ZERO);
        when(benefitReportMapper.getTotalMissedBenefit(userId, yearMonth)).thenReturn(BigDecimal.ZERO);
        when(benefitReportMapper.getCategoryBenefits(userId, yearMonth)).thenReturn(List.of());
        when(benefitReportMapper.getTopSpendingCategories(userId, yearMonth, 2))
                .thenReturn(List.of(category));
        when(benefitReportMapper.getRecommendedCards(userId, List.of(1L)))
                .thenReturn(List.of(card));

        BenefitSummaryResponse response = benefitReportService.getBenefitSummary(userId, yearMonth);

        assertThat(response.getRecommendations()).hasSize(1);
        assertThat(response.getRecommendations().get(0).getExpectedBenefit())
                .isEqualByComparingTo(BigDecimal.valueOf(80));
    }

    @Test
    void 한도가_POINT_기준이면_krw_per_point로_환산해서_비교한다() {
        Long userId = 1L;
        String yearMonth = "2026-04";

        CategorySpendResponse category = CategorySpendResponse.builder()
                .categoryId(1L)
                .categoryName("카페/디저트")
                .spendAmount(BigDecimal.valueOf(1000000))
                .build();

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

        when(benefitReportMapper.getTotalReceivedBenefit(userId, yearMonth)).thenReturn(BigDecimal.ZERO);
        when(benefitReportMapper.getTotalMissedBenefit(userId, yearMonth)).thenReturn(BigDecimal.ZERO);
        when(benefitReportMapper.getCategoryBenefits(userId, yearMonth)).thenReturn(List.of());
        when(benefitReportMapper.getTopSpendingCategories(userId, yearMonth, 2))
                .thenReturn(List.of(category));
        when(benefitReportMapper.getRecommendedCards(userId, List.of(1L)))
                .thenReturn(List.of(card));

        BenefitSummaryResponse response = benefitReportService.getBenefitSummary(userId, yearMonth);

        assertThat(response.getRecommendations()).hasSize(1);
        assertThat(response.getRecommendations().get(0).getExpectedBenefit())
                .isEqualByComparingTo(BigDecimal.valueOf(500));
    }

    @Test
    void 포인트_혜택이어도_한도가_AMOUNT_기준이면_환산없이_그대로_비교한다() {
        Long userId = 1L;
        String yearMonth = "2026-04";

        CategorySpendResponse category = CategorySpendResponse.builder()
                .categoryId(1L)
                .categoryName("카페/디저트")
                .spendAmount(BigDecimal.valueOf(1000000))
                .build();

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

        when(benefitReportMapper.getTotalReceivedBenefit(userId, yearMonth)).thenReturn(BigDecimal.ZERO);
        when(benefitReportMapper.getTotalMissedBenefit(userId, yearMonth)).thenReturn(BigDecimal.ZERO);
        when(benefitReportMapper.getCategoryBenefits(userId, yearMonth)).thenReturn(List.of());
        when(benefitReportMapper.getTopSpendingCategories(userId, yearMonth, 2))
                .thenReturn(List.of(category));
        when(benefitReportMapper.getRecommendedCards(userId, List.of(1L)))
                .thenReturn(List.of(card));

        BenefitSummaryResponse response = benefitReportService.getBenefitSummary(userId, yearMonth);

        assertThat(response.getRecommendations()).hasSize(1);
        assertThat(response.getRecommendations().get(0).getExpectedBenefit())
                .isEqualByComparingTo(BigDecimal.valueOf(15000));
    }
}