package com.fitwallet.domain.card.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** 카드 상품의 통합 실적 구간과 해당 구간에서 적용되는 혜택. */
@ApiModel(description = "카드 통합 이용 실적 구간")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardUsageTierResponse {

    @ApiModelProperty(value = "카드 통합 구간 순서. API가 생성한 0구간부터 시작한다", example = "1")
    private Integer tierOrder;

    @ApiModelProperty(value = "화면 표시용 구간명", example = "1구간")
    private String tierName;

    @ApiModelProperty(value = "구간 최소 인정금액(포함)", example = "200000.00")
    private BigDecimal minimumAmount;

    @ApiModelProperty(value = "구간 최대 인정금액(미포함). 최고 구간이면 null", example = "300000.00")
    private BigDecimal maximumAmount;

    @ApiModelProperty(value = "조회 월의 인정금액으로 이 구간의 최소 기준을 달성했는지", example = "true")
    private Boolean achieved;

    @ApiModelProperty(value = "조회 월의 현재 적용 구간인지", example = "false")
    private Boolean current;

    @ApiModelProperty(value = "이 구간에서 실제 적용되는 혜택 목록")
    private List<CardUsageBenefitResponse> benefits;
}
