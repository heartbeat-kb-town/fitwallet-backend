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
    private BigDecimal totalDiscount;
    private BigDecimal totalSpend;
    private List<CategoryTransactionGroupResponse> categories;
}
