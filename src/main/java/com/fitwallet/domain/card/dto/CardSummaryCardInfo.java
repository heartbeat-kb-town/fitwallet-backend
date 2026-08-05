package com.fitwallet.domain.card.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 내 카드 요약을 조합하기 위한 보유 카드 내부 조회 정보. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardSummaryCardInfo {

    private Long cardId;
    private Long cardProductId;
    private String cardName;
    private String issuerName;
    private String cardImageUrl;
    private CardType cardType;
    private String bankName;
    private BigDecimal balance;
}
