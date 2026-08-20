package com.fitwallet.domain.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 카테고리 × 월별 지출액 원본. 최근 N개월 지출을 (카테고리, 연월) 단위로 담는다.
 * 추천 엔진이 카테고리별 월 지출들을 모아 중앙값(예상 월 지출)을 계산하는 데만 쓰이고,
 * API 응답으로 직접 나가지 않는다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyCategorySpendRawResponse {
    private Long categoryId;
    private String categoryName;
    private String yearMonth;      // yyyy-MM
    private BigDecimal spendAmount;
}
