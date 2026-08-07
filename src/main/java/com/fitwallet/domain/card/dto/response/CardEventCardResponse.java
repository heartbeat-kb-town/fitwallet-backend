package com.fitwallet.domain.card.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 카드별 이벤트 조회 화면에 표시할 보유 카드 정보. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardEventCardResponse {

    private Long cardId;
    private Long cardProductId;
    private String cardName;
    private String issuerName;
}
