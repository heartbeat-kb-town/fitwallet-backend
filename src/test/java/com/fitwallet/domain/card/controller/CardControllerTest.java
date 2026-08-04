package com.fitwallet.domain.card.controller;

import com.fitwallet.domain.card.dto.CardTransactionSummaryType;
import com.fitwallet.domain.card.dto.request.CardTransactionSearchRequest;
import com.fitwallet.domain.card.dto.request.CardUsageSearchRequest;
import com.fitwallet.domain.card.dto.response.CardTransactionCursorResponse;
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
                        .value("SCHEDULED_PAYMENT"));
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
                        .summaryType(CardTransactionSummaryType.SCHEDULED_PAYMENT)
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
}
