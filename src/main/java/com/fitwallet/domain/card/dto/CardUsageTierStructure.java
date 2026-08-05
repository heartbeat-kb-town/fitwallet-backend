package com.fitwallet.domain.card.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** 카드 단위로 통합된 실적 구간 유형과 구간 목록. */
@Getter
@AllArgsConstructor
public class CardUsageTierStructure {

    private CardUsageTierType tierType;
    private List<CardUsageIntegratedTier> tiers;
}
