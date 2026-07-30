package com.fitwallet.domain.store.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가맹점 조회 결과 한 건. 필드는 API 명세("가맹점 조회")의 {@code stores[]}를 따른다.
 * <p>
 * {@code store ⋈ category} 조인 결과를 MyBatis가 직접 채운다.
 * 어떤 테이블의 행도 아니므로 별도 엔티티를 두지 않는다(§2).
 * <p>
 * 화면 리스트가 카테고리 아이콘·명칭·주소·거리만 표시하므로 {@code latitude}/{@code longitude}와
 * {@code brand}는 응답에 담지 않는다. {@code store_qr_token}·{@code kakao_place_id}는
 * 결제·적재용 내부 값이라 제외한다.
 * <p>
 * MyBatis가 리플렉션으로 채우므로 {@code @Setter}는 붙이지 않는다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreSummaryResponse {

    private Long storeId;
    private String storeName;

    /** 도로명 주소. 스키마상 NULL 허용이라 {@code null}이 내려갈 수 있다. */
    private String address;

    /**
     * 사용자 좌표와의 직선거리(m, 반올림).
     * <p>
     * 금액이 아니라 미터 정수라 {@code BigDecimal}이 아니고 {@code Integer}다
     * (§9의 {@code BigDecimal} 규칙은 {@code DECIMAL} 금액 컬럼에만 적용된다).
     * 좌표가 NULL인 매장은 조회 대상에서 빠지므로 이 값은 항상 채워진다.
     */
    private Integer distanceMeters;

    /** 업종. {@code store.category_id}가 NOT NULL이라 항상 존재한다. */
    private StoreCategoryResponse category;
}
