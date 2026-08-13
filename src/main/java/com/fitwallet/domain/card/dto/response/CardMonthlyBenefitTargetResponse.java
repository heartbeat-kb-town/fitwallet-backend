package com.fitwallet.domain.card.dto.response;

import com.fitwallet.domain.benefit.dto.BenefitScopeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 공동 한도 그룹 안에서 혜택 서비스 하나가 적용되는 카테고리 또는 브랜드. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardMonthlyBenefitTargetResponse {

    private BenefitScopeType scopeType;
    private Long targetId;
    private String targetName;
    private String targetImageUrl;
    private Long categoryId;
    private long transactionCount;
    private BigDecimal totalPaymentAmount;
    private BigDecimal receivedBenefitValue;
    private String receivedBenefitLabel;
    private BigDecimal sharedLimitUsedValue;
    private String sharedLimitUsedLabel;
}
