package com.fitwallet.domain.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 서비스 내부 계산용 (API 응답에 직접 안 나감).
 * 최종 응답은 {@link MissedCategoryDetailResponse}로 조립된다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MissedSummaryResponse {
    private BigDecimal totalMissedBenefit;
    private BigDecimal appUnusedAmount;
    private BigDecimal cardMismatchAmount;
}