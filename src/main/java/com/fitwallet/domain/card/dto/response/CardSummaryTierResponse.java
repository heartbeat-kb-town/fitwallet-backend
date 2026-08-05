package com.fitwallet.domain.card.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 내 카드 탭에 표시할 실적 구간 이름. */
@ApiModel(description = "내 카드 탭의 실적 구간 요약")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardSummaryTierResponse {

    @ApiModelProperty(value = "화면 표시용 구간명", example = "1구간")
    private String tierName;
}
