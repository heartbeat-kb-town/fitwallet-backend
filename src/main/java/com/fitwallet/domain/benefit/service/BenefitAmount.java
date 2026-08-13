package com.fitwallet.domain.benefit.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

/**
 * 혜택 하나가 이번 결제에서 만들어 내는 금액. <b>같은 혜택을 두 축으로 들고 있다.</b>
 * <ul>
 *   <li>{@link #krw} — 원화 환산. 카드끼리 비교·정렬하고 {@code expectedAmount}로 응답에 나가는 값</li>
 *   <li>{@link #nativeAmount} — 네이티브 단위({@code CASHBACK}=원, {@code ACCUMULATE}=포인트 개수).
 *       {@code payment_transaction.discount_amount}에 적재하는 값</li>
 * </ul>
 * 둘을 함께 반환하는 이유는 <b>한쪽에서 다른 쪽을 역산할 수 없기 때문</b>이다.
 * {@code krw ÷ krwPerPoint}로 되돌리면 원 단위 절사가 이미 끝난 뒤라 포인트가 어긋난다 —
 * 1,751P × 0.8원 = 1,400.8원이 1,400원으로 절사되고, 되돌리면 1,750P가 나온다.
 * <p>
 * 응답 DTO가 아니라 계산기의 반환 타입이므로 {@code service} 패키지에 둔다.
 */
@Getter
@RequiredArgsConstructor
public class BenefitAmount {

    /** 원화 환산 기대혜택액. 항상 원 단위(scale 0)다. */
    private final BigDecimal krw;

    /** 행 자신의 {@code benefit_type} 단위 혜택값. {@code CASHBACK}이면 원, {@code ACCUMULATE}면 포인트 개수. */
    private final BigDecimal nativeAmount;
}
