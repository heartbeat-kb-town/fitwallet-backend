package com.fitwallet.domain.report.service;

import com.fitwallet.domain.card.dto.response.CardListResponse;
import com.fitwallet.domain.card.service.CardService;
import com.fitwallet.domain.report.dto.response.*;
import com.fitwallet.domain.report.mapper.BenefitReportMapper;
import com.fitwallet.domain.report.mapper.MissedBenefitMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultBenefitReportServiceTest {

    @Mock
    private BenefitReportMapper benefitReportMapper;

    @Mock
    private MissedBenefitMapper missedBenefitMapper;

    @Mock
    private CardService cardService;

    @Mock
    private CardRecommendationEngine cardRecommendationEngine;

    @InjectMocks
    private DefaultBenefitReportService benefitReportService;

    @Test
    void 받은_혜택과_놓친_혜택_요약을_카드목록_및_추천과_함께_조립한다() {
        Long userId = 1L;
        String yearMonth = "2026-07";

        when(benefitReportMapper.getReceivedBenefitSummary(userId, yearMonth))
                .thenReturn(ReceivedBenefitSummaryResponse.builder()
                        .totalReceivedBenefit(BigDecimal.valueOf(36451))
                        .totalDiscountAmount(BigDecimal.valueOf(16132))
                        .totalPoint(BigDecimal.valueOf(20319))
                        .build());
        when(missedBenefitMapper.getMissedSummary(userId, yearMonth))
                .thenReturn(MissedSummaryResponse.builder()
                        .appUnusedAmount(BigDecimal.valueOf(16132))
                        .cardMismatchAmount(BigDecimal.valueOf(20319))
                        .build());
        List<CardListResponse> cards = List.of(
                CardListResponse.builder().userCardId(1L).cardName("KB Gold & More").build());
        when(cardService.findMyCards(anyLong(), any())).thenReturn(cards);
        List<CardRecommendationResponse> recommendations = List.of(
                CardRecommendationResponse.builder().cardProductId(45L).cardName("카페 라이프 카드").build());
        when(cardRecommendationEngine.recommend(userId, yearMonth)).thenReturn(recommendations);

        BenefitSummaryResponse response = benefitReportService.getBenefitSummary(userId, yearMonth);

        assertThat(response.getTotalReceivedBenefit()).isEqualByComparingTo(BigDecimal.valueOf(36451));
        assertThat(response.getTotalDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(16132));
        assertThat(response.getTotalPoint()).isEqualByComparingTo(BigDecimal.valueOf(20319));
        // 총 놓친 혜택은 앱 미사용 손실 + 카드 선택 손실의 합이다
        assertThat(response.getTotalMissedBenefit()).isEqualByComparingTo(BigDecimal.valueOf(36451));
        assertThat(response.getAppUnusedAmount()).isEqualByComparingTo(BigDecimal.valueOf(16132));
        assertThat(response.getCardMismatchAmount()).isEqualByComparingTo(BigDecimal.valueOf(20319));
        assertThat(response.getCards()).isEqualTo(cards);
        // 추천은 전용 엔진 결과를 그대로 담는다
        assertThat(response.getRecommendations()).isEqualTo(recommendations);
    }

    @Test
    void 추천이_없으면_빈_목록을_그대로_담는다() {
        Long userId = 1L;
        String yearMonth = "2026-04";

        when(benefitReportMapper.getReceivedBenefitSummary(anyLong(), anyString()))
                .thenReturn(ReceivedBenefitSummaryResponse.builder()
                        .totalReceivedBenefit(BigDecimal.ZERO)
                        .totalDiscountAmount(BigDecimal.ZERO)
                        .totalPoint(BigDecimal.ZERO)
                        .build());
        when(missedBenefitMapper.getMissedSummary(anyLong(), anyString()))
                .thenReturn(MissedSummaryResponse.builder()
                        .appUnusedAmount(BigDecimal.ZERO)
                        .cardMismatchAmount(BigDecimal.ZERO)
                        .build());
        when(cardService.findMyCards(anyLong(), any())).thenReturn(List.of());
        when(cardRecommendationEngine.recommend(userId, yearMonth)).thenReturn(List.of());

        BenefitSummaryResponse response = benefitReportService.getBenefitSummary(userId, yearMonth);

        assertThat(response.getRecommendations()).isEmpty();
    }
}
