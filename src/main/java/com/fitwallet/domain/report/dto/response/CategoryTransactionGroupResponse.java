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
public class CategoryTransactionGroupResponse {
    private Long categoryId;
    private String categoryName;
    private Integer usageCount;
    private BigDecimal benefitAmount;
    //여기에 카테고리별 거래내역 항목이 리스트로 들어감(이따 지우기)
    private List<TransactionDetailResponse> transactions;
}
