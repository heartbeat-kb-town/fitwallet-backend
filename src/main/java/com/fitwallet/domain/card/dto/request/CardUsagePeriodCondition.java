package com.fitwallet.domain.card.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 카드 이용 실적 금액을 집계할 시작 시각과 종료 시각. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardUsagePeriodCondition {

    private LocalDateTime startAt;
    private LocalDateTime endAt;
}
