package com.fitwallet.domain.report.dto.response;

import com.fitwallet.domain.card.dto.response.CardListResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 리포트 메인 요약 응답. 한 화면(받은 혜택·놓친 혜택·카드 혜택 현황·카드 추천)을
 * 한 번의 호출로 그린다.
 * <ul>
 *   <li>받은 혜택 — {@code totalReceivedBenefit}(원화, 포인트 환산 포함) /
 *       {@code totalDiscountAmount}(원화 할인 합) / {@code totalPoint}(포인트 개수)</li>
 *   <li>놓친 혜택 — {@code totalMissedBenefit}(= 앱 미사용 + 카드 선택) /
 *       {@code appUnusedAmount} / {@code cardMismatchAmount}</li>
 *   <li>카드 혜택 현황 — {@code cards}: 보유 카드 목록(앞면). 캐러셀 렌더용이라
 *       카드 도메인의 {@link CardListResponse}를 그대로 재사용한다. 탭 상세는
 *       {@code /api/card/{cardId}/benefit}·{@code /event}로 지연 로딩한다.</li>
 *   <li>카드 추천 — {@code recommendations}</li>
 * </ul>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenefitSummaryResponse {
    private BigDecimal totalReceivedBenefit;
    private BigDecimal totalDiscountAmount;
    private BigDecimal totalPoint;

    private BigDecimal totalMissedBenefit;
    private BigDecimal appUnusedAmount;
    private BigDecimal cardMismatchAmount;

    private List<CardListResponse> cards;
    private List<CardRecommendationResponse> recommendations;
}
