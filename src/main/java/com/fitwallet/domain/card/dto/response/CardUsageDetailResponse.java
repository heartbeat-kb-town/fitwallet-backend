package com.fitwallet.domain.card.dto.response;

import com.fitwallet.domain.card.dto.CardUsagePerformanceStatus;
import com.fitwallet.domain.card.dto.CardUsageTierType;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** 카드의 선택 월 이용 실적, 통합 구간과 구간별 혜택 응답. */
@ApiModel(description = "카드 이용 실적 상세 조회 결과")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardUsageDetailResponse {

    @ApiModelProperty(value = "이용 실적 화면에 표시할 카드명과 카드사명")
    private CardUsageCardResponse card;

    @ApiModelProperty(value = "조회 연월(yyyy-MM)", example = "2026-07")
    private String yearMonth;

    @ApiModelProperty(value = "현재 월을 포함하여 조회할 수 있는 최근 3개월",
            example = "[\"2026-08\", \"2026-07\", \"2026-06\"]")
    private List<String> availableYearMonths;

    @ApiModelProperty(value = "카드 상품의 통합 실적 구간 유형", example = "MULTIPLE_TIERS")
    private CardUsageTierType tierType;

    @ApiModelProperty(value = "조회 월의 이용 실적 달성 상태", example = "ACHIEVED")
    private CardUsagePerformanceStatus performanceStatus;

    @ApiModelProperty(value = "실적 인정·미반영 금액 요약")
    private CardUsageSummaryResponse usageSummary;

    @ApiModelProperty(value = "현재 적용 구간. 실적 조건이 없으면 null")
    private CardUsageTierSummaryResponse currentTier;

    @ApiModelProperty(value = "다음 실적 구간. 최고 구간이거나 실적 조건이 없으면 null")
    private CardUsageTierSummaryResponse nextTier;

    @ApiModelProperty(value = "다음 구간까지 필요한 인정금액. 다음 구간이 없으면 null",
            example = "174100.00")
    private BigDecimal amountUntilNextTier;

    @ApiModelProperty(value = "전체 통합 구간 바에서 현재 실적이 위치하는 진행률. 소수점 첫째 자리",
            example = "53.3")
    private BigDecimal tierProgressRate;

    @ApiModelProperty(value = "카드 상품 단위로 통합한 실적 구간 목록")
    private List<CardUsageTierResponse> tiers;

    @ApiModelProperty(value = "실적 조건 없이 적용되는 기본 혜택 목록")
    private List<CardUsageBenefitResponse> defaultBenefits;
}
