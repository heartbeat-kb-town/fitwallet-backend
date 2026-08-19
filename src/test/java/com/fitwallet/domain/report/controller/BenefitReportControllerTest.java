package com.fitwallet.domain.report.controller;

import com.fitwallet.domain.report.dto.response.BenefitSummaryResponse;
import com.fitwallet.domain.report.service.BenefitReportService;
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

class BenefitReportControllerTest {

    @Mock
    private BenefitReportService benefitReportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new BenefitReportController(benefitReportService)).build();
    }

    @Test
    void 리포트_요약_조회에_성공하면_200을_반환한다() throws Exception {
        when(benefitReportService.getBenefitSummary(1L, "2026-04"))
                .thenReturn(BenefitSummaryResponse.builder()
                        .totalReceivedBenefit(BigDecimal.ZERO)
                        .totalDiscountAmount(BigDecimal.ZERO)
                        .totalPoint(BigDecimal.ZERO)
                        .totalMissedBenefit(BigDecimal.ZERO)
                        .appUnusedAmount(BigDecimal.ZERO)
                        .cardMismatchAmount(BigDecimal.ZERO)
                        .cards(List.of())
                        .recommendations(List.of())
                        .build());

        mockMvc.perform(get("/api/report/benefit/summary")
                                .param("yearMonth", "2026-04")
                        // @LoginUserId 인증 처리 방식에 따라 헤더/설정 추가 필요
                )
                .andExpect(status().isOk());
    }
}