package com.fitwallet.domain.card.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/** 내 카드 탭에 표시할 선택 카드의 통합 요약. */
@ApiModel(description = "내 카드 탭의 선택 카드 요약")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardSummaryResponse {

    @ApiModelProperty(value = "선택한 보유 카드 기본 정보")
    private CardSummaryCardResponse card;

    @ApiModelProperty(value = "카드 유형별 상단 금액 정보")
    private CardSummaryAmountResponse amountSummary;

    @ApiModelProperty(value = "KST 기준 오늘과 어제의 전체 결제 내역. 최신순")
    private List<CardSummaryTransactionResponse> recentTransactions;

    @ApiModelProperty(value = "현재 월 이용 실적 요약")
    private CardSummaryUsageResponse usageSummary;
}
