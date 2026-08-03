package com.fitwallet.domain.card.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 카드별 월간 결제 내역 조회의 최종 응답.
 */
@ApiModel(description = "카드별 월간 결제 내역 조회 결과")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardTransactionDetailResponse {

    @ApiModelProperty(value = "조회한 보유 카드 정보")
    private CardTransactionCardResponse card;

    @ApiModelProperty(value = "조회 연월(yyyy-MM)", example = "2026-07")
    private String yearMonth;

    /** 현재 월을 포함하여 조회할 수 있는 최근 3개월. */
    @ApiModelProperty(value = "현재 월을 포함하여 조회할 수 있는 최근 3개월",
            example = "[\"2026-07\", \"2026-06\", \"2026-05\"]")
    private List<String> availableYearMonths;

    @ApiModelProperty(value = "카드 유형과 조회 월에 따른 결제 이용금액 요약")
    private CardTransactionSummaryResponse paymentSummary;

    @ApiModelProperty(value = "커서 방식의 결제 내역")
    private CardTransactionCursorResponse transactions;
}
