package com.fitwallet.domain.card.controller;

import com.fitwallet.domain.card.dto.CardTransactionSummaryType;
import com.fitwallet.domain.card.dto.CardListSortType;
import com.fitwallet.domain.card.dto.CardType;
import com.fitwallet.domain.card.dto.CardUsagePerformanceStatus;
import com.fitwallet.domain.card.dto.CardUsageTierType;
import com.fitwallet.domain.card.dto.request.CardListSearchRequest;
import com.fitwallet.domain.card.dto.request.CardTransactionSearchRequest;
import com.fitwallet.domain.card.dto.request.CardUsageSearchRequest;
import com.fitwallet.domain.card.dto.response.CardTransactionCursorResponse;
import com.fitwallet.domain.card.dto.response.CardSummaryAmountResponse;
import com.fitwallet.domain.card.dto.response.CardSummaryCardResponse;
import com.fitwallet.domain.card.dto.response.CardSummaryResponse;
import com.fitwallet.domain.card.dto.response.CardSummaryTierResponse;
import com.fitwallet.domain.card.dto.response.CardSummaryUsageResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionDetailResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionSummaryResponse;
import com.fitwallet.domain.card.dto.response.CardUsageDetailResponse;
import com.fitwallet.domain.card.exception.CardErrorCode;
import com.fitwallet.domain.card.service.CardService;
import com.fitwallet.global.config.AuthInterceptor;
import com.fitwallet.global.config.JwtProvider;
import com.fitwallet.global.config.LoginUserIdArgumentResolver;
import com.fitwallet.global.exception.BusinessException;
import com.fitwallet.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 카드 API의 요청 바인딩과 공통 응답 형식을 검증한다. */
@ExtendWith(MockitoExtension.class)
class CardControllerTest {

    @Mock
    private CardService cardService;

    @Mock
    private JwtProvider jwtProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CardController(cardService))
                .setCustomArgumentResolvers(new LoginUserIdArgumentResolver())
                .addInterceptors(new AuthInterceptor(jwtProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 카드별_결제내역_조회는_200과_CARD_TRANSACTIONS_FOUND를_반환한다() throws Exception {
        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);
        given(cardService.getCardTransactions(eq(1L), eq(2L), any()))
                .willReturn(transactionResponse());

        mockMvc.perform(get("/api/card/2/transactions")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("CARD_TRANSACTIONS_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("카드별 결제 내역 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.yearMonth").value("2026-07"))
                .andExpect(jsonPath("$.data.paymentSummary.summaryType")
                        .value("MONTHLY_PAYMENT_AMOUNT"));
    }

    @Test
    void 카드목록의_최근사용순_조건을_서비스로_전달한다() throws Exception {
        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);
        given(cardService.findMyCards(eq(1L), any())).willReturn(List.of());
        ArgumentCaptor<CardListSearchRequest> captor =
                ArgumentCaptor.forClass(CardListSearchRequest.class);

        mockMvc.perform(get("/api/user-cards")
                        .header("Authorization", "Bearer access-token")
                        .param("sort", "RECENTLY_USED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        then(cardService).should().findMyCards(eq(1L), captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(CardListSortType.RECENTLY_USED);
    }

    @Test
    void 내카드_요약조회는_통합응답과_CARD_SUMMARY_FOUND를_반환한다() throws Exception {
        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);
        given(cardService.findCardSummary(1L, 2L)).willReturn(summaryResponse());

        mockMvc.perform(get("/api/card/2/summary")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("CARD_SUMMARY_FOUND"))
                .andExpect(jsonPath("$.message").value("내 카드 요약 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.card.cardId").value(2))
                .andExpect(jsonPath("$.data.card.cardType").value("CREDIT"))
                .andExpect(jsonPath("$.data.amountSummary.creditUsageAmount").value(1240000))
                .andExpect(jsonPath("$.data.amountSummary.asOfDate").value("2026-08-04"))
                .andExpect(jsonPath("$.data.recentTransactions").isArray())
                .andExpect(jsonPath("$.data.usageSummary.tierType").value("MULTIPLE_TIERS"))
                .andExpect(jsonPath("$.data.usageSummary.currentTier.tierName").value("1구간"));
    }

    @Test
    void 조회할수_없는_카드요약은_404_CARD_NOT_FOUND를_반환한다() throws Exception {
        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);
        given(cardService.findCardSummary(1L, 999L))
                .willThrow(new BusinessException(CardErrorCode.CARD_NOT_FOUND));

        mockMvc.perform(get("/api/card/999/summary")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CARD_NOT_FOUND"));
    }

    @Test
    void 쿼리_파라미터를_요청_DTO에_직접_바인딩해_서비스로_전달한다() throws Exception {
        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);
        given(cardService.getCardTransactions(any(), any(), any()))
                .willReturn(transactionResponse());
        ArgumentCaptor<CardTransactionSearchRequest> captor =
                ArgumentCaptor.forClass(CardTransactionSearchRequest.class);

        mockMvc.perform(get("/api/card/2/transactions")
                        .header("Authorization", "Bearer access-token")
                        .param("yearMonth", "2026-06")
                        .param("size", "10")
                        .param("cursor", "YWJj"))
                .andExpect(status().isOk());

        then(cardService).should().getCardTransactions(eq(1L), eq(2L), captor.capture());
        assertThat(captor.getValue().getYearMonth()).isEqualTo("2026-06");
        assertThat(captor.getValue().getSize()).isEqualTo(10);
        assertThat(captor.getValue().getCursor()).isEqualTo("YWJj");
    }

    @Test
    void size가_숫자가_아니면_INVALID_INPUT_VALUE를_반환한다() throws Exception {
        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);

        mockMvc.perform(get("/api/card/2/transactions")
                        .header("Authorization", "Bearer access-token")
                        .param("size", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        then(cardService).shouldHaveNoInteractions();
    }

    @Test
    void 서비스가_CARD_NOT_FOUND를_던지면_404를_반환한다() throws Exception {
        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);
        given(cardService.getCardTransactions(eq(1L), eq(999L), any()))
                .willThrow(new BusinessException(CardErrorCode.CARD_NOT_FOUND));

        mockMvc.perform(get("/api/card/999/transactions")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CARD_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("요청한 카드를 찾을 수 없습니다."));
    }

    @Test
    void 카드이용실적_조회는_200과_CARD_USAGE_FOUND를_반환한다() throws Exception {
        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);
        given(cardService.getCardUsage(eq(1L), eq(5L), any()))
                .willReturn(CardUsageDetailResponse.builder()
                        .yearMonth("2026-07")
                        .availableYearMonths(List.of("2026-07", "2026-06", "2026-05"))
                        .tiers(List.of())
                        .defaultBenefits(List.of())
                        .build());

        mockMvc.perform(get("/api/card/5/usage")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("CARD_USAGE_FOUND"))
                .andExpect(jsonPath("$.message").value("월별 이용 실적 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.yearMonth").value("2026-07"))
                .andExpect(jsonPath("$.data.tiers").isArray())
                .andExpect(jsonPath("$.data.defaultBenefits").isArray());
    }

    @Test
    void 카드이용실적의_yearMonth를_DTO에_직접바인딩해_서비스로_전달한다() throws Exception {
        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);
        given(cardService.getCardUsage(any(), any(), any()))
                .willReturn(CardUsageDetailResponse.builder()
                        .tiers(List.of()).defaultBenefits(List.of()).build());
        ArgumentCaptor<CardUsageSearchRequest> captor =
                ArgumentCaptor.forClass(CardUsageSearchRequest.class);

        mockMvc.perform(get("/api/card/5/usage")
                        .header("Authorization", "Bearer access-token")
                        .param("yearMonth", "2026-06"))
                .andExpect(status().isOk());

        then(cardService).should().getCardUsage(eq(1L), eq(5L), captor.capture());
        assertThat(captor.getValue().getYearMonth()).isEqualTo("2026-06");
    }

    private CardTransactionDetailResponse transactionResponse() {
        return CardTransactionDetailResponse.builder()
                .yearMonth("2026-07")
                .availableYearMonths(List.of("2026-07", "2026-06", "2026-05"))
                .paymentSummary(CardTransactionSummaryResponse.builder()
                        .summaryType(CardTransactionSummaryType.MONTHLY_PAYMENT_AMOUNT)
                        .amount(new BigDecimal("89800.00"))
                        .build())
                .transactions(CardTransactionCursorResponse.builder()
                        .content(List.of())
                        .size(0)
                        .hasNext(false)
                        .nextCursor(null)
                        .build())
                .build();
    }

    private CardSummaryResponse summaryResponse() {
        return CardSummaryResponse.builder()
                .card(CardSummaryCardResponse.builder()
                        .cardId(2L)
                        .cardProductId(47L)
                        .cardName("테스트 카드")
                        .issuerName("테스트 카드사")
                        .cardType(CardType.CREDIT)
                        .build())
                .amountSummary(CardSummaryAmountResponse.builder()
                        .creditUsageAmount(new BigDecimal("1240000.00"))
                        .asOfDate(LocalDate.of(2026, 8, 4))
                        .build())
                .recentTransactions(List.of())
                .usageSummary(CardSummaryUsageResponse.builder()
                        .yearMonth("2026-08")
                        .tierType(CardUsageTierType.MULTIPLE_TIERS)
                        .performanceStatus(CardUsagePerformanceStatus.ACHIEVED)
                        .recognizedAmount(new BigDecimal("500000.00"))
                        .currentTier(CardSummaryTierResponse.builder().tierName("1구간").build())
                        .nextTier(CardSummaryTierResponse.builder().tierName("2구간").build())
                        .amountUntilNextTier(new BigDecimal("500000.00"))
                        .tierProgressRate(new BigDecimal("50.0"))
                        .build())
                .build();
    }
}
