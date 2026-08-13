package com.fitwallet.domain.report.controller;

import com.fitwallet.domain.report.dto.response.*;
import com.fitwallet.domain.report.service.CardBenefitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CardBenefitControllerTest {

    @Mock
    private CardBenefitService cardBenefitService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new CardBenefitController(cardBenefitService)).build();
    }

    @Test
    void 카드별_받은_혜택_상세_조회에_성공하면_200을_반환한다() throws Exception {
        CardBenefitDetailResponse response = CardBenefitDetailResponse.builder()
                .cardName("KB Gold & More")
                .cardImageUrl("https://.../card.png")
                .maskedCardNumber("**** 1234")
                .totalDiscount(BigDecimal.valueOf(12500))
                .totalPoint(BigDecimal.valueOf(4000))
                .totalSpend(BigDecimal.valueOf(950000))
                .categories(List.of())
                .build();

        when(cardBenefitService.getCardBenefitDetail(1L, 1L, "2026-04"))
                .thenReturn(response);

        mockMvc.perform(get("/api/report/benefit/received/cards/1")
                                .param("yearMonth", "2026-04")
                        // @LoginUserId를 어떻게 목킹할지는 팀 인증 처리 방식 확인 필요
                )
                .andExpect(status().isOk());
    }
}