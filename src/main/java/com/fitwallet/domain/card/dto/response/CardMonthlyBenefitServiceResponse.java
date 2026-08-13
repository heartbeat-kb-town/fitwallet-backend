package com.fitwallet.domain.card.dto.response;

import com.fitwallet.domain.benefit.dto.BenefitScopeType;
import com.fitwallet.domain.benefit.dto.BenefitType;
import com.fitwallet.domain.benefit.dto.ValueType;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitLimitStatus;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitUnit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** 공동 한도 그룹 안의 혜택 서비스와 현재 월 사용 현황. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardMonthlyBenefitServiceResponse {

    private Long benefitServiceId;
    private String benefitName;
    private String displayQualifier;
    private BenefitScopeType scopeType;
    private BenefitType benefitType;
    private ValueType valueType;
    private BigDecimal valueNumber;
    private CardMonthlyBenefitUnit valueUnit;
    private String pointCurrencyName;
    private String valueLabel;
    private BigDecimal perTransactionLimitValue;
    private String perTransactionLimitLabel;
    private long transactionCount;
    private BigDecimal totalPaymentAmount;
    private BigDecimal receivedBenefitValue;
    private String receivedBenefitLabel;
    private BigDecimal sharedLimitUsedValue;
    private String sharedLimitUsedLabel;
    private BigDecimal unattributedSharedLimitUsedValue;
    private String unattributedSharedLimitUsedLabel;
    private List<CardMonthlyBenefitLimitResponse> serviceMonthlyLimits;
    private CardMonthlyBenefitLimitStatus serviceLimitStatus;
    private List<CardMonthlyBenefitTargetResponse> targets;
}
