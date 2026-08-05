package com.fitwallet.domain.card.dto;

import com.fitwallet.domain.card.dto.response.CardUsageBenefitResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** 통합 실적 구간과 해당 구간에서 적용되는 혜택 목록. */
@Getter
@AllArgsConstructor
public class CardUsageTierBenefitGroup {

    private CardUsageIntegratedTier tier;
    private List<CardUsageBenefitResponse> benefits;
}
