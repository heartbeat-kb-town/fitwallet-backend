package com.fitwallet.domain.card.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 선택 월의 실적 인정·미반영 금액 요약. */
@ApiModel(description = "월별 이용 실적 금액 요약")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardUsageSummaryResponse {

    @ApiModelProperty(value = "실적 인정 거래의 할인 전 이용금액 합계", example = "325900.00")
    private BigDecimal recognizedAmount;

    @ApiModelProperty(value = "실적 미반영 거래의 할인 전 이용금액 합계", example = "70000.00")
    private BigDecimal excludedAmount;
}
