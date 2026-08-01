package com.fitwallet.domain.benefit.dto.response;

import com.fitwallet.domain.benefit.dto.CardBenefitStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카드 한 장의 예상 혜택 판정 결과. {@code status=AVAILABLE}이면 {@code reason}은 null이고,
 * {@code status}가 {@code NO_BENEFIT}이거나 사유가 {@code PREV_SPEND_NOT_MET}이면 {@code benefit}은 null이다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardBenefitResponse {

    private Long userCardId;
    private String cardName;
    private String cardCompanyName;
    private String cardImageUrl;
    private CardBenefitStatus status;
    private BenefitReasonResponse reason;
    private BenefitDetailResponse benefit;
}
