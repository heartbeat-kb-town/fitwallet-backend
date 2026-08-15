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
 * 승인 거래에서 다시 계산한 신용카드 결제예정금액을 구성하는 데 사용한다.
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

    /** 신용카드는 전체 승인 거래의 final_amount 합계이며, 체크카드는 null이다. */
    private BigDecimal scheduledPaymentAmount;
}
