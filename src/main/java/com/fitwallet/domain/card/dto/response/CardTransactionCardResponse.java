package com.fitwallet.domain.card.dto.response;

import com.fitwallet.domain.card.dto.CardType;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카드별 결제 내역 화면에 표시할 보유 카드 기본 정보.
 */
@ApiModel(description = "결제 내역 화면의 보유 카드 정보")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardTransactionCardResponse {

    @ApiModelProperty(value = "보유 카드 ID(user_card_id)", example = "1")
    private Long cardId;

    @ApiModelProperty(value = "카드 상품 ID", example = "47")
    private Long cardProductId;

    @ApiModelProperty(value = "카드 상품명", example = "KB국민 청춘대로 톡톡카드")
    private String cardName;

    @ApiModelProperty(value = "카드사명", example = "KB국민카드")
    private String issuerName;

    @ApiModelProperty(value = "카드 이미지 URL. 등록된 이미지가 없으면 null",
            example = "https://cdn.fitwallet.app/cards/card.png")
    private String cardImageUrl;

    @ApiModelProperty(value = "카드 유형(CREDIT: 신용카드, DEBIT: 체크카드)", example = "CREDIT")
    private CardType cardType;

    @ApiModelProperty(value = "마스킹 표시용 카드번호 뒤 4자리", example = "8014")
    private String maskedRearNumber;
}
