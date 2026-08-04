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
        when(benefitReportMapper.getRecommendedCards(userId, List.of()))
                .thenReturn(List.of());

        BenefitSummaryResponse response = benefitReportService.getBenefitSummary(userId, yearMonth);

        assertThat(response.getTotalReceivedBenefit()).isEqualByComparingTo(BigDecimal.valueOf(24500));
        assertThat(response.getTotalMissedBenefit()).isEqualByComparingTo(BigDecimal.valueOf(6200));
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
                .discountRate(BigDecimal.valueOf(10))
                .minPrevMonthSpend(BigDecimal.valueOf(300000))
                .limitValue(BigDecimal.valueOf(15000))
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
                .discountRate(BigDecimal.valueOf(10))        // 10% → 원래는 5만원
                .minPrevMonthSpend(BigDecimal.valueOf(300000))
                .limitValue(BigDecimal.valueOf(15000))       // 한도 1.5만원
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