package com.fitwallet.domain.store.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가맹점의 업종. 명세("가맹점 조회")의 {@code stores[].category}에 대응한다.
 * <p>
 * {@code store.category_id}가 NOT NULL이라 조인 결과가 항상 존재하므로 이 객체는 {@code null}이 되지 않는다.
 * 키워드 검색은 여러 업종이 섞일 수 있어 매장마다 따로 내려준다.
 * <p>
 * <b>1:1 중첩 객체라 MyBatis {@code resultType} 자동 매핑으로는 채워지지 않는다.</b>
 * {@code StoreMapper.xml}의 {@code <association>}이 채운다 — 자세한 이유는 그쪽 주석 참고.
 * <p>
 * MyBatis가 리플렉션으로 채우므로 {@code @Setter}는 붙이지 않는다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreCategoryResponse {

    private Long categoryId;
    private String categoryName;

    /** 카테고리 대표 이미지 S3 경로. 화면 리스트가 브랜드 로고 대신 이 아이콘을 쓴다. */
    private String categoryImageUrl;
}
