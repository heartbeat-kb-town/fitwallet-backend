package com.fitwallet.domain.card.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fitwallet.domain.card.dto.CardType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 보유 카드 목록 한 건. 필드는 API 명세("보유 카드 목록 조회")를 따른다.
 * <p>
 * {@code user_card ⋈ card_product} 조인 결과를 MyBatis가 직접 채운다.
 * 어떤 테이블의 행도 아니므로 별도 엔티티를 두지 않는다.
 * <p>
 * MyBatis가 리플렉션으로 채우므로 {@code @Setter}는 붙이지 않는다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardListResponse {

    private Long userCardId;
    private Long cardProductId;
    private String cardName;

    /** 카드사명. CardTransactionCardInfo/CardUsageCardInfo와 같은 이름을 쓴다. */
    private String issuerName;

    /** 카드 이미지 URL. 카드 상품에 이미지가 없으면 null. */
    private String cardImageUrl;

    /**
     * 신용/체크 구분. 명세 예시에는 없지만 화면에서 구분 표시가 필요해 추가했다.
     * (명세 예시가 balance와 creditLimit을 동시에 채우고 있어 예시 자체가 개략적이다)
     */
    private CardType cardType;

    /** 카드번호 앞 4자리 (DB 컬럼 {@code first4}) */
    private String maskedFrontNumber;
    /** 카드번호 뒤 4자리 (DB 컬럼 {@code last4}) */
    private String maskedRearNumber;

    /** DB는 DATE지만 명세와 실제 카드 표기를 따라 연-월로 내린다. */
    @JsonFormat(pattern = "yyyy-MM")
    private LocalDate expiryDate;

    /** DEBIT 전용. CREDIT이면 null */
    private String bankName;
    /** DEBIT 전용. CREDIT이면 null */
    private BigDecimal balance;

    /** CREDIT 전용. DEBIT이면 null */
    private BigDecimal creditLimit;
    /** CREDIT 전용. 현재 월 1일부터 전날까지 승인 거래의 final_amount 합계이며 DEBIT이면 null. */
    private BigDecimal scheduledPaymentAmount;

    private Integer displayOrder;
}
