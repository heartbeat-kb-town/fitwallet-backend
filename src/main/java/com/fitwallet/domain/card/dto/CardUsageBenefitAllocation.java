package com.fitwallet.domain.card.dto;

import com.fitwallet.domain.card.dto.response.CardUsageBenefitResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** 실적 조건 없는 기본 혜택과 통합 구간별 혜택 배치 결과. */
@Getter
@AllArgsConstructor
public class CardUsageBenefitAllocation {

    private List<CardUsageBenefitResponse> defaultBenefits;
    private List<CardUsageTierBenefitGroup> tierBenefitGroups;
}
