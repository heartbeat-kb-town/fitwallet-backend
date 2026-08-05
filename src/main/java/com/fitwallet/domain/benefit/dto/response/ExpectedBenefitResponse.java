package com.fitwallet.domain.benefit.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 가맹점 예상 혜택 조회 최종 응답. 보유 카드가 없으면 {@code hasCard=false}, {@code cards}는 빈 배열이다.
 */
@ApiModel(description = "가맹점 예상 혜택 조회 응답")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpectedBenefitResponse {

    @ApiModelProperty(value = "조회 기준 가맹점. 보유 카드가 없어도 채워진다")
    private ExpectedBenefitStoreResponse store;

    @ApiModelProperty(value = "보유 카드 존재 여부. false면 cards는 빈 배열이다(빈 상태 화면 분기 조건)",
            example = "true")
    private Boolean hasCard;

    @ApiModelProperty(value = "카드별 판정 결과. AVAILABLE → CONDITION_NOT_MET → NO_BENEFIT 순으로 정렬돼 있다")
    private List<CardBenefitResponse> cards;
}
