package com.fitwallet.domain.card.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 현재 구간과 다음 구간을 나타내는 실적 구간 요약. */
@ApiModel(description = "이용 실적 구간 요약")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardUsageTierSummaryResponse {

    @ApiModelProperty(value = "카드 통합 구간 순서. API가 생성한 0구간부터 시작한다", example = "2")
    private Integer tierOrder;

    @ApiModelProperty(value = "화면 표시용 구간명", example = "2구간")
    private String tierName;

    @ApiModelProperty(value = "구간 최소 인정금액(포함)", example = "300000.00")
    private BigDecimal minimumAmount;

    @ApiModelProperty(value = "구간 최대 인정금액(미포함). 최고 구간이면 null", example = "500000.00")
    private BigDecimal maximumAmount;
}
