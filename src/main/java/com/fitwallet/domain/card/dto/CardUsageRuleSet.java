package com.fitwallet.domain.card.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** 중복과 원본 데이터 정합성 검증을 마친 혜택 및 실적 구간 모음. */
@Getter
@AllArgsConstructor
public class CardUsageRuleSet {

    private List<CardUsageBenefitDefinition> benefits;
    private List<CardUsageSourceTier> sourceTiers;
}
