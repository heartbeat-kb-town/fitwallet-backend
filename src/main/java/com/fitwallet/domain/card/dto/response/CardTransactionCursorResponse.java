package com.fitwallet.domain.card.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 무한 스크롤을 위한 카드 결제 내역 커서 응답.
 */
@ApiModel(description = "무한 스크롤용 결제 내역 묶음")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardTransactionCursorResponse {

    @ApiModelProperty(value = "이번 응답에 포함된 결제 내역")
    private List<CardTransactionItemResponse> content;

    /** 이번 응답에 실제로 포함된 결제 내역 개수. */
    @ApiModelProperty(value = "content의 실제 개수", example = "20")
    private Integer size;

    @ApiModelProperty(value = "다음 묶음 존재 여부", example = "true")
    private Boolean hasNext;

    /** 다음 묶음이 없으면 null이다. */
    @ApiModelProperty(value = "다음 묶음 조회용 커서. 마지막 묶음이면 null",
            example = "MXwyMDI2LTA3fDIwMjYtMDctMjBUMDk6MDg6NTV8MzQx")
    private String nextCursor;
}
