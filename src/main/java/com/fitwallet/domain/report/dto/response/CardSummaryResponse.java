package com.fitwallet.domain.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 서비스 내부 계산용 (API 응답에 직접 안 나감).
 * 최종 응답은 {@link CardBenefitDetailResponse}로 조립된다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardSummaryResponse {
    private String cardName;
    private String cardImageUrl;
    private String maskedCardNumber;
    private BigDecimal totalDiscount;   // 총 할인 금액 (원화, CASHBACK 합)
    private BigDecimal totalPoint;      // 총 포인트 (P, ACCUMULATE 합)
    private BigDecimal totalSpend;      // 총 사용 금액 (원화, 전체 결제 합)
}
