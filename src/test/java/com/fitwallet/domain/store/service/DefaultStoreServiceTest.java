package com.fitwallet.domain.store.service;

import com.fitwallet.domain.store.dto.request.StoreSearchCondition;
import com.fitwallet.domain.store.dto.response.PopularKeywordResponse;
import com.fitwallet.domain.store.dto.response.RecentKeywordResponse;
import com.fitwallet.domain.store.dto.response.StoreCategoryResponse;
import com.fitwallet.domain.store.dto.response.StoreKeywordsResponse;
import com.fitwallet.domain.store.dto.response.StoreSearchResponse;
import com.fitwallet.domain.store.dto.response.StoreSummaryResponse;
import com.fitwallet.domain.store.exception.StoreErrorCode;
import com.fitwallet.domain.store.mapper.StoreMapper;
import com.fitwallet.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

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
    void 키워드와_카테고리가_모두_없어도_주변_조회_모드로_조회된다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).build();
        given(storeMapper.findLocationAgreed(1L)).willReturn(true);
        List<StoreSummaryResponse> expected = List.of(store(1L));
        given(storeMapper.findStores(any())).willReturn(expected);
        ArgumentCaptor<StoreSearchCondition> captor = ArgumentCaptor.forClass(StoreSearchCondition.class);

        StoreSearchResponse response = storeService.searchStores(1L, cond);

        // 스텁이 매번 1건만 주므로 계단이 끝까지 간다. 확정 반경은 마지막 단계에 나타난다.
        then(storeMapper).should(times(3)).findStores(captor.capture());
        assertThat(captor.getValue().getKeyword()).isNull();
        assertThat(captor.getValue().getCategoryId()).isNull();
        assertThat(captor.getValue().getRadiusMeters()).isEqualTo(3000);
        assertThat(response.getStores()).isEqualTo(expected);
    }

    @Test
    void 좌표만_있으면_카테고리_확인도_검색어_기록도_하지_않는다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).build();
        given(storeMapper.findLocationAgreed(1L)).willReturn(true);
        given(storeMapper.findStores(any())).willReturn(List.of());

        storeService.searchStores(1L, cond);

        then(storeMapper).should(never()).existsCategory(any());
        then(searchHistoryService).should(never()).record(any(), anyString());
    }

    @Test
    void 키워드가_공백뿐이고_카테고리도_없으면_미전달로_취급해_주변_조회_모드가_된다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).keyword("   ").build();
        given(storeMapper.findLocationAgreed(1L)).willReturn(true);
        given(storeMapper.findStores(any())).willReturn(List.of());

        StoreSearchResponse response = storeService.searchStores(1L, cond);

        assertThat(response.getKeyword()).isNull();
        assertThat(response.getRadiusMeters()).isEqualTo(3000);
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

        then(storeMapper).should(times(3)).findStores(captor.capture());
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

    @Test
    void recent와_popular이_모두_있으면_그대로_조립된_응답을_반환한다() {
        List<RecentKeywordResponse> recent = List.of(
                RecentKeywordResponse.builder().searchHistoryId(1L).keyword("스타벅스").build());
        List<PopularKeywordResponse> popularKeywords = List.of(
                PopularKeywordResponse.builder().rank(1).keyword("다이소").searchCount(10L).build());
        given(storeMapper.findRecentKeywords(1L)).willReturn(recent);
        given(storeMapper.findPopularKeywords()).willReturn(popularKeywords);

        StoreKeywordsResponse response = storeService.findKeywords(1L);

        assertThat(response.getRecent()).isEqualTo(recent);
        assertThat(response.getPopular().getKeywords()).isEqualTo(popularKeywords);
    }

    @Test
    void recent가_비어도_빈_배열로_채워진다() {
        given(storeMapper.findRecentKeywords(1L)).willReturn(List.of());
        given(storeMapper.findPopularKeywords()).willReturn(List.of());

        StoreKeywordsResponse response = storeService.findKeywords(1L);

        assertThat(response.getRecent()).isEmpty();
    }

    @Test
    void popular_keywords가_비어도_periodDays는_7이다() {
        given(storeMapper.findRecentKeywords(1L)).willReturn(List.of());
        given(storeMapper.findPopularKeywords()).willReturn(List.of());

        StoreKeywordsResponse response = storeService.findKeywords(1L);

        assertThat(response.getPopular().getPeriodDays()).isEqualTo(7);
        assertThat(response.getPopular().getKeywords()).isEmpty();
    }

    @Test
    void 매퍼_메서드가_각각_한_번씩만_호출된다() {
        given(storeMapper.findRecentKeywords(1L)).willReturn(List.of());
        given(storeMapper.findPopularKeywords()).willReturn(List.of());

        storeService.findKeywords(1L);

        then(storeMapper).should().findRecentKeywords(1L);
        then(storeMapper).should().findPopularKeywords();
    }

    @Test
    void 인기_검색어_재집계는_지운_뒤_넣는다() {
        // 순서가 계약이다. INSERT가 먼저면 rank PK 충돌로 실패한다.
        given(storeMapper.insertPopularKeywords()).willReturn(5);

        storeService.refreshPopularKeywords();

        InOrder inOrder = inOrder(storeMapper);
        then(storeMapper).should(inOrder).deletePopularKeywords();
        then(storeMapper).should(inOrder).insertPopularKeywords();
    }

    @Test
    void 인기_검색어_재집계가_실패하면_예외를_다시_던진다() {
        // 삼키면 @Transactional 프록시가 예외를 못 봐서 DELETE만 커밋된다 —
        // 인기 검색어가 통째로 빈 채로 남는다. 다시 던져야 롤백돼 이전 집계가 살아남는다.
        given(storeMapper.insertPopularKeywords())
                .willThrow(new DataIntegrityViolationException("집계 실패"));

        assertThatThrownBy(() -> storeService.refreshPopularKeywords())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 인기_검색어_재집계는_조회_매퍼를_부르지_않는다() {
        // 갱신과 조회는 완전히 분리돼 있다. 갱신이 조회를 부르면 스케줄러가 요청 경로의
        // 캐시를 건드리는 셈이 되고, 이 API를 사전 집계로 옮긴 이유가 무너진다.
        given(storeMapper.insertPopularKeywords()).willReturn(5);

        storeService.refreshPopularKeywords();

        then(storeMapper).should(never()).findPopularKeywords();
        then(storeMapper).should(never()).findRecentKeywords(anyLong());
    }

    @Test
    void 검색_기록_삭제시_매퍼가_1을_반환하면_예외_없이_끝난다() {
        given(storeMapper.deleteSearchHistory(10L, 1L)).willReturn(1);

        storeService.deleteKeyword(1L, 10L);

        then(storeMapper).should().deleteSearchHistory(10L, 1L);
    }

    @Test
    void 검색_기록_삭제시_매퍼가_0을_반환하면_SEARCH_HISTORY_NOT_FOUND_예외를_던진다() {
        given(storeMapper.deleteSearchHistory(10L, 1L)).willReturn(0);

        assertThatThrownBy(() -> storeService.deleteKeyword(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.SEARCH_HISTORY_NOT_FOUND);
    }

    @Test
    void 검색_기록_전체_삭제는_매퍼를_한_번_호출하고_예외가_없다() {
        storeService.deleteAllKeywords(1L);

        then(storeMapper).should().deleteAllSearchHistory(1L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 계단식 반경
    //
    // 계단식은 "같은 답을 더 싸게" 얻으려는 최적화다. 그래서 여기 테스트가 확인하는 것은
    // 속도가 아니라 계약이다 — 요청한 반경을 넘겨 뒤지지 않는가, 조기 종료해도 답이 같은가.
    // 실제 소요 시간은 272만 행 perf DB에서만 의미가 있어 단위 테스트로 재지 않는다.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void 주변_조회_모드는_300m_1km_확정반경_순으로_넓혀가며_조회한다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).build();
        given(storeMapper.findLocationAgreed(1L)).willReturn(true);
        given(storeMapper.findStores(any())).willReturn(List.of());
        ArgumentCaptor<StoreSearchCondition> captor = ArgumentCaptor.forClass(StoreSearchCondition.class);

        storeService.searchStores(1L, cond);

        then(storeMapper).should(times(3)).findStores(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(StoreSearchCondition::getRadiusMeters)
                .containsExactly(300, 1000, 3000);
    }

    @Test
    void 좁은_단계에서_다섯건이_차면_더_넓히지_않는다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).build();
        given(storeMapper.findLocationAgreed(1L)).willReturn(true);
        given(storeMapper.findStores(any())).willReturn(storesAtDistances(50, 60, 70, 80, 90));

        storeService.searchStores(1L, cond);

        then(storeMapper).should(times(1)).findStores(any());
    }

    @Test
    void 다섯번째_거리가_단계_반경과_같으면_조기_종료하지_않는다() {
        // 5번째가 단계 반경에 정확히 걸치면 조기 종료하지 않는다. 사각형과 HAVING의 경계가
        // 어긋나면 그 행과 거리가 같은 다른 가맹점이 넓은 단계에서만 살아나 store_rank·store_name
        // 타이브레이크로 5번째를 밀어낼 수 있기 때문이다(매퍼 주석의 +1m 여유 참고).
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).build();
        given(storeMapper.findLocationAgreed(1L)).willReturn(true);
        given(storeMapper.findStores(any())).willReturn(storesAtDistances(50, 60, 70, 80, 300));
        ArgumentCaptor<StoreSearchCondition> captor = ArgumentCaptor.forClass(StoreSearchCondition.class);

        storeService.searchStores(1L, cond);

        // 300m에서 멈추지 않고 1km로 넓혀 다시 확인한다. 1km에서는 5번째(300m)가 경계에
        // 걸치지 않으므로 거기서 끝난다.
        then(storeMapper).should(times(2)).findStores(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(StoreSearchCondition::getRadiusMeters)
                .containsExactly(300, 1000);
    }

    @Test
    void 요청_반경이_첫_단계보다_좁으면_그_반경_하나만_조회한다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).radiusMeters(100).build();
        given(storeMapper.findLocationAgreed(1L)).willReturn(true);
        given(storeMapper.findStores(any())).willReturn(List.of());
        ArgumentCaptor<StoreSearchCondition> captor = ArgumentCaptor.forClass(StoreSearchCondition.class);

        storeService.searchStores(1L, cond);

        // 클램프가 없으면 [300, 1000, 100]을 돌아 100m 밖 가맹점이 응답에 실린다.
        then(storeMapper).should(times(1)).findStores(captor.capture());
        assertThat(captor.getValue().getRadiusMeters()).isEqualTo(100);
    }

    @Test
    void 요청_반경이_중간_단계와_겹치면_같은_반경을_두_번_조회하지_않는다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).radiusMeters(1000).build();
        given(storeMapper.findLocationAgreed(1L)).willReturn(true);
        given(storeMapper.findStores(any())).willReturn(List.of());
        ArgumentCaptor<StoreSearchCondition> captor = ArgumentCaptor.forClass(StoreSearchCondition.class);

        storeService.searchStores(1L, cond);

        then(storeMapper).should(times(2)).findStores(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(StoreSearchCondition::getRadiusMeters)
                .containsExactly(300, 1000);
    }

    @Test
    void 어떤_단계에서도_요청_반경을_넘는_조회를_하지_않는다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).radiusMeters(500).build();
        given(storeMapper.findLocationAgreed(1L)).willReturn(true);
        given(storeMapper.findStores(any())).willReturn(List.of());
        ArgumentCaptor<StoreSearchCondition> captor = ArgumentCaptor.forClass(StoreSearchCondition.class);

        storeService.searchStores(1L, cond);

        then(storeMapper).should(times(2)).findStores(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(StoreSearchCondition::getRadiusMeters)
                .containsExactly(300, 500)
                .allSatisfy(radius -> assertThat(radius).isLessThanOrEqualTo(500));
    }

    @Test
    void 최대_반경에서도_다섯건을_못_채우면_마지막_단계의_결과를_그대로_반환한다() {
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).build();
        given(storeMapper.findLocationAgreed(1L)).willReturn(true);
        List<StoreSummaryResponse> widest = storesAtDistances(2500, 2800);
        given(storeMapper.findStores(any()))
                .willReturn(List.of())
                .willReturn(storesAtDistances(900))
                .willReturn(widest);

        StoreSearchResponse response = storeService.searchStores(1L, cond);

        assertThat(response.getStores()).isEqualTo(widest);
    }

    @Test
    void 키워드_모드에서는_계단식을_쓰지_않고_한_번만_조회한다() {
        // 검색어가 있으면 LIKE가 먼저 대부분을 쳐내므로 단계를 늘리는 만큼 그대로 손해다.
        StoreSearchCondition cond = StoreSearchCondition.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).keyword("스타벅스").radiusMeters(3000).build();
        given(storeMapper.findLocationAgreed(1L)).willReturn(true);
        given(storeMapper.findStores(any())).willReturn(List.of());
        ArgumentCaptor<StoreSearchCondition> captor = ArgumentCaptor.forClass(StoreSearchCondition.class);

        storeService.searchStores(1L, cond);

        then(storeMapper).should(times(1)).findStores(captor.capture());
        assertThat(captor.getValue().getRadiusMeters()).isEqualTo(3000);
    }

    /** 거리만 다른 결과 목록. 계단식의 종료 판단이 5번째 거리를 보므로 그 값만 의미가 있다. */
    private List<StoreSummaryResponse> storesAtDistances(int... distanceMeters) {
        return IntStream.range(0, distanceMeters.length)
                .mapToObj(i -> StoreSummaryResponse.builder()
                        .storeId((long) (i + 1))
                        .storeName("가맹점 " + (i + 1))
                        .distanceMeters(distanceMeters[i])
                        .build())
                .collect(Collectors.toList());
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
