package com.fitwallet.domain.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 받은 혜택 요약(리포트 메인 "받은 혜택" 카드).
 * 매퍼가 한 번의 집계로 세 값을 함께 채운다.
 * <ul>
 *   <li>{@code totalReceivedBenefit} — 총 받은 혜택(원화). 원화 할인 합 +
 *       포인트를 {@code point_currency.krw_per_point}로 환산한 금액.</li>
 *   <li>{@code totalDiscountAmount} — 총 할인 금액(원화). 포인트가 아닌 혜택의 합.</li>
 *   <li>{@code totalPoint} — 총 포인트(포인트 개수, 환산 전).</li>
 * </ul>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceivedBenefitSummaryResponse {
    private BigDecimal totalReceivedBenefit;
    private BigDecimal totalDiscountAmount;
    private BigDecimal totalPoint;
}
