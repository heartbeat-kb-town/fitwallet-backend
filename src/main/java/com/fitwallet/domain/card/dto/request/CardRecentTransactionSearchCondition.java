package com.fitwallet.domain.card.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 내 카드 탭의 최근 이용 내역 조회 기간. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardRecentTransactionSearchCondition {

    private LocalDateTime startAt;
    private LocalDateTime endAt;
}
