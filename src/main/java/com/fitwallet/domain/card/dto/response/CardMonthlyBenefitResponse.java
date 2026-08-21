package com.fitwallet.domain.card.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/** 보유 카드 한 장의 월간 혜택 현황 응답. */
@ApiModel(description = "카드별 월간 혜택 현황")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardMonthlyBenefitResponse {

    @ApiModelProperty(value = "조회 대상 카드")
    private CardMonthlyBenefitCardResponse card;

    @ApiModelProperty(value = "혜택 집계 연월(yyyy-MM)", example = "2026-08")
    private String yearMonth;

    @ApiModelProperty(value = "혜택 집계 기준일. 오늘 거래까지 포함한다", example = "2026-08-21")
    private LocalDate asOfDate;

    @ApiModelProperty(value = "원화 환산 월간 혜택 요약")
    private CardMonthlyBenefitSummaryResponse monthlySummary;

    @ApiModelProperty(value = "전월 실적 적용 상태")
    private CardMonthlyBenefitPerformanceResponse performance;

    @ApiModelProperty(value = "업종 범위 월간 혜택. 없으면 빈 배열")
    private List<CardMonthlyCategoryBenefitResponse> categoryBenefits;

    @ApiModelProperty(value = "브랜드 범위 월간 혜택. 없으면 빈 배열")
    private List<CardMonthlyBrandBenefitResponse> brandBenefits;

    /** 현재 실적 구간에서 선택된 공동 월 한도 그룹. 없으면 빈 배열. */
    private List<CardMonthlyBenefitSharedLimitGroupResponse> sharedLimitGroups;
}
