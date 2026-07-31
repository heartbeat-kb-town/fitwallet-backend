package com.fitwallet.domain.store.service;

import com.fitwallet.domain.store.dto.request.StoreSearchCondition;
import com.fitwallet.domain.store.dto.response.StoreCategoryResponse;
import com.fitwallet.domain.store.dto.response.StoreSearchResponse;
import com.fitwallet.domain.store.dto.response.StoreSummaryResponse;
import com.fitwallet.domain.store.exception.StoreErrorCode;
import com.fitwallet.domain.store.mapper.StoreMapper;
import com.fitwallet.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

/**
 * Service 단위 테스트. Mapper·SearchHistoryService를 목킹하므로 DB가 필요 없다.
 * <p>
 * {@code @InjectMocks}는 구체 클래스가 있어야 인스턴스를 만들 수 있어
 * 필드 타입을 인터페이스({@code StoreService})가 아니라 구현체로 둔다.
 * <p>
 * {@code StoreSearchCondition}에 {@code @Builder}가 있으므로 픽스처는
 * {@code ReflectionTestUtils} 없이 빌더로 만든다.
 */
@ExtendWith(MockitoExtension.class)
class DefaultStoreServiceTest {

    private static final double LATITUDE = 37.4979;
    private static final double LONGITUDE = 127.0276;

    @Mock
    private StoreMapper storeMapper;

    @Mock
    private SearchHistoryService searchHistoryService;

    @InjectMocks
    private DefaultStoreService storeService;

    @Test
    void 위도와_경도_둘_다_없으면_COORDINATE_PAIR_REQUIRED_예외를_던진다() {
        StoreSearchCondition cond = StoreSearchCondition.builder().keyword("커피").build();

        assertThatThrownBy(() -> storeService.searchStores(1L, cond))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.COORDINATE_PAIR_REQUIRED);
    }

    @Test
    void 위도만_전달되면_COORDINATE_PAIR_REQUIRED_예외를_던진다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).keyword("커피").build();

        assertThatThrownBy(() -> storeService.searchStores(1L, cond))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.COORDINATE_PAIR_REQUIRED);
    }

    @Test
    void 경도만_전달되면_COORDINATE_PAIR_REQUIRED_예외를_던진다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .longitude(LONGITUDE).keyword("커피").build();

        assertThatThrownBy(() -> storeService.searchStores(1L, cond))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.COORDINATE_PAIR_REQUIRED);
    }

    @Test
    void 좌표가_범위를_벗어나면_INVALID_COORDINATE_예외를_던진다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(91.0).longitude(LONGITUDE).keyword("커피").build();

        assertThatThrownBy(() -> storeService.searchStores(1L, cond))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.INVALID_COORDINATE);
    }

    @Test
    void 키워드와_카테고리가_모두_없으면_KEYWORD_OR_CATEGORY_REQUIRED_예외를_던진다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).build();

        assertThatThrownBy(() -> storeService.searchStores(1L, cond))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.KEYWORD_OR_CATEGORY_REQUIRED);
    }

    @Test
    void 키워드가_공백뿐이면_미전달로_취급해_KEYWORD_OR_CATEGORY_REQUIRED_예외를_던진다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).keyword("   ").build();

        assertThatThrownBy(() -> storeService.searchStores(1L, cond))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.KEYWORD_OR_CATEGORY_REQUIRED);
    }

    @Test
    void radiusMeters가_0이하면_INVALID_RADIUS_예외를_던진다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).categoryId(2L).radiusMeters(0).build();

        assertThatThrownBy(() -> storeService.searchStores(1L, cond))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.INVALID_RADIUS);
    }

    @Test
    void 주변_조회_모드에서_반경이_3000_초과면_RADIUS_EXCEEDED_예외를_던진다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).categoryId(2L).radiusMeters(3001).build();

        assertThatThrownBy(() -> storeService.searchStores(1L, cond))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.RADIUS_EXCEEDED);
    }

    @Test
    void 키워드_모드에서_반경이_3000_초과여도_정상_통과한다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).keyword("커피").radiusMeters(3001).build();
        given(storeMapper.findLocationAgreed(1L)).willReturn(true);
        given(storeMapper.findStores(any())).willReturn(List.of());

        StoreSearchResponse response = storeService.searchStores(1L, cond);

        assertThat(response.getRadiusMeters()).isEqualTo(3001);
    }

    @Test
    void 위치_동의가_false이면_LOCATION_AGREEMENT_REQUIRED_예외를_던진다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).keyword("커피").build();
        given(storeMapper.findLocationAgreed(1L)).willReturn(false);

        assertThatThrownBy(() -> storeService.searchStores(1L, cond))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.LOCATION_AGREEMENT_REQUIRED);
    }

    @Test
    void 위치_동의_정보가_없는_사용자면_LOCATION_AGREEMENT_REQUIRED_예외를_던진다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).keyword("커피").build();
        given(storeMapper.findLocationAgreed(999L)).willReturn(null);

        assertThatThrownBy(() -> storeService.searchStores(999L, cond))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.LOCATION_AGREEMENT_REQUIRED);
    }

    @Test
    void 존재하지_않는_카테고리면_CATEGORY_NOT_FOUND_예외를_던진다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).categoryId(999L).build();
        given(storeMapper.findLocationAgreed(1L)).willReturn(true);
        given(storeMapper.existsCategory(999L)).willReturn(false);

        assertThatThrownBy(() -> storeService.searchStores(1L, cond))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    void 키워드_모드에서_반경_미전달시_null이_적용되고_매퍼와_응답_모두_null이다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).keyword("커피").build();
        given(storeMapper.findLocationAgreed(1L)).willReturn(true);
        given(storeMapper.findStores(any())).willReturn(List.of());
        ArgumentCaptor<StoreSearchCondition> captor = ArgumentCaptor.forClass(StoreSearchCondition.class);

        StoreSearchResponse response = storeService.searchStores(1L, cond);

        then(storeMapper).should().findStores(captor.capture());
        assertThat(captor.getValue().getRadiusMeters()).isNull();
        assertThat(response.getRadiusMeters()).isNull();
    }

    @Test
    void 주변_조회_모드에서_반경_미전달시_3000이_적용되고_응답에_에코된다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).categoryId(2L).build();
        given(storeMapper.findLocationAgreed(1L)).willReturn(true);
        given(storeMapper.existsCategory(2L)).willReturn(true);
        given(storeMapper.findStores(any())).willReturn(List.of());
        ArgumentCaptor<StoreSearchCondition> captor = ArgumentCaptor.forClass(StoreSearchCondition.class);

        StoreSearchResponse response = storeService.searchStores(1L, cond);

        then(storeMapper).should().findStores(captor.capture());
        assertThat(captor.getValue().getRadiusMeters()).isEqualTo(3000);
        assertThat(response.getRadiusMeters()).isEqualTo(3000);
    }

    @Test
    void trim한_키워드가_매퍼와_응답에_반영된다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).keyword(" 카페 ").build();
        given(storeMapper.findLocationAgreed(1L)).willReturn(true);
        given(storeMapper.findStores(any())).willReturn(List.of());
        ArgumentCaptor<StoreSearchCondition> captor = ArgumentCaptor.forClass(StoreSearchCondition.class);

        StoreSearchResponse response = storeService.searchStores(1L, cond);

        then(storeMapper).should().findStores(captor.capture());
        assertThat(captor.getValue().getKeyword()).isEqualTo("카페");
        assertThat(response.getKeyword()).isEqualTo("카페");
    }

    @Test
    void 키워드가_있으면_검색어_기록을_호출한다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).keyword("커피").build();
        given(storeMapper.findLocationAgreed(1L)).willReturn(true);
        given(storeMapper.findStores(any())).willReturn(List.of());

        storeService.searchStores(1L, cond);

        then(searchHistoryService).should().record(1L, "커피");
    }

    @Test
    void 카테고리만_있으면_검색어_기록을_호출하지_않는다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).categoryId(2L).build();
        given(storeMapper.findLocationAgreed(1L)).willReturn(true);
        given(storeMapper.existsCategory(2L)).willReturn(true);
        given(storeMapper.findStores(any())).willReturn(List.of());

        storeService.searchStores(1L, cond);

        then(searchHistoryService).should(never()).record(any(), anyString());
    }

    @Test
    void 검색어_기록이_예외를_던져도_조회_결과는_정상_반환된다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).keyword("커피").build();
        given(storeMapper.findLocationAgreed(1L)).willReturn(true);
        List<StoreSummaryResponse> expected = List.of(store(1L));
        given(storeMapper.findStores(any())).willReturn(expected);
        willThrow(new DataIntegrityViolationException("fk 위반"))
                .given(searchHistoryService).record(anyLong(), anyString());

        StoreSearchResponse response = storeService.searchStores(1L, cond);

        assertThat(response.getStores()).isEqualTo(expected);
    }

    @Test
    void 키워드가_101자_이상이면_기록이_실패해도_조회_결과는_정상_반환된다() {
        String longKeyword = "가".repeat(101);
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).keyword(longKeyword).build();
        given(storeMapper.findLocationAgreed(1L)).willReturn(true);
        List<StoreSummaryResponse> expected = List.of(store(1L));
        given(storeMapper.findStores(any())).willReturn(expected);
        // search_history.keyword가 VARCHAR(100)이라 실제로는 DB 에러 1406(Data too long)이 나지만,
        // 서비스 계층에서는 DataIntegrityViolationException으로 관측된다. 명세에 길이 제한이 없어
        // 별도 검증을 추가하지 않고 기존 try-catch로 흡수되는 동작을 고정한다.
        willThrow(new DataIntegrityViolationException("Data too long for column 'keyword'"))
                .given(searchHistoryService).record(anyLong(), anyString());

        StoreSearchResponse response = storeService.searchStores(1L, cond);

        assertThat(response.getKeyword()).isEqualTo(longKeyword);
        assertThat(response.getStores()).isEqualTo(expected);
    }

    @Test
    void 결과가_없으면_예외_없이_빈_목록을_반환한다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).keyword("없는가게").build();
        given(storeMapper.findLocationAgreed(1L)).willReturn(true);
        given(storeMapper.findStores(any())).willReturn(List.of());

        StoreSearchResponse response = storeService.searchStores(1L, cond);

        assertThat(response.getStores()).isEmpty();
    }

    private StoreSummaryResponse store(Long storeId) {
        return StoreSummaryResponse.builder()
                .storeId(storeId)
                .storeName("메가MGC커피 역삼점")
                .address("서울 강남구 테헤란로 123")
                .distanceMeters(152)
                .category(StoreCategoryResponse.builder()
                        .categoryId(1L)
                        .categoryName("카페/디저트")
                        .categoryImageUrl("https://cdn.fitwallet.kr/category/cafe.png")
                        .build())
                .build();
    }
}
