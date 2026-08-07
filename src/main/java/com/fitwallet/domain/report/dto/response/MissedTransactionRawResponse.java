package com.fitwallet.domain.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 서비스 내부 계산용 (API 응답에 직접 안 나감).
 * categoryId 기준으로 그룹핑되어 {@link MissedCategoryGroupResponse}로 조립된다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MissedTransactionRawResponse {
    private Long categoryId;
    private String categoryName;
    private LocalDateTime approvedAt;
    private String storeName;
    private String usedCardName;
    private BigDecimal paidAmount;
    private String alternativeCardName;
    private BigDecimal discountRate;
    private BigDecimal diffAmount;
}