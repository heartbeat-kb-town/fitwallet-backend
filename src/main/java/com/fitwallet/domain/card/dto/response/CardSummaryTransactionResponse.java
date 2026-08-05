package com.fitwallet.domain.card.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 내 카드 탭의 최근 이용 내역 한 건. */
@ApiModel(description = "내 카드 탭의 최근 이용 내역")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardSummaryTransactionResponse {

    @ApiModelProperty(value = "결제 거래 ID", example = "348")
    private Long transactionId;

    @ApiModelProperty(value = "가맹점명. 가맹점 정보가 없으면 null",
            example = "스타벅스 강남점")
    private String storeName;

    @ApiModelProperty(value = "카테고리명. 가맹점 정보가 없으면 null", example = "카페")
    private String categoryName;

    @ApiModelProperty(value = "카테고리 이미지 URL. 가맹점 정보가 없으면 null",
            example = "https://cdn.fitwallet.app/categories/cafe.png")
    private String categoryImageUrl;

    @ApiModelProperty(value = "할인 적용 전 결제 금액(payment_transaction.amount)",
            example = "5800.00")
    private BigDecimal paymentAmount;

    @ApiModelProperty(value = "결제 일시(Asia/Seoul, ISO-8601)",
            example = "2026-08-05T14:30:00")
    private LocalDateTime paidAt;
}
