package com.fitwallet.domain.store.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@code GET /api/store/keywords}의 {@code popular.keywords[]} 원소. 최근 7일 집계 상위 5개.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PopularKeywordResponse {

    /** 1부터 시작하는 순위. */
    private Integer rank;

    private String keyword;

    /** 기간 내 해당 키워드를 마지막으로 검색한 사용자 수(1인 1표). */
    private Long searchCount;
}
