package com.fitwallet.domain.card.dto.response;

import com.fitwallet.domain.card.dto.CardTransactionStatus;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 카드별 결제 내역 목록의 한 건.
 * <p>
 * 가맹점을 특정하지 못한 거래는 가맹점과 카테고리 필드가 null일 수 있다.
 */
@ApiModel(description = "결제 내역 한 건")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardTransactionItemResponse {

    @ApiModelProperty(value = "결제 거래 ID", example = "348")
    private Long transactionId;

    /** 가맹점 정보가 없는 거래이면 null이다. */
    @ApiModelProperty(value = "가맹점명. 가맹점 정보가 없으면 null", example = "GS25 군자능동빌리지점")
    private String storeName;

    /** 가맹점 정보가 없는 거래이면 null이다. */
    @ApiModelProperty(value = "카테고리명. 가맹점 정보가 없으면 null", example = "편의점/마트")
    private String categoryName;

    /** 가맹점 정보가 없는 거래이면 null이다. */
    @ApiModelProperty(value = "카테고리 이미지 URL. 가맹점 정보가 없으면 null",
            example = "https://cdn.fitwallet.app/categories/mart.png")
    private String categoryImageUrl;

    /** 할인 적용 전 결제 금액인 payment_transaction.amount를 반환한다. */
    @ApiModelProperty(value = "할인 적용 전 결제 금액(payment_transaction.amount)", example = "24900.00")
    private BigDecimal paymentAmount;

    @ApiModelProperty(value = "결제 일시(Asia/Seoul, ISO-8601)", example = "2026-07-21T21:16:30")
    private LocalDateTime paidAt;

    @ApiModelProperty(value = "거래 상태", example = "CANCELED")
    private CardTransactionStatus transactionStatus;

    @ApiModelProperty(value = "카드 실적 인정 여부. false일 때만 실적 미인정 배지를 표시한다", example = "true")
    private Boolean performanceIncluded;
}
