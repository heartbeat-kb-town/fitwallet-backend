package com.fitwallet.domain.card.dto.response;

import com.fitwallet.domain.card.dto.CardTransactionSummaryType;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 카드 유형에 따라 결제 내역 화면 상단에 표시하는 결제 이용금액 요약.
 */
@ApiModel(description = "결제 내역 화면 상단의 결제 이용금액 요약")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardTransactionSummaryResponse {

    /** 금액의 산정 방식을 나타내는 요약 유형. */
    @ApiModelProperty(value = "금액 산정 방식(SCHEDULED_PAYMENT 또는 MONTHLY_PAYMENT_AMOUNT)",
            example = "SCHEDULED_PAYMENT")
    private CardTransactionSummaryType summaryType;

    /**
     * 신용카드는 조회 월과 무관하게 전체 승인 거래의 실제 청구액을 합산한다.
     * 체크카드는 조회 기간에 해당하는 승인 거래 금액을 합산한다.
    */
    @ApiModelProperty(value = "결제 이용금액", example = "89800.00")
    private BigDecimal amount;
}
