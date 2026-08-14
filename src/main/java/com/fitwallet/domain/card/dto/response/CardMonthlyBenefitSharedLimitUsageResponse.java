package com.fitwallet.domain.card.dto.response;

import com.fitwallet.domain.benefit.dto.BenefitScopeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 공동 월 한도 사용량의 서비스·대상별 기여도. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardMonthlyBenefitSharedLimitUsageResponse {

    private Long benefitServiceId;
    private BenefitScopeType scopeType;
    private Long targetId;
    private String targetName;
    private Long categoryId;
    private String displayQualifier;
    private boolean unattributed;
    private BigDecimal usedValue;
    private String usedLabel;
}
