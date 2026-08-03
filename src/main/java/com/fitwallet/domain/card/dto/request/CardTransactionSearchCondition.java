package com.fitwallet.domain.card.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 카드별 결제 내역 조회에 사용하는 확정 조건.
 * <p>
 * HTTP 요청 원문을 그대로 담지 않는다. Service가 조회 월과 커서를 검증한 뒤
 * Mapper가 바로 사용할 수 있는 날짜 범위와 커서 값으로 변환해 채운다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardTransactionSearchCondition {

    /** 조회 월의 시작 시각(포함). */
    private LocalDateTime startAt;

    /**
     * 조회 종료 시각(미포함).
     * 현재 월 신용카드는 오늘의 시작 시각, 현재 월 체크카드는 내일의 시작 시각,
     * 과거 월은 카드 유형과 관계없이 다음 달의 시작 시각을 사용한다.
     */
    private LocalDateTime endAt;

    /** 다음 묶음 조회의 기준 결제 시각. 첫 조회면 null이다. */
    private LocalDateTime cursorPaidAt;

    /** 같은 결제 시각의 순서를 고정하는 결제 내역 ID. 첫 조회면 null이다. */
    private Long cursorTransactionId;

    /** hasNext 판정을 위해 요청 size에 1을 더한 SQL LIMIT 값. */
    private Integer limit;
}
