package com.fitwallet.domain.store.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 가맹점 조회({@code GET /api/store/search})의 봉투 응답. 명세의 {@code data}에 대응한다.
 * <p>
 * {@code stores}처럼 Mapper가 그대로 돌려주는 값이 아니라 {@code keyword}·{@code categoryId}·
 * {@code radiusMeters}는 {@code DefaultStoreService}가 검증·반경 정책을 적용한 뒤 조립한다.
 * 페이징 필드는 없다 — 결과가 항상 최대 5건 고정이라 전체 건수·다음 페이지 개념이 없다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreSearchResponse {

    /** 서버가 trim해 실제 검색에 사용한 키워드. 미전달이면 {@code null}. */
    private String keyword;

    /** 요청한 카테고리 에코. 미전달이면 {@code null}. */
    private Long categoryId;

    /** 반경 정책이 적용된 실제 값(m). 반경 필터를 걸지 않은 키워드 검색이면 {@code null}. */
    private Integer radiusMeters;

    /** 거리순 상위 최대 5건. 결과가 없으면 빈 목록이다(에러가 아니다). */
    private List<StoreSummaryResponse> stores;
}
