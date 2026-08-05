package com.fitwallet.domain.card.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 카드 유형에 따라 내 카드 탭 상단에 표시할 금액 정보. */
@ApiModel(description = "카드 유형별 상단 금액 정보")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardSummaryAmountResponse {

    @ApiModelProperty(value = "신용카드의 현재 월 결제 이용금액. 체크카드이면 null",
            example = "1240000.00")
    private BigDecimal creditUsageAmount;

    @ApiModelProperty(value = "체크카드의 현재 잔액. 신용카드이면 null",
            example = "1250000.00")
    private BigDecimal balance;

    @ApiModelProperty(value = "체크카드의 결제 은행명. 신용카드이면 null",
            example = "KB국민은행")
    private String bankName;

    @ApiModelProperty(value = "금액 반영 기준일. 신용카드는 어제, 체크카드는 오늘",
            example = "2026-08-05")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate asOfDate;
}
