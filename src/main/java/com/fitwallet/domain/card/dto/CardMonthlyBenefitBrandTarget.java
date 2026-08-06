package com.fitwallet.domain.card.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 브랜드 범위 혜택 서비스와 대상 브랜드를 연결한 내부 조회 결과. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardMonthlyBenefitBrandTarget {

    private Long serviceId;
    private Long brandId;
    private String brandName;
    private String brandImageUrl;
}
