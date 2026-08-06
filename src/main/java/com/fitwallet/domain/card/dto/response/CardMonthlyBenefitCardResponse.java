package com.fitwallet.domain.card.dto.response;

import com.fitwallet.domain.card.dto.CardType;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 카드 월간 혜택 화면 상단에 표시하는 보유 카드 정보. */
@ApiModel(description = "카드 월간 혜택 조회 대상 카드 정보")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardMonthlyBenefitCardResponse {

    @ApiModelProperty(value = "보유 카드 ID(user_card_id)", example = "1")
    private Long userCardId;

    @ApiModelProperty(value = "카드 상품명", example = "KB Gold & More")
    private String cardName;

    @ApiModelProperty(value = "카드사명", example = "KB국민카드")
    private String issuerName;

    @ApiModelProperty(value = "카드 이미지 URL. 이미지가 없으면 null")
    private String cardImageUrl;

    @ApiModelProperty(value = "카드 유형", example = "CREDIT")
    private CardType cardType;
}
