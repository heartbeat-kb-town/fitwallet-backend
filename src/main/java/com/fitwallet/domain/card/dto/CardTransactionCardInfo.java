package com.fitwallet.domain.card.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 카드별 결제 내역 조회에 사용하는 보유 카드 내부 정보.
 * <p>
 * 로그인 사용자의 카드 소유권을 확인하고 카드 기본 정보와
 * 현재 월 신용카드의 저장된 결제 이용금액을 구성하는 데 사용한다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardTransactionCardInfo {

    private Long cardId;
    private Long cardProductId;
    private String cardName;
    private String issuerName;
    private String cardImageUrl;
    private CardType cardType;
    private String maskedRearNumber;

    /** 현재 월 신용카드는 반드시 값이 있어야 하며, null이면 데이터 정합성 오류로 처리한다. */
    private BigDecimal scheduledPaymentAmount;
}
