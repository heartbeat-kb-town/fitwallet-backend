package com.fitwallet.domain.store.mapper;

import com.fitwallet.domain.store.dto.request.StoreSearchCondition;
import com.fitwallet.domain.store.dto.response.PopularKeywordResponse;
import com.fitwallet.domain.store.dto.response.RecentKeywordResponse;
import com.fitwallet.domain.store.dto.response.StoreCategoryResponse;
import com.fitwallet.domain.store.dto.response.StoreSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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

    /**
     * 시드 매장이 하나도 없는 좌표(충청 내륙). 사각형 경계 검증용 매장을 여기 심으면
     * 시드 244건에 밀려 LIMIT 5에서 잘리는 일이 없어 단언이 결정적이 된다.
     */
    private static final double FAR_LATITUDE = 36.5000;
    private static final double FAR_LONGITUDE = 127.5000;

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

    /** 시드에 이미 있는 검색 기록 id(이디야커피, user_id=1). 삭제 대상 존재 확인용. */
    private static final Long EXISTING_SEARCH_HISTORY_ID = 1L;

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
    void FULLTEXT_전국_조회가_LIKE_조회와_같은_결과를_낸다() {
        // MATCH는 가속기일 뿐이고 판정은 LIKE가 한다. 두 경로의 답이 다르면 그 전제가 깨진 것이다.
        List<StoreSummaryResponse> byLike = storeMapper.findStores(condition("커피", null, null));

        List<StoreSummaryResponse> byFulltext =
                storeMapper.findStoresByFulltext(condition("커피", null, null), "+\"커피\"");

        assertThat(byFulltext)
                .isNotEmpty()
                .extracting(StoreSummaryResponse::getStoreId)
                .isEqualTo(byLike.stream().map(StoreSummaryResponse::getStoreId).collect(Collectors.toList()));
    }

    @Test
    void stopword가_섞인_영문_검색어도_FULLTEXT가_찾는다() {
        // 이 테스트가 V14의 빈 stopword 테이블을 지킨다.
        //
        // ngram은 토큰이 stopword를 '포함만 해도' 색인에서 뺀다. 기본 목록에 a와 i가 있어
        // 'AI'는 두 글자가 모두 stopword라 통째로 사라진다 — 빈 stopword 테이블을 가리키지 않은
        // 상태에서 인덱스를 만들면 여기서 0건이 나온다. 실패가 예외가 아니라 '조용한 0건'이라
        // 이 테스트가 없으면 배포 후에도 눈치채기 어렵다(perf DB 실측: bar·lab·nail·ca·A1이 전부 0건).
        List<StoreSummaryResponse> byLike = storeMapper.findStores(condition("AI", null, null));
        assertThat(byLike).as("시드에 AI가 들어간 상호명이 있어야 이 테스트가 의미를 가진다").isNotEmpty();

        List<StoreSummaryResponse> byFulltext =
                storeMapper.findStoresByFulltext(condition("AI", null, null), "+\"AI\"");

        assertThat(byFulltext)
                .extracting(StoreSummaryResponse::getStoreId)
                .isEqualTo(byLike.stream().map(StoreSummaryResponse::getStoreId).collect(Collectors.toList()));
    }

    @Test
    void 쓸_조각이_없는_검색어는_MATCH_없이_LIKE로만_조회된다() {
        // '(주)'처럼 구분자와 1글자만으로 된 키워드는 MATCH 식을 만들 수 없다. 그때 서비스가
        // matchExpression을 null로 넘기고, 매퍼는 MATCH를 붙이지 않은 채 LIKE로만 돌아야 한다.
        // 빈 MATCH 식을 넘기면 0건이 나와 결과가 바뀐다.
        List<StoreSummaryResponse> byLike = storeMapper.findStores(condition("커피", null, null));

        List<StoreSummaryResponse> withoutMatch =
                storeMapper.findStoresByFulltext(condition("커피", null, null), null);

        assertThat(withoutMatch)
                .isNotEmpty()
                .extracting(StoreSummaryResponse::getStoreId)
                .isEqualTo(byLike.stream().map(StoreSummaryResponse::getStoreId).collect(Collectors.toList()));
    }

    @Test
    void 라우팅_신호는_LIKE_매칭_수_이상을_센다() {
        // MATCH는 LIKE의 상위집합이므로 이 값은 실제 매칭 수보다 작을 수 없다.
        // 작게 나오면 상위집합 전제가 깨진 것이고, 그러면 라우팅이 아니라 결과가 틀어진다.
        int likeCount = storeMapper.findStores(condition("커피", null, null)).size();

        int matchCount = storeMapper.countByFulltext("+\"커피\"");

        assertThat(matchCount).isGreaterThanOrEqualTo(likeCount);
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
    void 키워드와_카테고리가_모두_없으면_반경_안에서_가까운_순_5건이_조회된다() {
        // 서비스가 주변 조회 모드에 적용하는 기본 반경(3km)을 그대로 넣는다.
        List<StoreSummaryResponse> stores = storeMapper.findStores(condition(null, null, 3000));

        assertThat(stores)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.comparing(StoreSummaryResponse::getDistanceMeters))
                .allSatisfy(store -> assertThat(store.getDistanceMeters()).isLessThanOrEqualTo(3000));
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

    @Test
    void 최근_검색어는_searched_at_내림차순_최대_5건이다() {
        List<RecentKeywordResponse> keywords = storeMapper.findRecentKeywords(SEED_USER_ID);

        assertThat(keywords)
                .hasSize(5)
                .isSortedAccordingTo(Comparator.comparing(RecentKeywordResponse::getSearchedAt).reversed());
    }

    @Test
    void 검색_기록이_없는_사용자는_빈_리스트를_반환한다() {
        // FK 제약이 걸린 쓰기와 달리 조회라 존재하지 않는 사용자 ID를 그대로 써도 안전하다.
        List<RecentKeywordResponse> keywords = storeMapper.findRecentKeywords(9999L);

        assertThat(keywords).isEmpty();
    }

    @Test
    void 인기_검색어는_최대_5건이다() {
        // 시드(7일 이내 3건: 이디야커피/커피 할인/공차)에 서로 다른 키워드 6개를 더해 8건으로 만든다.
        Long userId = insertUser();
        for (int i = 1; i <= 6; i++) {
            insertSearchHistory(userId, "인기검증키워드" + i, "0 SECOND");
        }

        List<PopularKeywordResponse> popular = refreshAndFindPopularKeywords();

        assertThat(popular).hasSize(5);
    }

    @Test
    void 인기_검색어는_search_count_내림차순이다() {
        Long user1 = insertUser();
        Long user2 = insertUser();
        Long user3 = insertUser();
        insertSearchHistory(user1, "정렬검증다수", "0 SECOND");
        insertSearchHistory(user2, "정렬검증다수", "0 SECOND");
        insertSearchHistory(user3, "정렬검증다수", "0 SECOND");
        Long user4 = insertUser();
        insertSearchHistory(user4, "정렬검증소수", "0 SECOND");

        List<PopularKeywordResponse> popular = refreshAndFindPopularKeywords();

        assertThat(popular)
                .extracting(PopularKeywordResponse::getKeyword)
                .contains("정렬검증다수", "정렬검증소수")
                .containsSequence("정렬검증다수", "정렬검증소수");
    }

    @Test
    void 인기_검색어가_동점이면_최근에_검색된_키워드가_앞선다() {
        Long oldUser1 = insertUser();
        Long oldUser2 = insertUser();
        insertSearchHistory(oldUser1, "동점오래됨", "5 DAY");
        insertSearchHistory(oldUser2, "동점오래됨", "5 DAY");
        Long newUser1 = insertUser();
        Long newUser2 = insertUser();
        insertSearchHistory(newUser1, "동점최근", "1 DAY");
        insertSearchHistory(newUser2, "동점최근", "1 DAY");

        List<PopularKeywordResponse> popular = refreshAndFindPopularKeywords();

        assertThat(popular)
                .extracting(PopularKeywordResponse::getKeyword)
                .containsSequence("동점최근", "동점오래됨");
    }

    @Test
    void 인기_검색어_집계는_7일보다_오래된_기록을_제외한다() {
        Long userId = insertUser();
        insertSearchHistory(userId, "기간밖키워드", "8 DAY");

        List<PopularKeywordResponse> popular = refreshAndFindPopularKeywords();

        assertThat(popular).extracting(PopularKeywordResponse::getKeyword).doesNotContain("기간밖키워드");
    }

    @Test
    void 재집계하면_이전_집계에만_있던_키워드가_사라진다() {
        // DELETE 없이 REPLACE INTO 한 문장으로 갱신하면 이 테스트가 깨진다 —
        // 새 결과가 4위까지만 나올 때 옛 5위 행이 그대로 남기 때문이다(StoreMapper.xml 주석).
        Long userId = insertUser();
        insertSearchHistory(userId, "재집계검증키워드", "0 SECOND");
        assertThat(refreshAndFindPopularKeywords())
                .extracting(PopularKeywordResponse::getKeyword)
                .contains("재집계검증키워드");

        storeMapper.deleteAllSearchHistory(userId);

        assertThat(refreshAndFindPopularKeywords())
                .extracting(PopularKeywordResponse::getKeyword)
                .doesNotContain("재집계검증키워드");
    }

    @Test
    void 집계가_한_번도_돌지_않았으면_빈_목록이다() {
        // 첫 배포 직후 스케줄러가 아직 안 돈 상태다. 예외가 아니라 빈 목록이 계약이다
        // (StoreKeywordsResponse Javadoc — "서비스 초기에는 popular.keywords만 빌 수 있다").
        storeMapper.deletePopularKeywords();

        assertThat(storeMapper.findPopularKeywords()).isEmpty();
    }

    @Test
    void 인기_검색어_순위는_1부터_순서대로_매겨진다() {
        Long userId = insertUser();
        insertSearchHistory(userId, "순위검증키워드", "0 SECOND");

        List<PopularKeywordResponse> popular = refreshAndFindPopularKeywords();

        assertThat(popular)
                .extracting(PopularKeywordResponse::getRank)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, popular.size())
                        .boxed().toList());
    }

    @Test
    void 본인_소유_검색_기록을_삭제하면_영향_행이_1이고_실제로_사라진다() {
        int affected = storeMapper.deleteSearchHistory(EXISTING_SEARCH_HISTORY_ID, SEED_USER_ID);

        assertThat(affected).isEqualTo(1);
        assertThat(countSearchHistory()).isEqualTo(SEED_SEARCH_HISTORY_COUNT - 1);
    }

    @Test
    void 존재하지_않는_검색_기록_id면_영향_행이_0이다() {
        int affected = storeMapper.deleteSearchHistory(999999L, SEED_USER_ID);

        assertThat(affected).isZero();
    }

    @Test
    void 다른_사용자의_검색_기록은_지우려_해도_영향_행이_0이고_행이_그대로_남는다() {
        Long otherUserId = insertUser();
        insertSearchHistory(otherUserId, "다른유저키워드", "0 SECOND");
        Long otherSearchHistoryId = jdbcTemplate.queryForObject(
                "SELECT search_history_id FROM search_history WHERE user_id = ? AND keyword = ?",
                Long.class, otherUserId, "다른유저키워드");

        int affected = storeMapper.deleteSearchHistory(otherSearchHistoryId, SEED_USER_ID);

        assertThat(affected).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM search_history WHERE search_history_id = ?",
                Integer.class, otherSearchHistoryId)).isEqualTo(1);
    }

    @Test
    void 검색_기록_전체_삭제시_시드_사용자의_기록이_전부_사라진다() {
        storeMapper.deleteAllSearchHistory(SEED_USER_ID);

        assertThat(countSearchHistory()).isZero();
    }

    @Test
    void 지울_기록이_없는_사용자에게_전체_삭제를_호출해도_예외가_없다() {
        Long emptyUserId = insertUser();

        storeMapper.deleteAllSearchHistory(emptyUserId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM search_history WHERE user_id = ?",
                Integer.class, emptyUserId)).isZero();
    }

    /**
     * 인기 검색어 집계는 다중 사용자 데이터가 필요한데 시드는 user_id=1 하나뿐이다(§10 시드 재사용
     * 원칙과 별개로, 이 케이스는 시드에 없는 데이터 모양이라 합성 사용자를 만든다).
     * {@code users.name}만 NOT NULL이라 최소 컬럼만 채운다. 클래스 레벨 {@code @Transactional}이
     * 롤백하므로 생성한 사용자도 테스트 종료 시 함께 사라진다.
     */
    private Long insertUser() {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO users (name, is_location_agreed) VALUES ('검증용', 0)",
                    Statement.RETURN_GENERATED_KEYS);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    /**
     * {@code intervalLiteral}은 테스트 내부 고정 상수만 받는다(예: {@code "3 DAY"}) — 사용자
     * 입력이 섞이지 않으므로 문자열 결합이 §6의 {@code ${}} 금지에 해당하지 않는다. MySQL 서버의
     * {@code NOW()} 기준으로 상대 시각을 계산해야 컨테이너 시간대(UTC)와 무관하게 정확하다.
     */
    private void insertSearchHistory(Long userId, String keyword, String intervalLiteral) {
        jdbcTemplate.update(
                "INSERT INTO search_history (user_id, keyword, searched_at) "
                        + "VALUES (?, ?, NOW() - INTERVAL " + intervalLiteral + ")",
                userId, keyword);
    }

    /**
     * 인기 검색어를 재집계한 뒤 읽는다.
     * <p>
     * <b>아래 popular 테스트들이 실제로 검증하는 것은 {@code insertPopularKeywords}의 집계
     * 규칙이다.</b> V13에서 조회가 {@code search_history} 직접 집계에서 사전 집계 테이블
     * 읽기로 바뀌면서, {@code findPopularKeywords}만 불러서는 7일 창·동점 처리·순위 같은
     * 규칙을 확인할 수 없게 됐다. 그래서 검증 대상을 집계 쪽으로 옮기고, 조회는 그 결과를
     * 꺼내오는 수단으로만 쓴다.
     * <p>
     * 운영에서 이 두 문장을 부르는 것은 {@code DefaultStoreService.refreshPopularKeywords}
     * 하나뿐이고 5분 주기 스케줄러가 호출한다. 테스트는 클래스 레벨 {@code @Transactional}로
     * 롤백되므로 여기서 지운 집계가 다른 테스트에 남지 않는다.
     */
    private List<PopularKeywordResponse> refreshAndFindPopularKeywords() {
        storeMapper.deletePopularKeywords();
        storeMapper.insertPopularKeywords();
        return storeMapper.findPopularKeywords();
    }

    /** 시드 페르소나의 검색어 행 수. 시드 16행은 전부 user_id = 1이다. */
    private int countSearchHistory() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM search_history WHERE user_id = ?", Integer.class, SEED_USER_ID);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // bounding box 선필터 (V11 인덱스와 짝)
    //
    // 여기서 확인하는 것은 성능이 아니라 정확성이다. 로컬 dev DB는 store가 244건이라
    // 실행계획이 어차피 풀스캔이고 시간도 의미가 없다. 성능은 272만 행 perf DB에서 잰다.
    //
    // 사각형은 원을 완전히 덮어야 한다. 크면 무해하지만(HAVING이 잘라낸다) 조금이라도 작으면
    // 반경 경계의 가맹점이 조용히 사라져 "결과가 안 바뀐다"는 이 최적화의 전제가 깨진다.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 사각형의 위도폭이 반경에 정확히 닿는지 본다. 가맹점을 기준 좌표 <b>정북</b>으로 정확히
     * 3,000m 떨어진 곳에 심는다 — 위도만 어긋나므로 사각형의 위도폭이 조금이라도 모자라면
     * 이 매장이 HAVING까지 가지 못하고 사라진다.
     * <p>
     * 상수를 흔히 쓰이는 111,320(WGS84 적도 반지름 6,378,137에서 나온 값)으로 바꾸면
     * 사각형이 3.37m 작아져 <b>이 테스트가 깨진다.</b> 매퍼는 거리 계산과 같은 평균 반지름
     * 6,371,000을 써야 한다.
     */
    @Test
    void 반경_경계에_정확히_걸친_매장도_사각형에_들어온다() {
        double latitudeDegreesPerMeter = 1 / (6371000 * Math.PI / 180);
        double exactlyThreeKmNorth = FAR_LATITUDE + 3000 * latitudeDegreesPerMeter;
        Long storeId = insertStore(exactlyThreeKmNorth, FAR_LONGITUDE);

        List<StoreSummaryResponse> stores = storeMapper.findStores(
                buildCondition(FAR_LATITUDE, FAR_LONGITUDE, null, null, 3000));

        assertThat(stores).extracting(StoreSummaryResponse::getStoreId).containsExactly(storeId);
        assertThat(stores.get(0).getDistanceMeters()).isEqualTo(3000);
    }

    /**
     * 경도 쪽도 같이 본다. 정동은 자오선 간격이 위도에 따라 좁아지므로
     * {@code COS(RADIANS(위도))}로 나눠야 사각형이 원에 닿는다. 나눗셈을 빠뜨리면
     * 사각형이 위도 37도에서 약 20% 좁아져 이 매장이 사라진다.
     */
    @Test
    void 정동으로_반경_경계에_걸친_매장도_사각형에_들어온다() {
        double longitudeDegreesPerMeter =
                1 / (6371000 * Math.PI / 180 * Math.cos(Math.toRadians(FAR_LATITUDE)));
        double exactlyThreeKmEast = FAR_LONGITUDE + 3000 * longitudeDegreesPerMeter;
        Long storeId = insertStore(FAR_LATITUDE, exactlyThreeKmEast);

        List<StoreSummaryResponse> stores = storeMapper.findStores(
                buildCondition(FAR_LATITUDE, FAR_LONGITUDE, null, null, 3000));

        assertThat(stores).extracting(StoreSummaryResponse::getStoreId).containsExactly(storeId);
    }

    /** 사각형 밖으로 나간 매장은 여전히 제외된다. 사각형이 크기만 하고 안 자르면 그것도 버그다. */
    @Test
    void 반경을_넘은_매장은_사각형을_넓혀도_제외된다() {
        double latitudeDegreesPerMeter = 1 / (6371000 * Math.PI / 180);
        insertStore(FAR_LATITUDE + 3100 * latitudeDegreesPerMeter, FAR_LONGITUDE);

        List<StoreSummaryResponse> stores = storeMapper.findStores(
                buildCondition(FAR_LATITUDE, FAR_LONGITUDE, null, null, 3000));

        assertThat(stores).isEmpty();
    }

    /**
     * 사각형을 붙여도 결과가 바뀌지 않는지 본다. 시드 244건이 전부 반경 1km 안에 있으므로,
     * 반경을 주지 않은(= 사각형이 없는) 조회의 상위 5건과 반경 1km 조회의 상위 5건이 같아야 한다.
     */
    @Test
    void 사각형을_거쳐도_반경_안_상위_5건이_달라지지_않는다() {
        List<StoreSummaryResponse> withoutBox = storeMapper.findStores(condition(null, null, null));
        List<StoreSummaryResponse> withBox = storeMapper.findStores(condition(null, null, 1000));

        assertThat(withoutBox).allSatisfy(store ->
                assertThat(store.getDistanceMeters()).isLessThanOrEqualTo(1000));
        assertThat(withBox).extracting(StoreSummaryResponse::getStoreId)
                .isEqualTo(withoutBox.stream().map(StoreSummaryResponse::getStoreId).toList());
    }

    /** 좌표가 NULL인 매장은 반경을 주지 않은 조회에서도 나오면 안 된다. 사각형이 없는 갈래다. */
    @Test
    void 좌표가_없는_매장은_반경_없는_조회에서도_제외된다() {
        insertStore(null, null);

        List<StoreSummaryResponse> stores = storeMapper.findStores(condition(null, null, null));

        assertThat(stores).allSatisfy(store ->
                assertThat(store.getDistanceMeters()).isNotNull());
    }

    /**
     * 검증용 매장을 심는다. 클래스 레벨 {@code @Transactional}이 롤백하므로 시드가 더러워지지 않는다.
     * 좌표가 {@code null}이면 그대로 넣는다 — DDL이 허용한다.
     */
    private Long insertStore(Double latitude, Double longitude) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO store (category_id, store_name, latitude, longitude, address) "
                            + "VALUES (?, ?, ?, ?, '검증용 주소')",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, CAFE_CATEGORY_ID);
            ps.setString(2, "bbox 검증용 매장");
            if (latitude == null) {
                ps.setNull(3, java.sql.Types.DECIMAL);
                ps.setNull(4, java.sql.Types.DECIMAL);
            } else {
                ps.setBigDecimal(3, java.math.BigDecimal.valueOf(latitude));
                ps.setBigDecimal(4, java.math.BigDecimal.valueOf(longitude));
            }
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
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
