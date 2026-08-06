package com.fitwallet.domain.card.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 혜택 서비스가 실제 적용된 이번 달 거래를 대상별로 집계한 내부 조회 결과. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardMonthlyBenefitTargetUsage {

    private Long serviceId;
    private Long categoryId;
    private Long brandId;
    private long transactionCount;
    private BigDecimal totalPaymentAmount;
    private BigDecimal receivedBenefitAmount;
}
