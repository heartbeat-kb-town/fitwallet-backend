package com.fitwallet.domain.card.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 카드의 이번 달 월 한도 혜택을 원화 기준으로 합산한 상단 요약. */
@ApiModel(description = "카드 월간 혜택 원화 환산 요약")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardMonthlyBenefitSummaryResponse {

    @ApiModelProperty(value = "남은 월 한도 안에서 추가로 받을 수 있는 잠재 혜택 원화 금액", example = "13800")
    private BigDecimal potentialBenefitAmount;

    @ApiModelProperty(value = "조회 기간에 실제 수령한 반환 대상 혜택의 원화 합계", example = "25200")
    private BigDecimal receivedBenefitAmount;

    @ApiModelProperty(value = "공유 한도를 중복 제거한 전체 혜택 한도의 원화 합계", example = "39000")
    private BigDecimal totalBenefitLimit;

    @ApiModelProperty(value = "혜택 한도 사용률. 전체 한도가 없거나 0이면 null", example = "64.6")
    private BigDecimal benefitUsageRate;

    @ApiModelProperty(value = "받은 혜택 상세 화면으로 이동할 수 있는지 여부", example = "true")
    private boolean receivedBenefitDetailAvailable;
}
