package com.fitwallet.domain.card.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카드별 결제 내역 조회 요청.
 * <p>
 * 미입력된 조회 연월과 조회 개수의 기본값은 Service에서 적용한다.
 * 커서는 Service에서 디코딩하고 요청의 카드 및 조회 연월과 일치하는지 검증한다.
 */
@ApiModel(description = "카드별 세부 결제 내역 조회 조건")
@Getter
@NoArgsConstructor
public class CardTransactionSearchRequest {

    /** 조회 연월. 미입력 시 현재 월을 사용한다. */
    @ApiModelProperty(value = "조회 연월. 미입력 시 현재 월이며 현재 월 포함 최근 3개월만 허용한다",
            example = "2026-07")
    private String yearMonth;

    /** 한 번에 반환할 결제 내역 개수. 미입력 시 20개를 사용한다. */
    @ApiModelProperty(value = "한 번에 반환할 결제 내역 개수. 기본값 20, 허용 범위 1~100",
            example = "20")
    private Integer size;

    /** 다음 묶음 조회에 사용할 URL-safe Base64 커서. 첫 조회에서는 입력하지 않는다. */
    @ApiModelProperty(value = "다음 묶음 조회용 URL-safe Base64 커서. 첫 조회에서는 생략한다",
            example = "MXwyMDI2LTA3fDIwMjYtMDctMjBUMDk6MDg6NTV8MzQx")
    private String cursor;
}
