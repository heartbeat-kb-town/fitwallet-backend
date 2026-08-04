package com.fitwallet.domain.card.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 이용 실적 조회에 필요한 보유 카드와 카드상품의 내부 정보. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardUsageCardInfo {

    private Long cardProductId;
    private String cardName;
    private String issuerName;
    private CardType cardType;
}
