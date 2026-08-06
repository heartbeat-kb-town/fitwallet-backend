package com.fitwallet.domain.card.dto.response;

import com.fitwallet.domain.benefit.dto.BenefitType;
import com.fitwallet.domain.benefit.dto.ValueType;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitLimitStatus;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitUnit;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** 업종 범위 혜택 서비스 하나와 카테고리 하나를 조합한 월간 혜택 행. */
@ApiModel(description = "카테고리별 카드 월간 혜택")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardMonthlyCategoryBenefitResponse {

    @ApiModelProperty(value = "혜택 서비스 ID", example = "1")
    private Long benefitServiceId;

    @ApiModelProperty(value = "카테고리 ID", example = "2")
    private Long categoryId;

    @ApiModelProperty(value = "DB의 정식 카테고리명", example = "편의점/마트")
    private String categoryName;

    @ApiModelProperty(value = "카테고리 이미지 URL. 이미지가 없으면 null")
    private String categoryImageUrl;

    @ApiModelProperty(value = "UI 행 제목. 편의점/마트는 혜택 대상에 따라 분리한다", example = "편의점")
    private String displayName;

    @ApiModelProperty(value = "혜택 종류", example = "CASHBACK")
    private BenefitType benefitType;

    @ApiModelProperty(value = "혜택값 종류", example = "RATE")
    private ValueType valueType;

    @ApiModelProperty(value = "정률 또는 정액 혜택값", example = "10")
    private BigDecimal valueNumber;

    @ApiModelProperty(value = "혜택값 표시 단위", example = "PERCENT")
    private CardMonthlyBenefitUnit valueUnit;

    @ApiModelProperty(value = "적립 포인트 화폐명. 할인 혜택이면 null", example = "KB포인트리")
    private String pointCurrencyName;

    @ApiModelProperty(value = "혜택값 표시 문구", example = "10% 할인")
    private String valueLabel;

    @ApiModelProperty(value = "건당 최대 혜택값. 건당 한도가 없으면 null", example = "1000")
    private BigDecimal perTransactionLimitValue;

    @ApiModelProperty(value = "건당 최대 혜택 문구. 건당 한도가 없으면 null", example = "건당 최대 1,000원")
    private String perTransactionLimitLabel;

    /** 현재 혜택 서비스가 실제 적용되고 현재 카테고리에 해당하는 이번 달 거래 건수. */
    @ApiModelProperty(value = "현재 혜택이 실제 적용된 현재 카테고리의 거래 건수", example = "1")
    private long transactionCount;

    /** {@link #transactionCount}와 동일한 거래 집합의 할인 전 결제금액 합계. */
    @ApiModelProperty(value = "현재 혜택이 실제 적용된 현재 카테고리 거래의 결제금액 합계", example = "23800")
    private BigDecimal totalPaymentAmount;

    @ApiModelProperty(value = "동일 거래 집합에서 실제 수령한 혜택값. 적립은 포인트 단위", example = "1000")
    private BigDecimal receivedBenefitValue;

    @ApiModelProperty(value = "실제 수령 혜택 표시 문구", example = "총 1,000원 할인")
    private String receivedBenefitLabel;

    @ApiModelProperty(value = "현재 적용되는 모든 월 한도")
    private List<CardMonthlyBenefitLimitResponse> monthlyLimits;

    @ApiModelProperty(value = "월 한도 중 하나라도 소진됐으면 LIMIT_EXHAUSTED", example = "AVAILABLE")
    private CardMonthlyBenefitLimitStatus itemLimitStatus;
}
