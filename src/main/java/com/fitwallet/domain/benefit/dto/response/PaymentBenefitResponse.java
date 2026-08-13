package com.fitwallet.domain.benefit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 결제 확정에 필요한 혜택 판정 결과 한 건. <b>화면에 나가지 않는다</b> —
 * benefit 도메인이 payment 도메인에 넘기는 전달 전용 DTO다.
 * <p>
 * {@link BenefitDetailResponse}(프론트 응답)와 따로 두는 이유는 payment가 쓰는 세 값이
 * 그쪽에 없기 때문이다 — {@code benefitType}·{@code nativeAmount}·{@code tierId}.
 * 응답 DTO를 넓히면 API 계약이 바뀌므로 통로를 나눴다.
 * <p>
 * {@code AVAILABLE}인 카드만 담기고, <b>{@code expectedAmount} 내림차순으로 정렬돼 온다</b> —
 * 놓친 혜택은 자기 카드를 뺀 첫 번째 원소다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentBenefitResponse {

    private Long userCardId;

    private Long benefitServiceId;

    /**
     * 적용 혜택의 한도 그룹. {@code payment_transaction.applied_tier_id}에 적재해야
     * {@code BenefitMapper.findUsage}가 이 결제를 한도 사용량으로 집계한다.
     * 한도가 아예 걸려 있지 않으면 {@code null}이다.
     */
    private Long tierId;

    /**
     * 원화 환산 기대혜택액. 카드 비교·정렬, 놓친 혜택 계산,
     * {@code payment_transaction.final_amount} 차감은 전부 이 축에서 한다.
     */
    private BigDecimal expectedAmount;

    /**
     * 네이티브 단위 혜택값. {@code payment_transaction.discount_amount}에 그대로 들어간다.
     * 단위는 {@code benefitServiceId}가 가리키는 행의 {@code benefit_type}으로 해석한다
     * ({@code CASHBACK}=원, {@code ACCUMULATE}=포인트 개수).
     */
    private BigDecimal nativeAmount;
}
