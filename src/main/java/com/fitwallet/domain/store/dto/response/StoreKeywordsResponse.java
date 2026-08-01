package com.fitwallet.domain.store.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 검색어 조회({@code GET /api/store/keywords})의 봉투 응답. 명세의 {@code data}에 대응한다.
 * <p>
 * {@code recent}·{@code popular}는 서로 독립적으로 비어 있을 수 있다 — 신규 가입자는
 * {@code recent}만, 서비스 초기에는 {@code popular.keywords}만 빌 수 있다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreKeywordsResponse {

    private List<RecentKeywordResponse> recent;

    private PopularKeywordsResponse popular;
}
