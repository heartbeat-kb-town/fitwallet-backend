package com.fitwallet.domain.card.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 카드 이용 실적 상세 조회 요청. */
@ApiModel(description = "카드 이용 실적 상세 조회 조건")
@Getter
@NoArgsConstructor
public class CardUsageSearchRequest {

    @ApiModelProperty(value = "조회 연월. 미입력 시 현재 월이며 미래 월은 허용하지 않는다",
            example = "2026-07")
    private String yearMonth;
}
