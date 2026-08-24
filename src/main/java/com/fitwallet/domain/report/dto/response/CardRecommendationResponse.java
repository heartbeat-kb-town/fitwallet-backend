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
public class CardRecommendationResponse {
    private Long cardProductId;
    private String cardName;
    private String cardImageUrl;
    /** 카드사의 상품 상세 페이지 주소(card_product.detail_url). 아직 URL이 없는 카드는 null이다. */
    private String detailUrl;
    private BigDecimal expectedBenefit;
    private String description;
}
