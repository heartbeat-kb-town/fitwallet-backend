package com.fitwallet.domain.benefit.controller;

import com.fitwallet.domain.benefit.dto.response.ExpectedBenefitResponse;
import com.fitwallet.domain.benefit.dto.response.ExpectedBenefitStoreResponse;
import com.fitwallet.domain.benefit.exception.BenefitErrorCode;
import com.fitwallet.domain.benefit.service.BenefitService;
import com.fitwallet.global.config.AuthInterceptor;
import com.fitwallet.global.config.JwtProvider;
import com.fitwallet.global.config.LoginUserIdArgumentResolver;
import com.fitwallet.global.exception.BusinessException;
import com.fitwallet.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc {@code standaloneSetup} + Mock Service(§11 "선택" 계층).
 * <p>
 * {@code @LoginUserId}가 실제로 값을 채우려면 {@link AuthInterceptor}가 먼저 요청 속성을 채워야
 * 하므로, standalone 빌더에 커스텀 인자 리졸버와 함께 인터셉터도 등록한다.
 */
@ExtendWith(MockitoExtension.class)
class BenefitControllerTest {

    @Mock
    private BenefitService benefitService;

    @Mock
    private JwtProvider jwtProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BenefitController(benefitService))
                .setCustomArgumentResolvers(new LoginUserIdArgumentResolver())
                .addInterceptors(new AuthInterceptor(jwtProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 정상_호출이_200과_EXPECTED_BENEFIT_FOUND를_반환한다() throws Exception {
        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);
        given(benefitService.findExpectedBenefits(eq(1L), any(), any())).willReturn(
                ExpectedBenefitResponse.builder()
                        .store(ExpectedBenefitStoreResponse.builder()
                                .storeId(3011L).storeName("스타벅스 강남점").build())
                        .hasCard(false)
                        .cards(List.of())
                        .build());

        mockMvc.perform(get("/api/benefit/expected")
                        .header("Authorization", "Bearer access-token")
                        .param("storeId", "3011"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("EXPECTED_BENEFIT_FOUND"))
                .andExpect(jsonPath("$.data.store.storeId").value(3011))
                .andExpect(jsonPath("$.data.hasCard").value(false));
    }

    /**
     * {@code storeId}는 컨트롤러가 파싱하지 않는다(§클래스 Javadoc). 숫자가 아닌 값이 와도
     * 타입 변환 없이 문자열 그대로 서비스에 도착하는지가 이 API 에러 스펙의 전제다.
     */
    @Test
    void storeId_쿼리_파라미터가_가공_없이_서비스로_전달된다() throws Exception {
        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);
        given(benefitService.findExpectedBenefits(any(), any(), any())).willReturn(
                ExpectedBenefitResponse.builder().hasCard(false).cards(List.of()).build());

        mockMvc.perform(get("/api/benefit/expected")
                        .header("Authorization", "Bearer access-token")
                        .param("storeId", "abc"))
                .andExpect(status().isOk());

        then(benefitService).should().findExpectedBenefits(eq(1L), eq("abc"), isNull());
    }

    /** {@code amount}도 {@code storeId}와 같은 이유로 컨트롤러가 파싱하지 않는다. */
    @Test
    void amount_쿼리_파라미터가_가공_없이_서비스로_전달된다() throws Exception {
        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);
        given(benefitService.findExpectedBenefits(any(), any(), any())).willReturn(
                ExpectedBenefitResponse.builder().hasCard(false).cards(List.of()).build());

        mockMvc.perform(get("/api/benefit/expected")
                        .header("Authorization", "Bearer access-token")
                        .param("storeId", "1")
                        .param("amount", "abc"))
                .andExpect(status().isOk());

        then(benefitService).should().findExpectedBenefits(eq(1L), eq("1"), eq("abc"));
    }

    @Test
    void amount가_잘못되면_서비스가_던진_400_AMOUNT_INVALID가_그대로_나간다() throws Exception {
        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);
        willThrow(new BusinessException(BenefitErrorCode.AMOUNT_INVALID))
                .given(benefitService).findExpectedBenefits(eq(1L), eq("1"), eq("0"));

        mockMvc.perform(get("/api/benefit/expected")
                        .header("Authorization", "Bearer access-token")
                        .param("storeId", "1")
                        .param("amount", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AMOUNT_INVALID"))
                .andExpect(jsonPath("$.message").value("결제 금액은 0보다 큰 숫자로 입력해 주세요."));
    }

    @Test
    void storeId가_없으면_500이_아니라_서비스가_던진_400이_그대로_나간다() throws Exception {
        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);
        willThrow(new BusinessException(BenefitErrorCode.STORE_ID_REQUIRED))
                .given(benefitService).findExpectedBenefits(eq(1L), isNull(), isNull());

        mockMvc.perform(get("/api/benefit/expected")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("STORE_ID_REQUIRED"))
                .andExpect(jsonPath("$.data").doesNotExist());

        then(benefitService).should().findExpectedBenefits(eq(1L), isNull(), isNull());
    }
}
