package com.fitwallet.domain.store.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * {@code GET /api/store/keywords}의 {@code popular}. 집계 기간은 서비스 정책값이라
 * DB에서 오지 않고 {@code DefaultStoreService}가 채운다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PopularKeywordsResponse {

    /** 집계 기간(고정값 7). */
    private Integer periodDays;

    /** 상위 최대 5개. 없으면 빈 목록이다(에러가 아니다). */
    private List<PopularKeywordResponse> keywords;
}
