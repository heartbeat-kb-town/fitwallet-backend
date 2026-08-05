package com.fitwallet.domain.store.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * {@code GET /api/store/keywords}의 {@code recent[]} 원소. 최신순 최대 5개.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecentKeywordResponse {

    /** 개별 삭제({@code DELETE /api/store/keywords/recent/{id}})에 쓰는 식별자. */
    private Long searchHistoryId;

    private String keyword;

    /** 마지막으로 검색한 시각(정렬 근거). */
    private LocalDateTime searchedAt;
}
