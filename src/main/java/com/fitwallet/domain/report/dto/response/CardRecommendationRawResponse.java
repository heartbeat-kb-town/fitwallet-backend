package com.fitwallet.domain.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * 카드 추천 후보의 원본 데이터.
 * 서비스 레이어에서 카테고리별 예상 혜택을 계산하는 데만 쓰이고, API 응답으로 직접 나가지 않는다.
 * 최종 응답은 {@link CardRecommendationResponse}로 변환해서 내려간다.
 */
public class CardRecommendationRawResponse {
    private Long cardProductId;
    private String cardName;
    private String cardImageUrl;
    private Long categoryId;
    private String categoryName;
    private BigDecimal discountRate;
    private BigDecimal minPrevMonthSpend;
    private BigDecimal limitValue;
}