package com.fitwallet.domain.card.dto.response;

import com.fitwallet.domain.benefit.dto.LimitBasis;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitLimitStatus;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitUnit;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 혜택 하나에 현재 적용되는 금액·포인트·횟수 월 한도. */
@ApiModel(description = "카드 혜택 월 한도 사용 현황")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardMonthlyBenefitLimitResponse {

    private Long limitId;

    @ApiModelProperty(value = "DB 한도 기준", example = "AMOUNT")
    private LimitBasis limitBasis;

    @ApiModelProperty(value = "화면 표시 단위로 환산한 월 한도", example = "5000")
    private BigDecimal limitValue;

    @ApiModelProperty(value = "화면 표시 단위로 환산한 이번 달 사용량", example = "1000")
    private BigDecimal usedValue;

    @ApiModelProperty(value = "화면 표시 단위로 환산한 잔여량. 음수가 되지 않는다", example = "4000")
    private BigDecimal remainingValue;

    @ApiModelProperty(value = "한도 표시 단위", example = "KRW")
    private CardMonthlyBenefitUnit limitUnit;

    @ApiModelProperty(value = "사용량과 전체 한도 표시 문구", example = "1,000원 / 5,000원")
    private String limitLabel;

    @ApiModelProperty(value = "개별 월 한도 소진 상태", example = "AVAILABLE")
    private CardMonthlyBenefitLimitStatus limitStatus;

    @ApiModelProperty(value = "여러 혜택 서비스가 함께 소비하는 공유 한도인지 여부", example = "true")
    private boolean shared;
}
