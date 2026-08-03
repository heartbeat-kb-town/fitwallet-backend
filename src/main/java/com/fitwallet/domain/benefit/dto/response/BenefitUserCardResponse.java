package com.fitwallet.domain.benefit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 예상 혜택 판정 대상 보유 카드 한 건. {@code user_card ⋈ card_product ⋈ issuer} 조인 결과를
 * MyBatis가 직접 채운다. 어떤 테이블의 행도 아니므로 별도 엔티티를 두지 않는다.
 * <p>
 * MyBatis가 리플렉션으로 채우므로 {@code @Setter}는 붙이지 않는다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenefitUserCardResponse {

    private Long userCardId;
    private Long cardProductId;

    /** 카드 목록 정렬 기준(상태 그룹 다음 순위). */
    private Integer displayOrder;

    private String cardName;
    private String cardImageUrl;
    private String cardCompanyName;
}
