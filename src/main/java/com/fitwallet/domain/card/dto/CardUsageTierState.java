package com.fitwallet.domain.card.dto;

import com.fitwallet.domain.card.dto.response.CardUsageTierResponse;
import com.fitwallet.domain.card.dto.response.CardUsageTierSummaryResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/** 인정금액을 통합 구간에 적용해 계산한 현재·다음 구간과 진행 상태. */
@Getter
@AllArgsConstructor
public class CardUsageTierState {

    private CardUsagePerformanceStatus performanceStatus;
    private CardUsageTierSummaryResponse currentTier;
    private CardUsageTierSummaryResponse nextTier;
    private BigDecimal amountUntilNextTier;
    private BigDecimal tierProgressRate;
    private List<CardUsageTierResponse> tiers;
}
