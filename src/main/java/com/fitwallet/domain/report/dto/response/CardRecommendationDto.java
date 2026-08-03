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
public class CardRecommendationDto {
    private Long cardProductId;
    private String cardName;
    private String cardImageUrl;
    private BigDecimal expectedBenefit;
    private String description;
}
