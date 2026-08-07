package com.fitwallet.domain.card.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 카드별 이벤트 조회 화면에 표시할 보유 카드 정보. */
@ApiModel(description = "카드별 이벤트 조회의 보유 카드 정보")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardEventCardResponse {

    @ApiModelProperty(value = "보유 카드 ID(user_card_id)", example = "1")
    private Long cardId;

    @ApiModelProperty(value = "카드 상품 ID", example = "47")
    private Long cardProductId;

    @ApiModelProperty(value = "카드 상품명", example = "KB국민 청춘대로 톡톡카드")
    private String cardName;

    @ApiModelProperty(value = "카드사명", example = "KB국민카드")
    private String issuerName;
}
