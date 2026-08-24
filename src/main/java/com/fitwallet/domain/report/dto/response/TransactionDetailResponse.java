package com.fitwallet.domain.report.dto.response;

import com.fitwallet.domain.card.dto.CardTransactionStatus;
import com.fitwallet.domain.report.dto.BenefitType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionDetailResponse {
    private LocalDateTime approvedAt;
    private String storeName;
    private BenefitType benefitType;   // CASHBACK=원화 할인, ACCUMULATE=포인트 적립
    private BigDecimal benefitRate;    // 정률(RATE) 혜택의 % 값. 정액(FIXED) 혜택이면 null
    private BigDecimal paidAmount;
    private BigDecimal benefitAmount;  // 단위는 benefitType이 결정 (CASHBACK=원, ACCUMULATE=포인트)
    private CardTransactionStatus transactionStatus;
}
