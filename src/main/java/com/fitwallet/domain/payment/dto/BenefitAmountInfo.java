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
    /**
     * 건당 혜택 상한. {@code benefit_service.per_tx_limit_value} 를 그대로 받는다.
     * <p>
     * 단위는 원화가 아닐 수 있다 — ACCUMULATE 혜택에서는 포인트 개수다(스키마 v27 주석 참고).
     * 필드명이 컬럼명과 어긋나면 {@code resultType} 자동 매핑이 값을 조용히 버려 상한이
     * 통째로 무력화되므로, 컬럼을 바꿀 때는 이 이름도 함께 바꿔야 한다.
     */
    private BigDecimal perTxLimitValue;
}