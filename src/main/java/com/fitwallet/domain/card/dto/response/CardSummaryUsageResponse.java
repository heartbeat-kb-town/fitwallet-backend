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

/** 기존 카드 이용 실적 상세 계산 결과에서 내 카드 탭에 필요한 값만 추린 요약. */
@ApiModel(description = "현재 월 카드 이용 실적 요약")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardSummaryUsageResponse {

    @ApiModelProperty(value = "실적 조회 연월(yyyy-MM)", example = "2026-08")
    private String yearMonth;

    @ApiModelProperty(value = "카드 상품의 실적 구간 유형", example = "MULTIPLE_TIERS")
    private CardUsageTierType tierType;

    @ApiModelProperty(value = "현재 월 이용 실적 달성 상태", example = "ACHIEVED")
    private CardUsagePerformanceStatus performanceStatus;

    @ApiModelProperty(value = "실적 인정 거래의 할인 적용 후 결제금액 합계. 적립 혜택은 결제금액에서 차감하지 않으며, 실적 조건이 없으면 null",
            example = "500000.00")
    private BigDecimal recognizedAmount;

    @ApiModelProperty(value = "현재 적용 구간. 실적 조건이 없으면 null")
    private CardSummaryTierResponse currentTier;

    @ApiModelProperty(value = "다음 실적 구간. 최고 구간이거나 실적 조건이 없으면 null")
    private CardSummaryTierResponse nextTier;

    @ApiModelProperty(value = "다음 구간까지 필요한 인정금액. 다음 구간이 없으면 null",
            example = "500000.00")
    private BigDecimal amountUntilNextTier;

    @ApiModelProperty(value = "전체 통합 구간 바에서 현재 실적이 위치하는 진행률. 소수점 첫째 자리",
            example = "53.3")
    private BigDecimal tierProgressRate;
}
