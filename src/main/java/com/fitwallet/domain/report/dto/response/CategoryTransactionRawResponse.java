package com.fitwallet.domain.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 서비스 내부 계산용 (API 응답에 직접 안 나감).
 * categoryId 기준으로 그룹핑되어 {@link CategoryTransactionGroupResponse}로 조립된다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryTransactionRawResponse {
    private Long categoryId;
    private String categoryName;
    private LocalDateTime approvedAt;
    private String storeName;
    private BigDecimal discountRate;
    private BigDecimal paidAmount;
    private BigDecimal benefitAmount;
}