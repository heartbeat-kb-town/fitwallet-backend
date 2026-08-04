package com.fitwallet.domain.card.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 플랜 그룹 또는 개별 혜택에 연결된 정규화 전 원본 실적 구간. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardUsageSourceTier {

    private Long sourceTierId;
    private Long planGroupId;
    private Long benefitId;
    private Integer sourceTierOrder;
    private BigDecimal minimumAmount;
    private BigDecimal maximumAmount;
}
