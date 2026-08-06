package com.fitwallet.domain.card.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 업종 범위 혜택 서비스와 대상 카테고리를 연결한 내부 조회 결과. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardMonthlyBenefitCategoryTarget {

    private Long serviceId;
    private Long categoryId;
    private String categoryName;
    private String categoryImageUrl;
}
