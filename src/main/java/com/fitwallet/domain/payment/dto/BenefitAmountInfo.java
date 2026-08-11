package com.fitwallet.domain.payment.dto;

import com.fitwallet.domain.benefit.dto.ValueType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenefitAmountInfo {
    private ValueType valueType;
    private BigDecimal valueNumber;
    private BigDecimal perTxLimitAmount;
}