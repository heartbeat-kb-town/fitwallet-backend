package com.fitwallet.domain.store.mapper;

import com.fitwallet.domain.store.dto.request.StoreSearchCondition;
import com.fitwallet.domain.store.dto.response.StoreSummaryResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 가맹점 도메인 조회.
 * <p>
 * 도메인은 테이블을 소유하지 않는다(§2). 이 매퍼는 {@code store}, {@code category}를 조인해
 * 화면이 필요한 모양으로 바로 반환하고, 위치 동의 확인을 위해 {@code users}도 읽는다.
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
    List<StoreSummaryResponse> findStores(@Param("cond") StoreSearchCondition cond);

    /** 카테고리 존재 여부. 서비스가 {@code categoryId} 검증에 쓴다. */
    boolean existsCategory(@Param("categoryId") Long categoryId);

    /**
     * 위치 정보 이용 동의 여부. 서비스가 403 판정에 쓴다.
     * <p>
     * <b>없는 사용자면 {@code null}</b>이라 박싱 타입을 쓴다. 서비스는 {@code null}과
     * {@code false}를 같게(거부) 취급한다.
     */
    Boolean findLocationAgreed(@Param("userId") Long userId);
}
