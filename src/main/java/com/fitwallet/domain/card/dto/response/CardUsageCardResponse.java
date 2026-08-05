package com.fitwallet.domain.card.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 이용 실적 화면에 표시할 최소 카드 정보. */
@ApiModel(description = "이용 실적 화면의 카드 정보")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardUsageCardResponse {

    @ApiModelProperty(value = "카드 상품명", example = "KB국민 노리 체크카드")
    private String cardName;

    @ApiModelProperty(value = "카드사명", example = "KB국민카드")
    private String issuerName;
}
