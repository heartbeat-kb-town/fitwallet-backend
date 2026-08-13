package com.fitwallet.domain.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardBenefitDetailResponse {
    private String cardName;
    private String cardImageUrl;
    private String maskedCardNumber;
    private BigDecimal totalDiscount;   // 총 할인 금액 (원화, CASHBACK 합)
    private BigDecimal totalPoint;      // 총 포인트 (P, ACCUMULATE 합)
    private BigDecimal totalSpend;      // 총 사용 금액 (원화, 전체 결제 합)
    private List<CategoryTransactionGroupResponse> categories;
}
