package com.fitwallet.domain.card.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fitwallet.domain.card.dto.CardEventTargetType;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** 카드별 이벤트 조회 결과의 이벤트 한 건. MyBatis가 조회 결과를 직접 채운다. */
@ApiModel(description = "카드별 이벤트 조회의 이벤트 항목")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardEventItemResponse {

    @ApiModelProperty(value = "이벤트 ID", example = "3")
    private Long eventId;

    @ApiModelProperty(value = "이벤트 적용 대상(CARD_PRODUCT 또는 ISSUER)", example = "CARD_PRODUCT")
    private CardEventTargetType targetType;

    @ApiModelProperty(value = "이벤트 표시 문구", example = "KB국민 청춘대로 톡톡 CGV 모바일 예매 시 1인 5,000원 할인(월 2회)")
    private String summary;

    @ApiModelProperty(value = "이벤트 시작일", example = "2026-07-01")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startsAt;

    @ApiModelProperty(value = "이벤트 종료일", example = "2026-07-31")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endsAt;

    @ApiModelProperty(value = "종료일까지 남은 일수", example = "7")
    private Long daysRemaining;

    @ApiModelProperty(value = "외부 이벤트 상세 페이지 URL", example = "https://card.kbcard.com/", allowEmptyValue = true)
    private String detailUrl;

    @ApiModelProperty(value = "상세 페이지 이동 가능 여부", example = "true")
    private Boolean detailAvailable;
}
