package com.fitwallet.domain.store.mapper;

import com.fitwallet.domain.store.dto.request.StoreSearchCondition;
import com.fitwallet.domain.store.dto.response.PopularKeywordResponse;
import com.fitwallet.domain.store.dto.response.RecentKeywordResponse;
import com.fitwallet.domain.store.dto.response.StoreSummaryResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 가맹점 도메인 조회.
 * <p>
 * 도메인은 테이블을 소유하지 않는다(§2). 이 매퍼는 {@code store}, {@code category}를 조인해
 * 화면이 필요한 모양으로 바로 반환하고, 위치 동의 확인을 위해 {@code users}도 읽으며,
 * 검색어 기록을 위해 {@code search_history}에 쓴다. 매퍼는 도메인당 하나라(§3)
 * {@code SearchHistoryMapper}를 따로 만들지 않는다.
 * SQL은 {@code resources/mapper/store/StoreMapper.xml}에 있고,
 * {@code <select>}의 id는 여기 메서드명과 같아야 한다.
 */
@Mapper
public interface StoreMapper {

    /**
     * 사용자 좌표에서 가까운 순으로 가맹점을 조회한다. <b>최대 5건 고정</b>이다.
     * <p>
     * 키워드 검색과 카테고리 주변 조회를 한 쿼리로 처리한다 — {@code cond}의 {@code keyword} /
     * {@code categoryId} / {@code radiusMeters}가 각각 {@code null}이면 그 필터가 걸리지 않는다.
     * 반경 정책은 서비스가 이미 적용했으므로 여기서는 받은 값을 그대로 쓴다.
     * <p>
     * 좌표({@code latitude}/{@code longitude})가 NULL인 매장은 거리를 구할 수 없어 결과에서 제외된다.
     * 조건에 맞는 매장이 없으면 빈 목록이다(예외를 던지지 않는다).
     * <p>
     * 전체 건수를 내려주지 않으므로 짝이 되는 {@code count*} 메서드가 없다.
     */
    /**
     * @param latCells 사각형이 걸치는 {@code lat_cell} 목록(V15). {@code null}이면 이 조건이 붙지
     *                 않고 위경도 인덱스로 돈다 — 좌표·카테고리 갈래가 그렇다.
     *                 <p>
     *                 검색어 갈래에서만 넘긴다. B-tree는 선행 컬럼이 범위면 후행 컬럼으로 더
     *                 좁히지 못해서, {@code latitude BETWEEN}만으로는 인덱스 범위가 위도에만
     *                 걸리고 경도는 ICP 필터로만 작동한다(= 전국을 가로지르는 위도 띠를 걷는다).
     *                 선행을 <b>등치</b>인 {@code lat_cell IN (...)}으로 바꾸면 그 다음 컬럼인
     *                 경도의 범위가 인덱스에 먹는다. 근거와 실측은 V15 주석에 있다.
     *                 <p>
     *                 ⚠️ {@code latitude BETWEEN}은 그대로 남는다. 셀은 0.01° 격자라 사각형보다
     *                 넓고, 정답을 정하는 것은 여전히 사각형이다. 셀은 <b>덜 걷게 하는 힌트</b>일
     *                 뿐이라 넉넉하게 잡아도 결과가 바뀌지 않는다.
     */
    List<StoreSummaryResponse> findStores(@Param("cond") StoreSearchCondition cond,
                                          @Param("latCells") List<Integer> latCells);

    /**
     * 검색어 갈래의 <b>전국 단계</b>. 계단식 사각형이 5건을 못 채웠을 때만 호출된다.
     * <p>
     * {@link #findStores}와 같은 답을 내되 반경이 없다 — 전국에서 가까운 순 <b>최대 5건</b>이다.
     * {@code cond}의 {@code radiusMeters}는 보지 않는다.
     * <p>
     * {@code matchExpression}은 {@code MATCH ... AGAINST}에 그대로 들어가는 불리언 모드 식이다
     * (예: {@code +"호텔" +"리조트"}). <b>판정자가 아니라 가속기</b>이고 확정은 SQL에 남아 있는
     * {@code LIKE}가 한다 — 이 식이 {@code LIKE}의 상위집합이기만 하면 응답이 보존된다.
     * 그 보장은 {@code DefaultStoreService}가 만든다.
     * <p>
     * <b>{@code null}이면 {@code MATCH}를 아예 붙이지 않는다.</b> 쓸 조각이 하나도 안 나오는
     * 키워드({@code (주)}처럼 구분자와 1글자만으로 된 경우)가 있기 때문이다. 그때는 개선 전과
     * 같이 {@code LIKE} 풀스캔으로 돈다 — 느리지만 결과는 맞다.
     */
    List<StoreSummaryResponse> findStoresByFulltext(@Param("cond") StoreSearchCondition cond,
                                                    @Param("matchExpression") String matchExpression);

    /**
     * 라우팅 신호 — 이 검색어가 전국에 몇 건 매칭되는가.
     * <p>
     * 서비스가 "계단을 더 밟을지 전국으로 바로 갈지"를 이 값으로 가른다. <b>어느 쪽으로 가도
     * 응답은 같으므로</b> 이 값은 정확성이 아니라 비용에만 영향을 준다.
     * <p>
     * FT 인덱스만 읽어 싸다(2.4~22.5ms, {@code rows_examined = 1}). {@code categoryId}는 조건에
     * 넣지 않는다 — 넣으면 그 최적화가 깨진다. 카테고리가 함께 오면 실제 매칭 수의 상한이 되는데,
     * 라우팅이 계단 쪽으로 기울 뿐이라 안전한 방향의 오차다.
     *
     * @param matchExpression 불리언 모드 식. {@code null}이면 호출하지 않는다(조각이 없으면
     *                        셀 수 있는 것이 없다)
     */
    int countByFulltext(@Param("matchExpression") String matchExpression);

    /** 카테고리 존재 여부. 서비스가 {@code categoryId} 검증에 쓴다. */
    boolean existsCategory(@Param("categoryId") Long categoryId);

    /**
     * 위치 정보 이용 동의 여부. 서비스가 403 판정에 쓴다.
     * <p>
     * <b>없는 사용자면 {@code null}</b>이라 박싱 타입을 쓴다. 서비스는 {@code null}과
     * {@code false}를 같게(거부) 취급한다.
     */
    Boolean findLocationAgreed(@Param("userId") Long userId);

    /**
     * 검색어를 기록한다. 같은 {@code (user_id, keyword)} 행이 있으면 새 행을 넣지 않고
     * {@code searched_at}만 현재 시각으로 갱신한다.
     * <p>
     * 중복 방지는 애플리케이션이 아니라 <b>DB가 한다</b> — ERD v24부터
     * {@code uk_search_history_user_id_keyword} UNIQUE 제약이 있어
     * {@code INSERT ... ON DUPLICATE KEY UPDATE} 한 문장으로 끝난다. "SELECT로 있는지 보고 분기"나
     * "UPDATE 해보고 0행이면 INSERT" 같은 왕복이 없고, 동시 요청 둘이 모두 새 키워드로 판단해
     * 각각 INSERT하는 경쟁 조건도 생기지 않는다.
     * <p>
     * {@code searched_at}은 감사 컬럼이 아니라 <b>도메인 값</b>이다. DDL에 DEFAULT가 없으므로
     * §9의 "{@code created_at}/{@code updated_at}은 INSERT 문에 쓰지 않는다"와 반대로
     * 여기서는 명시해야 한다.
     * <p>
     * <b>반환값을 두지 않는다.</b> {@code ON DUPLICATE KEY UPDATE}의 영향 행 수는
     * 신규 1 / 갱신 2 / <b>값이 그대로면 0</b>이라(같은 초에 재검색하면 0이 나온다)
     * "기록됐는가"의 판단 근거로 쓸 수 없다.
     * <p>
     * 이름이 {@code insert*}가 아닌 이유는 문장은 INSERT지만 실제 효과가 UPDATE일 수 있어
     * 호출부를 오도하기 때문이다(§3 접두사 목록 개정 제안 중).
     */
    void upsertSearchHistory(@Param("userId") Long userId, @Param("keyword") String keyword);

    /**
     * 사용자의 최근 검색어. {@code searched_at} 내림차순 최대 5개. 없으면 빈 목록(예외 아님).
     */
    List<RecentKeywordResponse> findRecentKeywords(@Param("userId") Long userId);

    /**
     * 사전 집계된 인기 검색어를 {@code rank} 순으로 읽는다. <b>여기서 집계하지 않는다</b> —
     * {@code popular_keyword} 테이블에 이미 들어 있는 5행을 그대로 가져온다.
     * <p>
     * {@code searchCount}는 검색 횟수가 아니라 그 키워드를 마지막으로 검색한 사용자 수다
     * ({@code (user_id, keyword)} UNIQUE라서). 동점이면 가장 최근에 검색된 쪽이 앞선다 —
     * 이 규칙을 실제로 적용하는 곳은 {@link #insertPopularKeywords()}다.
     * <p>
     * {@link #refreshPopularKeywords 갱신}이 한 번도 돌지 않았으면 빈 목록이다(예외 아님).
     *
     * @see com.fitwallet.domain.store.service.StoreService#refreshPopularKeywords()
     */
    List<PopularKeywordResponse> findPopularKeywords();

    /**
     * 인기 검색어 재집계 ① — 기존 집계를 전부 지운다.
     * <p>
     * <b>{@link #insertPopularKeywords()}와 반드시 같은 트랜잭션에서 호출해야 한다.</b>
     * 지우기만 하고 끝나면 인기 검색어가 통째로 사라진다.
     */
    void deletePopularKeywords();

    /**
     * 인기 검색어 재집계 ② — 최근 7일 기준 상위 5개를 다시 계산해 넣는다.
     * <p>
     * 집계 규칙(7일 창 · 1인 1표 · 동점 시 최근 우선 · 최대 5건 · 내림차순)이 전부 이 SQL에
     * 들어 있다. 조회 쪽에는 규칙이 없으므로 <b>집계 규칙의 회귀 테스트는 이 메서드에 붙인다.</b>
     *
     * @return 실제로 들어간 행 수. 7일 창에 검색 기록이 없으면 0이다.
     */
    int insertPopularKeywords();

    /**
     * 검색 기록 하나를 삭제한다. 소유권 검증까지 WHERE 절에서 끝낸다 — 남의 기록이면 영향 행 0.
     * 영향 받은 행 수를 그대로 반환한다. 서비스가 0이면 404로 판단한다.
     */
    int deleteSearchHistory(@Param("searchHistoryId") Long searchHistoryId, @Param("userId") Long userId);

    /** 사용자의 검색 기록을 전부 삭제한다. 멱등적이라 반환값이 필요 없다(0건 삭제도 정상). */
    void deleteAllSearchHistory(@Param("userId") Long userId);
}
