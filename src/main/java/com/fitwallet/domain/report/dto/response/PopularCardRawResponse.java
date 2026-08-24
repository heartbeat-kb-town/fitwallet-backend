package com.fitwallet.domain.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 콜드스타트 폴백용 인기 카드 원본. 보유 수 상위의 미보유 카드 정보만 담는다.
 * 거래 내역이 없어 예상 혜택을 계산할 수 없을 때 {@link CardRecommendationResponse}로 변환된다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PopularCardRawResponse {
    private Long cardProductId;
    private String cardName;
    private String cardImageUrl;
    private String detailUrl;
}
