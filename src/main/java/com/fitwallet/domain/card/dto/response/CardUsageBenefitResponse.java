package com.fitwallet.domain.card.dto.response;

import com.fitwallet.domain.benefit.dto.BenefitType;
import com.fitwallet.domain.benefit.dto.ValueType;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 실적 구간에서 적용되는 카드 혜택. */
@ApiModel(description = "실적 구간별 카드 혜택")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardUsageBenefitResponse {

    @ApiModelProperty(value = "혜택 서비스 ID", example = "125")
    private Long benefitId;

    @ApiModelProperty(value = "혜택명", example = "스타벅스 환급할인")
    private String benefitName;

    @ApiModelProperty(value = "혜택 유형(CASHBACK: 할인, ACCUMULATE: 적립)", example = "CASHBACK")
    private BenefitType benefitType;

    @ApiModelProperty(value = "혜택 값 유형(RATE: 정률, FIXED: 정액)", example = "RATE")
    private ValueType valueType;

    @ApiModelProperty(value = "혜택 원본 값", example = "20.00")
    private BigDecimal valueNumber;

    @ApiModelProperty(value = "화면 표시용 혜택 값. 고정 주유 혜택은 리터당 단위를 포함한다",
            example = "20%")
    private String valueLabel;
}
