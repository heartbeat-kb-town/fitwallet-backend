package com.fitwallet.domain.benefit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 가맹점 예상 혜택 조회 최종 응답. 보유 카드가 없으면 {@code hasCard=false}, {@code cards}는 빈 배열이다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpectedBenefitResponse {

    private ExpectedBenefitStoreResponse store;
    private Boolean hasCard;
    private List<CardBenefitResponse> cards;
}
