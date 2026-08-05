package com.fitwallet.domain.card.dto.response;

import com.fitwallet.domain.card.dto.CardType;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 내 카드 탭에 표시할 보유 카드 기본 정보. */
@ApiModel(description = "내 카드 탭의 보유 카드 기본 정보")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardSummaryCardResponse {

    @ApiModelProperty(value = "보유 카드 ID(user_card_id)", example = "1")
    private Long cardId;

    @ApiModelProperty(value = "카드 상품 ID", example = "47")
    private Long cardProductId;

    @ApiModelProperty(value = "카드 상품명", example = "KB Gold & More")
    private String cardName;

    @ApiModelProperty(value = "카드사명", example = "KB국민카드")
    private String issuerName;

    @ApiModelProperty(value = "실제 카드 이미지 URL. 등록된 이미지가 없으면 null",
            example = "https://cdn.fitwallet.app/cards/kb-gold-more.png")
    private String cardImageUrl;

    @ApiModelProperty(value = "카드 유형(CREDIT: 신용카드, DEBIT: 체크카드)",
            example = "CREDIT")
    private CardType cardType;
}
