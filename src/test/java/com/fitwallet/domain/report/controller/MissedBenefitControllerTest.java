package com.fitwallet.domain.report.controller;

import com.fitwallet.domain.report.dto.LossType;
import com.fitwallet.domain.report.dto.response.MissedCategoryDetailResponse;
import com.fitwallet.domain.report.service.MissedBenefitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MissedBenefitControllerTest {

    @Mock
    private MissedBenefitService missedBenefitService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new MissedBenefitController(missedBenefitService)).build();
    }

    @Test
    void 놓친혜택_리포트_조회에_성공하면_200을_반환한다() throws Exception {
        MissedCategoryDetailResponse response = MissedCategoryDetailResponse.builder()
                .totalMissedBenefit(BigDecimal.valueOf(36451))
                .appUnusedAmount(BigDecimal.valueOf(16132))
                .cardMismatchAmount(BigDecimal.valueOf(20319))
                .lossType("APP_UNUSED")
                .categories(List.of())
                .build();

        when(missedBenefitService.getMissedBenefitDetail(any(), eq("2026-07"), eq(LossType.APP_UNUSED)))
                .thenReturn(response);

        mockMvc.perform(get("/api/report/benefit/missed")
                                .param("yearMonth", "2026-07")
                                .param("lossType", "APP_UNUSED"))
                .andExpect(status().isOk());
    }

    @Test
    void 잘못된_lossType이면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/report/benefit/missed")
                                .param("yearMonth", "2026-07")
                                .param("lossType", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }
}
