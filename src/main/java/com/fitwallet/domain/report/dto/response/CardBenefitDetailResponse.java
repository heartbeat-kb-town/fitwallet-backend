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
    private String cardImageUrl;      // 추가
    private String maskedCardNumber;
    private BigDecimal totalDiscount;
    private BigDecimal totalSpend;
    //여기에 받은 카테고리가 리스트로 들어감. (이따 지우기)
    private List<CategoryTransactionGroupResponse> categories;
}
