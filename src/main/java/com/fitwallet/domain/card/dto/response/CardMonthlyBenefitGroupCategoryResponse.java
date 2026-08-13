package com.fitwallet.domain.card.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 공동 한도 그룹에 포함된 DB 카테고리. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardMonthlyBenefitGroupCategoryResponse {

    private Long categoryId;
    private String categoryName;
    private String categoryImageUrl;
}
