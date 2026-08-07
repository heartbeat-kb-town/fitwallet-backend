package com.fitwallet.domain.card.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/** 카드별 진행 중 이벤트 조회의 최상위 응답. */
@ApiModel(description = "카드별 진행 중 이벤트 조회 응답")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardEventResponse {

    @ApiModelProperty(value = "이벤트를 조회한 보유 카드 정보")
    private CardEventCardResponse card;

    @ApiModelProperty(value = "진행 중 이벤트 수", example = "2")
    private Integer eventCount;

    @ApiModelProperty(value = "진행 중 이벤트 목록")
    private List<CardEventItemResponse> events;
}
