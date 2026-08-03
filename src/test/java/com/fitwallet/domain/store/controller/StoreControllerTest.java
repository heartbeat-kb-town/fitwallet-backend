package com.fitwallet.domain.store.controller;

import com.fitwallet.domain.store.dto.request.StoreSearchCondition;
import com.fitwallet.domain.store.dto.response.PopularKeywordsResponse;
import com.fitwallet.domain.store.dto.response.StoreKeywordsResponse;
import com.fitwallet.domain.store.dto.response.StoreSearchResponse;
import com.fitwallet.domain.store.service.StoreService;
import com.fitwallet.global.config.AuthInterceptor;
import com.fitwallet.global.config.JwtProvider;
import com.fitwallet.global.config.LoginUserIdArgumentResolver;
import com.fitwallet.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
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
class StoreControllerTest {

    @Mock
    private StoreService storeService;

    @Mock
    private JwtProvider jwtProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StoreController(storeService))
                .setCustomArgumentResolvers(new LoginUserIdArgumentResolver())
                .addInterceptors(new AuthInterceptor(jwtProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 정상_호출이_200과_STORE_SEARCH_FOUND를_반환한다() throws Exception {
        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);
        given(storeService.searchStores(eq(1L), any())).willReturn(
                StoreSearchResponse.builder()
                        .keyword("커피").categoryId(null).radiusMeters(null).stores(List.of())
                        .build());

        mockMvc.perform(get("/api/store/search")
                        .header("Authorization", "Bearer access-token")
                        .param("keyword", "커피")
                        .param("latitude", "37.4979")
                        .param("longitude", "127.0276"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("STORE_SEARCH_FOUND"));
    }

    @Test
    void 쿼리_파라미터가_StoreSearchCondition에_바인딩돼_서비스로_전달된다() throws Exception {
        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);
        given(storeService.searchStores(any(), any())).willReturn(
                StoreSearchResponse.builder().stores(List.of()).build());
        ArgumentCaptor<StoreSearchCondition> captor = ArgumentCaptor.forClass(StoreSearchCondition.class);

        mockMvc.perform(get("/api/store/search")
                        .header("Authorization", "Bearer access-token")
                        .param("categoryId", "2")
                        .param("latitude", "37.4979")
                        .param("longitude", "127.0276")
                        .param("radiusMeters", "1000"))
                .andExpect(status().isOk());

        then(storeService).should().searchStores(eq(1L), captor.capture());
        StoreSearchCondition cond = captor.getValue();
        assertThat(cond.getCategoryId()).isEqualTo(2L);
        assertThat(cond.getLatitude()).isEqualTo(37.4979);
        assertThat(cond.getLongitude()).isEqualTo(127.0276);
        assertThat(cond.getRadiusMeters()).isEqualTo(1000);
    }

    @Test
    void 잘못된_타입의_쿼리_파라미터는_500이_아니라_400을_반환한다() throws Exception {
        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);

        mockMvc.perform(get("/api/store/search")
                        .header("Authorization", "Bearer access-token")
                        .param("keyword", "커피")
                        .param("latitude", "abc")
                        .param("longitude", "127.0276"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        then(storeService).shouldHaveNoInteractions();
    }

    @Test
    void 검색어_조회_정상_호출이_200과_SEARCH_KEYWORDS_FOUND를_반환한다() throws Exception {
        given(jwtProvider.getUserIdFromAccessToken("access-token")).willReturn(1L);
        given(storeService.findKeywords(1L)).willReturn(
                StoreKeywordsResponse.builder()
                        .recent(List.of())
                        .popular(PopularKeywordsResponse.builder().periodDays(7).keywords(List.of()).build())
                        .build());

        mockMvc.perform(get("/api/store/keywords").header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SEARCH_KEYWORDS_FOUND"));
    }
}
