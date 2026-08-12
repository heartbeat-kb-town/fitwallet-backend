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

    @ApiModelProperty(value = """
            카드별 판정 결과. 정렬 순서는 status 그룹(AVAILABLE → CONDITION_NOT_MET → NO_BENEFIT)
            → 기대혜택액 내림차순 → 카드 표시 순서다.
            `amount`를 보내지 않으면 기대혜택액을 모르므로 status 그룹까지만 정렬된다.""")
    private List<CardBenefitResponse> cards;
}
