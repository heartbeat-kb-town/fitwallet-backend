package com.fitwallet.domain.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreQrScanResponse {
    private String paymentId;
    private Long storeId;
    private String storeName;
    private BigDecimal amount;
}