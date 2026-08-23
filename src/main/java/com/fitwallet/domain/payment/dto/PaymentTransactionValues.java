package com.fitwallet.domain.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 결제가 승인됐을 때 {@code payment_transaction}에 적재할 값 묶음.
 * <p>
 * 승인 여부는 {@code Math.random()} 목업이 정하지만 이 값들은 그렇지 않다 —
 * 계산을 따로 떼어 두면 승인 분기 없이 그대로 검증할 수 있다.
 * <p>
 * <b>단위가 섞여 있다.</b> {@code discountAmount}만 네이티브(CASHBACK=원, ACCUMULATE=포인트 개수)이고
 * 나머지 금액은 전부 원화다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransactionValues {

    private Long appliedBenefitServiceId;

    /** 한도 사용량 집계 키. 없으면 이 결제는 어떤 한도에도 쌓이지 않는다. */
    private Long appliedTierId;

    /** 적용된 혜택값(네이티브 단위). */
    private BigDecimal discountAmount;

    /** 혜택을 원화로 환산해 뺀 실제 승인 금액. */
    private BigDecimal finalAmount;

    private Long betterUserCardId;
    private BigDecimal alternativeDiscountAmount;
    private BigDecimal missedAmount;
}
