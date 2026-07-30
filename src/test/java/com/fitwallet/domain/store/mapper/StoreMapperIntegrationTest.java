package com.fitwallet.domain.store.mapper;

import com.fitwallet.domain.store.dto.request.StoreSearchCondition;
import com.fitwallet.domain.store.dto.response.StoreCategoryResponse;
import com.fitwallet.domain.store.dto.response.StoreSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 가맹점 Mapper 통합 테스트. docker compose로 띄운 실제 MySQL과 시드 데이터를 사용한다.
 * <p>
 * 클래스 레벨 {@code @Transactional}이 테스트마다 롤백하므로 데이터를 바꾸는 테스트를 써도 된다.
 * <p>
 * ⚠️ 한 테스트 안에서 "조회 → 변경 → 같은 조회"를 하지 않는다. 같은 트랜잭션은 같은 SqlSession이라
 * MyBatis 1차 캐시가 첫 결과를 돌려준다. 데이터를 흔드는 테스트는 <b>변경을 먼저 하고 한 번만 조회</b>한다.
 */
@SpringJUnitConfig(locations = "classpath:root-context.xml")
@Transactional
class StoreMapperIntegrationTest {

    /**
     * 시드 매장이 밀집한 서울 광진구 군자동. 244건이 위도 37.5404~37.5561, 경도 127.0647~127.0805에 있다.
     * 이 좌표에서 가까운 순서는 store 1(7m) → 93·92(둘 다 16m) → 2(17m) → 46(21m)이다.
     */
    private static final double BASE_LATITUDE = 37.5481;
    private static final double BASE_LONGITUDE = 127.0731;

    /** 시드 데모 페르소나. {@code is_location_agreed = 1}. */
    private static final Long SEED_USER_ID = 1L;

    /** 기준 좌표에서 7m. 두 번째로 가까운 매장이 16m라 반경 10m 조회에는 이 한 건만 들어온다. */
    private static final Long NEAREST_STORE_ID = 1L;

    /** 카페/디저트. 시드의 '커피' 매장 7건이 전부 이 카테고리다. */
    private static final Long CAFE_CATEGORY_ID = 1L;
    /** 편의점/마트. 시드에 48건 있다. */
    private static final Long CONVENIENCE_CATEGORY_ID = 2L;

    /**
     * 동일 좌표에 겹쳐 있는 매장 셋. 거리가 전부 0m로 같아 정렬 tie-break를 검증할 수 있다.
     * 이름순으로 써브웨이(103) → 퍼포즈(105) → 프레퍼스(104)이고, 네 번째로 가까운 매장은 2m다.
     */
    private static final double TIE_LATITUDE = 37.54735558;
    private static final double TIE_LONGITUDE = 127.07293687;
    private static final Long TIE_FIRST_BY_NAME = 103L;
    private static final Long TIE_LAST_BY_NAME = 104L;

    /** 시드의 search_history는 16행이고 전부 user_id = 1이다(users 테이블에 1번 한 명뿐). */
    private static final int SEED_SEARCH_HISTORY_COUNT = 16;

    /**
     * 시드에 이미 있는 검색어. searched_at 갱신 검증의 기준값이다.
     * <p>
     * 갱신 여부를 {@code LocalDateTime.now()}와 비교하지 않는 이유는 MySQL 컨테이너가 UTC라
     * {@code NOW()}가 KST보다 9시간 이르기 때문이다. 수개월 전인 이 시드 값과의 비교만 안전하다.
     */
    private static final String SEED_KEYWORD = "스타벅스";
    private static final LocalDateTime SEED_KEYWORD_SEARCHED_AT = LocalDateTime.of(2026, 4, 21, 11, 59, 53);

    @Autowired
    private StoreMapper storeMapper;

    /**
     * 테스트 데이터를 흔들기 위한 용도. DataSource가 트랜잭션에 묶여 있어
     * 여기서 바꾼 값도 테스트 종료 시 함께 롤백된다.
     */
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp(@Autowired DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void 거리가_가까운_순으로_정렬된다() {
        List<StoreSummaryResponse> stores = storeMapper.findStores(condition(null, null, null));

        assertThat(stores)
                .isNotEmpty()
                .isSortedAccordingTo(Comparator.comparing(StoreSummaryResponse::getDistanceMeters));
    }

    @Test
    void 조건에_맞는_매장이_많아도_최대_5건만_반환한다() {
        List<StoreSummaryResponse> stores =
                storeMapper.findStores(condition(null, CONVENIENCE_CATEGORY_ID, null));

        assertThat(stores).hasSize(5);
    }

    @Test
    void 키워드로_상호명을_부분_일치_검색한다() {
        List<StoreSummaryResponse> stores = storeMapper.findStores(condition("커피", null, null));

        assertThat(stores)
                .isNotEmpty()
                .extracting(StoreSummaryResponse::getStoreName)
                .allSatisfy(name -> assertThat(name).contains("커피"));
    }

    @Test
    void 카테고리로_업종을_필터링한다() {
        List<StoreSummaryResponse> stores =
                storeMapper.findStores(condition(null, CONVENIENCE_CATEGORY_ID, null));

        assertThat(stores)
                .isNotEmpty()
                .extracting(StoreSummaryResponse::getCategory)
                .extracting(StoreCategoryResponse::getCategoryId)
                .containsOnly(CONVENIENCE_CATEGORY_ID);
    }

    @Test
    void 키워드와_카테고리를_함께_주면_카테고리_안에서_검색한다() {
        List<StoreSummaryResponse> stores =
                storeMapper.findStores(condition("커피", CAFE_CATEGORY_ID, null));

        assertThat(stores)
                .isNotEmpty()
                .allSatisfy(store -> {
                    assertThat(store.getStoreName()).contains("커피");
                    assertThat(store.getCategory().getCategoryId()).isEqualTo(CAFE_CATEGORY_ID);
                });
    }

    @Test
    void 키워드는_맞지만_카테고리가_다르면_제외된다() {
        // 시드의 '커피' 매장 7건은 전부 카페/디저트다. 편의점/마트로 필터하면 교집합이 없다.
        List<StoreSummaryResponse> stores =
                storeMapper.findStores(condition("커피", CONVENIENCE_CATEGORY_ID, null));

        assertThat(stores).isEmpty();
    }

    @Test
    void 반경을_주면_그_안의_매장만_조회된다() {
        List<StoreSummaryResponse> stores = storeMapper.findStores(condition(null, null, 10));

        assertThat(stores)
                .extracting(StoreSummaryResponse::getStoreId)
                .containsExactly(NEAREST_STORE_ID);
    }

    @Test
    void 반경을_주지_않으면_거리_필터가_걸리지_않는다() {
        List<StoreSummaryResponse> stores = storeMapper.findStores(condition(null, null, null));

        assertThat(stores).hasSize(5);
        assertThat(stores.get(0).getDistanceMeters()).isLessThan(stores.get(4).getDistanceMeters());
    }

    @Test
    void 좌표가_NULL인_매장은_조회에서_제외된다() {
        // 변경을 먼저 하고 한 번만 조회한다 (MyBatis 1차 캐시 회피)
        jdbcTemplate.update("UPDATE store SET latitude = NULL WHERE store_id = ?", NEAREST_STORE_ID);

        List<StoreSummaryResponse> stores = storeMapper.findStores(condition(null, null, null));

        assertThat(stores)
                .extracting(StoreSummaryResponse::getStoreId)
                .doesNotContain(NEAREST_STORE_ID);
    }

    /**
     * {@code <association>}이 실제로 동작하는지 확인하는 핵심 테스트.
     * <p>
     * {@code resultType}으로 잘못 매핑하면 {@code category_*} 컬럼이 조용히 버려져 이 필드가
     * {@code null}이 된다. 쿼리는 성공하므로 이 단정 말고는 잡을 방법이 없다.
     */
    @Test
    void 카테고리_중첩_객체가_항상_채워진다() {
        List<StoreSummaryResponse> stores = storeMapper.findStores(condition(null, null, null));

        assertThat(stores).isNotEmpty().allSatisfy(store -> {
            assertThat(store.getCategory()).isNotNull();
            assertThat(store.getCategory().getCategoryId()).isNotNull();
            assertThat(store.getCategory().getCategoryName()).isNotBlank();
        });
    }

    @Test
    void 거리가_같으면_표시순위가_있는_매장이_없는_매장보다_앞선다() {
        // 시드의 store_rank는 244건 전부 NULL이라 값을 넣어야 검증할 수 있다.
        // 이름순 마지막인 프레퍼스(104)에 순위를 주면 동일 좌표 셋 중 맨 앞으로 와야 한다.
        jdbcTemplate.update("UPDATE store SET store_rank = 1 WHERE store_id = ?", TIE_LAST_BY_NAME);

        List<StoreSummaryResponse> stores =
                storeMapper.findStores(tieCondition());

        assertThat(stores)
                .extracting(StoreSummaryResponse::getStoreId)
                .startsWith(TIE_LAST_BY_NAME);
    }

    @Test
    void 표시순위가_전부_없으면_이름_사전순으로_정렬된다() {
        List<StoreSummaryResponse> stores = storeMapper.findStores(tieCondition());

        assertThat(stores)
                .extracting(StoreSummaryResponse::getStoreId)
                .startsWith(TIE_FIRST_BY_NAME);
    }

    @Test
    void 존재하는_카테고리는_true다() {
        assertThat(storeMapper.existsCategory(CONVENIENCE_CATEGORY_ID)).isTrue();
    }

    @Test
    void 존재하지_않는_카테고리는_false다() {
        assertThat(storeMapper.existsCategory(999L)).isFalse();
    }

    @Test
    void 시드_사용자는_위치_정보_이용에_동의한_상태다() {
        assertThat(storeMapper.findLocationAgreed(SEED_USER_ID)).isTrue();
    }

    @Test
    void 위치_미동의_사용자는_false를_반환한다() {
        jdbcTemplate.update("UPDATE users SET is_location_agreed = 0 WHERE user_id = ?", SEED_USER_ID);

        assertThat(storeMapper.findLocationAgreed(SEED_USER_ID)).isFalse();
    }

    @Test
    void 존재하지_않는_사용자는_null을_반환한다() {
        assertThat(storeMapper.findLocationAgreed(9999L)).isNull();
    }

    @Test
    void 새_검색어를_기록하면_행이_하나_늘어난다() {
        storeMapper.upsertSearchHistory(SEED_USER_ID, "군자동 파스타");

        assertThat(countSearchHistory()).isEqualTo(SEED_SEARCH_HISTORY_COUNT + 1);
    }

    @Test
    void 이미_있는_검색어를_다시_기록해도_행이_늘지_않는다() {
        storeMapper.upsertSearchHistory(SEED_USER_ID, SEED_KEYWORD);

        assertThat(countSearchHistory()).isEqualTo(SEED_SEARCH_HISTORY_COUNT);
    }

    // 위 테스트와 나눠 둔 이유는 §10이다. 한 테스트에서 "행 수 확인 → 기록 → searched_at 확인"을
    // 하면 앞의 조회가 같은 SqlSession에 캐시로 남는다. 여기서도 사전 값을 읽지 않고
    // 시드 상수(SEED_KEYWORD_SEARCHED_AT)와 비교한다.
    @Test
    void 이미_있는_검색어를_다시_기록하면_searched_at이_갱신된다() {
        storeMapper.upsertSearchHistory(SEED_USER_ID, SEED_KEYWORD);

        LocalDateTime searchedAt = jdbcTemplate.queryForObject(
                "SELECT searched_at FROM search_history WHERE user_id = ? AND keyword = ?",
                LocalDateTime.class, SEED_USER_ID, SEED_KEYWORD);

        assertThat(searchedAt).isAfter(SEED_KEYWORD_SEARCHED_AT);
    }

    // keyword 콜레이션이 utf8mb4_0900_ai_ci라 'cu'와 'CU'가 UNIQUE 키에서 같은 값이다.
    // 검색어 칩이 대소문자만 다르게 둘로 갈라지지 않게 하려는 의도된 동작이다(002-schema.sql).
    @Test
    void 대소문자만_다른_검색어는_같은_행으로_취급된다() {
        jdbcTemplate.update(
                "INSERT INTO search_history (user_id, keyword, searched_at) VALUES (?, 'cu', NOW())",
                SEED_USER_ID);

        storeMapper.upsertSearchHistory(SEED_USER_ID, "CU");

        assertThat(countSearchHistory()).isEqualTo(SEED_SEARCH_HISTORY_COUNT + 1);
    }

    // 검색어 보존 정책 검증. LOCATION-003의 "최대 5개"는 저장 한도가 아니라 화면 표시 개수라
    // (GET /api/store/keywords의 recent가 최신 5개만 내려준다) 이 매퍼에는 삭제 로직이 없다.
    @Test
    void 검색어가_다섯_건을_넘어도_아무것도_삭제되지_않는다() {
        for (int i = 1; i <= 6; i++) {
            storeMapper.upsertSearchHistory(SEED_USER_ID, "보존정책검증" + i);
        }

        assertThat(countSearchHistory()).isEqualTo(SEED_SEARCH_HISTORY_COUNT + 6);
    }

    // 호출부가 왜 try-catch를 해야 하는지의 구체적 실패 모드다. search_history.user_id가
    // users를 FK 참조하므로 없는 사용자로 기록하면 여기서 터진다.
    @Test
    void 존재하지_않는_사용자로_기록하면_예외가_난다() {
        assertThatThrownBy(() -> storeMapper.upsertSearchHistory(9999L, "없는사용자"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** 시드 페르소나의 검색어 행 수. 시드 16행은 전부 user_id = 1이다. */
    private int countSearchHistory() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM search_history WHERE user_id = ?", Integer.class, SEED_USER_ID);
    }

    /** 기준 좌표(군자동) + 주어진 필터. Request DTO에 @Setter가 없으므로 리플렉션으로 채운다. */
    private StoreSearchCondition condition(String keyword, Long categoryId, Integer radiusMeters) {
        return buildCondition(BASE_LATITUDE, BASE_LONGITUDE, keyword, categoryId, radiusMeters);
    }

    /** 동일 좌표 매장 셋이 있는 지점. 정렬 tie-break 검증용. */
    private StoreSearchCondition tieCondition() {
        return buildCondition(TIE_LATITUDE, TIE_LONGITUDE, null, null, null);
    }

    private StoreSearchCondition buildCondition(double latitude, double longitude,
                                                String keyword, Long categoryId, Integer radiusMeters) {
        StoreSearchCondition condition = new StoreSearchCondition();
        ReflectionTestUtils.setField(condition, "latitude", latitude);
        ReflectionTestUtils.setField(condition, "longitude", longitude);
        ReflectionTestUtils.setField(condition, "keyword", keyword);
        ReflectionTestUtils.setField(condition, "categoryId", categoryId);
        ReflectionTestUtils.setField(condition, "radiusMeters", radiusMeters);
        return condition;
    }
}
