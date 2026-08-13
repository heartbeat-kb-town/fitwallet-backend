package com.fitwallet.domain.card.dto;

import com.fitwallet.domain.benefit.dto.BenefitScopeType;
import com.fitwallet.domain.benefit.dto.BenefitType;
import com.fitwallet.domain.benefit.dto.LimitBasis;
import com.fitwallet.domain.benefit.dto.ValueType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 월 한도 하나와 그 한도를 적용받는 혜택 서비스를 함께 조회한 내부 계산용 행. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardMonthlyBenefitRule {

    private Long serviceId;
    private String benefitName;
    private BenefitType benefitType;
    private ValueType valueType;
    private BigDecimal valueNumber;
    private BenefitScopeType scopeType;
    private BigDecimal benefitMinimumAmount;
    private BigDecimal benefitMaximumAmount;
    private BigDecimal perTransactionLimitAmount;
    private String pointCurrencyName;
    private BigDecimal krwPerPoint;

    private Long tierId;
    private Long servicePlanGroupId;
    private Long limitPlanGroupId;
    private Integer tierOrder;
    private BigDecimal tierMinimumAmount;
    private BigDecimal tierMaximumAmount;

    private Long limitId;
    private LimitBasis limitBasis;
    private BigDecimal limitValue;

    /** 여러 서비스가 같은 한도를 소비하는 plan group 소유 한도인지 여부. */
    public boolean isShared() {
        return limitPlanGroupId != null;
    }
}
