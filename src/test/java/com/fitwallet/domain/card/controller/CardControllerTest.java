package com.fitwallet.domain.card.controller;

import com.fitwallet.domain.card.dto.CardTransactionSummaryType;
import com.fitwallet.domain.card.dto.CardListSortType;
import com.fitwallet.domain.card.dto.CardType;
import com.fitwallet.domain.card.dto.CardUsagePerformanceStatus;
import com.fitwallet.domain.card.dto.CardUsageTierType;
import com.fitwallet.domain.card.dto.request.CardDisplayOrderUpdateRequest;
import com.fitwallet.domain.card.dto.request.CardListSearchRequest;
import com.fitwallet.domain.card.dto.request.CardTransactionSearchRequest;
import com.fitwallet.domain.card.dto.request.CardUsageSearchRequest;
import com.fitwallet.domain.card.dto.CardEventTargetType;
import com.fitwallet.domain.card.dto.response.CardEventCardResponse;
import com.fitwallet.domain.card.dto.response.CardEventItemResponse;
import com.fitwallet.domain.card.dto.response.CardEventResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionCursorResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitCardResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitPerformanceResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitSummaryResponse;
import com.fitwallet.domain.card.dto.response.CardUsageTierSummaryResponse;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(new CardController(cardService))
                .setCustomArgumentResolvers(new LoginUserIdArgumentResolver())
                .addInterceptors(new AuthInterceptor(jwtProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
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
    void 카드별_월간혜택_조회는_공통응답과_기준일을_반환한다() throws Exception {
        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);
        given(cardService.getCardMonthlyBenefit(1L, 2L)).willReturn(
                CardMonthlyBenefitResponse.builder()
                        .card(CardMonthlyBenefitCardResponse.builder()
                                .userCardId(2L)
                                .cardName("신한카드 All Pass")
                                .issuerName("신한카드")
                                .cardType(CardType.CREDIT)
                                .build())
                        .yearMonth("2026-07")
                        .asOfDate(LocalDate.of(2026, 7, 23))
                        .monthlySummary(CardMonthlyBenefitSummaryResponse.builder()
                                .potentialBenefitAmount(new BigDecimal("4000"))
                                .receivedBenefitAmount(new BigDecimal("1000"))
                                .totalBenefitLimit(new BigDecimal("5000"))
                                .potentialBenefitRate(new BigDecimal("80.0"))
                                .receivedBenefitDetailAvailable(true)
                                .build())
                        .performance(CardMonthlyBenefitPerformanceResponse.builder()
                                .performanceMonth("2026-06")
                                .status(CardUsagePerformanceStatus.ACHIEVED)
                                .currentTier(CardUsageTierSummaryResponse.builder()
                                        .tierOrder(1)
                                        .tierName("1구간")
                                        .minimumAmount(new BigDecimal("300000"))
                                        .build())
                                .message("전월 실적 조건이 적용 중이에요.")
                                .build())
                        .categoryBenefits(List.of())
                        .brandBenefits(List.of())
                        .build());

        mockMvc.perform(get("/api/card/2/benefit")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("CARD_MONTHLY_BENEFIT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("카드 월간 혜택 현황 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.card.userCardId").value(2))
                .andExpect(jsonPath("$.data.yearMonth").value("2026-07"))
                .andExpect(jsonPath("$.data.asOfDate").value("2026-07-23"))
                .andExpect(jsonPath("$.data.monthlySummary.potentialBenefitAmount").value(4000))
                .andExpect(jsonPath("$.data.monthlySummary.potentialBenefitRate").value(80.0))
                .andExpect(jsonPath("$.data.performance.status").value("ACHIEVED"))
                .andExpect(jsonPath("$.data.performance.currentTier.tierName").value("1구간"))
                .andExpect(jsonPath("$.data.categoryBenefits").isArray())
                .andExpect(jsonPath("$.data.brandBenefits").isArray());

        then(cardService).should().getCardMonthlyBenefit(1L, 2L);
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
    void 카드별_이벤트_조회는_응답_계약과_CARD_EVENTS_FOUND를_반환한다() throws Exception {
        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);
        given(cardService.findCardEvents(1L, 2L)).willReturn(
                CardEventResponse.builder()
                        .card(CardEventCardResponse.builder()
                                .cardId(2L)
                                .cardProductId(47L)
                                .cardName("KB국민 청춘대로 톡톡카드")
                                .issuerName("KB국민카드")
                                .build())
                        .eventCount(1)
                        .events(List.of(CardEventItemResponse.builder()
                                .eventId(3L)
                                .targetType(CardEventTargetType.CARD_PRODUCT)
                                .summary("CGV 모바일 예매 시 1인 5,000원 할인(월 2회)")
                                .startsAt(LocalDate.of(2026, 7, 1))
                                .endsAt(LocalDate.of(2026, 7, 31))
                                .daysRemaining(7L)
                                .detailUrl("https://card.kbcard.com/event/3")
                                .detailAvailable(true)
                                .build()))
                        .build());

        mockMvc.perform(get("/api/card/2/event")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("CARD_EVENTS_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("카드별 진행 중 이벤트 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.card.cardId").value(2))
                .andExpect(jsonPath("$.data.card.cardProductId").value(47))
                .andExpect(jsonPath("$.data.eventCount").value(1))
                .andExpect(jsonPath("$.data.events[0].eventId").value(3))
                .andExpect(jsonPath("$.data.events[0].targetType").value("CARD_PRODUCT"))
                .andExpect(jsonPath("$.data.events[0].startsAt").value("2026-07-01"))
                .andExpect(jsonPath("$.data.events[0].daysRemaining").value(7))
                .andExpect(jsonPath("$.data.events[0].detailAvailable").value(true));

        then(cardService).should().findCardEvents(1L, 2L);
    }

    @Test
    void 카드별_이벤트가_없어도_빈_배열을_포함한_성공응답을_반환한다() throws Exception {
        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);
        given(cardService.findCardEvents(1L, 2L)).willReturn(
                CardEventResponse.builder()
                        .card(CardEventCardResponse.builder().cardId(2L).build())
                        .eventCount(0)
                        .events(List.of())
                        .build());

        mockMvc.perform(get("/api/card/2/event")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CARD_EVENTS_FOUND"))
                .andExpect(jsonPath("$.data.eventCount").value(0))
                .andExpect(jsonPath("$.data.events").isArray())
                .andExpect(jsonPath("$.data.events").isEmpty());
    }

    @Test
    void 카드별_이벤트_조회에서_카드를_찾지_못하면_404와_CARD_NOT_FOUND를_반환한다() throws Exception {
        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);
        given(cardService.findCardEvents(1L, 999L))
                .willThrow(new BusinessException(CardErrorCode.CARD_NOT_FOUND));

        mockMvc.perform(get("/api/card/999/event")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CARD_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("요청한 카드를 찾을 수 없습니다."));
    }

    @Test
    void 카드별_이벤트_데이터가_잘못되면_500과_INVALID_CARD_EVENT_DATA를_반환한다() throws Exception {
        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);
        given(cardService.findCardEvents(1L, 2L))
                .willThrow(new BusinessException(CardErrorCode.INVALID_CARD_EVENT_DATA));

        mockMvc.perform(get("/api/card/2/event")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_CARD_EVENT_DATA"))
                .andExpect(jsonPath("$.message")
                        .value("카드 이벤트 데이터가 올바르지 않습니다."));
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

    @Test
    void 카드_순서_변경은_요청_바디를_서비스에_전달하고_200과_CARD_DISPLAY_ORDER_UPDATED를_반환한다() throws Exception {
        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);

        mockMvc.perform(patch("/api/user-cards/display-order")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userCardIds\":[3,1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("CARD_DISPLAY_ORDER_UPDATED"))
                .andExpect(jsonPath("$.message").value("카드 표시 순서를 수정했습니다."));

        ArgumentCaptor<CardDisplayOrderUpdateRequest> captor =
                ArgumentCaptor.forClass(CardDisplayOrderUpdateRequest.class);
        then(cardService).should().updateCardsDisplayOrder(eq(1L), captor.capture());
        assertThat(captor.getValue().getUserCardIds()).containsExactly(3L, 1L, 2L);
    }

    @Test
    void 카드_순서_변경시_userCardIds가_없으면_400과_INVALID_INPUT_VALUE를_반환한다() throws Exception {
        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);

        mockMvc.perform(patch("/api/user-cards/display-order")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        then(cardService).shouldHaveNoInteractions();
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
