package com.fitwallet.domain.card.dto.request;

import com.fitwallet.domain.card.dto.CardListSortType;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 보유 카드 목록 조회 요청. */
@ApiModel(description = "보유 카드 목록 조회 조건")
@Getter
@NoArgsConstructor
public class CardListSearchRequest {

    @ApiModelProperty(value = "카드 정렬 기준. 미입력 시 DISPLAY_ORDER",
            example = "RECENTLY_USED")
    private CardListSortType sort;
}
