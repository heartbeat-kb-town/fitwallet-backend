package com.fitwallet.domain.card.dto.response;

import com.fitwallet.domain.card.dto.CardUsagePerformanceStatus;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 월간 혜택 적용에 사용한 전월 실적 상태. */
@ApiModel(description = "카드 월간 혜택 전월 실적 상태")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardMonthlyBenefitPerformanceResponse {

    @ApiModelProperty(value = "실적 산정 월(yyyy-MM)", example = "2026-06")
    private String performanceMonth;

    @ApiModelProperty(value = "전월 실적 조건 상태", example = "ACHIEVED")
    private CardUsagePerformanceStatus status;

    @ApiModelProperty(value = "실적 상태 안내 문구", example = "전월 실적 조건이 적용 중이에요.")
    private String message;
}
