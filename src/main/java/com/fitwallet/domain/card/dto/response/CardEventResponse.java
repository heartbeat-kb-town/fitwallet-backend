package com.fitwallet.domain.card.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/** 카드별 진행 중 이벤트 조회의 최상위 응답. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardEventResponse {

    private CardEventCardResponse card;
    private Integer eventCount;
    private List<CardEventItemResponse> events;
}
