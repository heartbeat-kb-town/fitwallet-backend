package com.fitwallet.domain.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 카테고리별 이번 달 지출액.
 * 서비스 내부 계산용 (API 응답에 직접 안 나감, Mapper 결과만 담음).
 * 카드 추천 계산({@link CardRecommendationRawDto}와 조합)에 쓰인다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategorySpendDto {
    private Long categoryId;
    private String categoryName;
    private BigDecimal spendAmount;
}