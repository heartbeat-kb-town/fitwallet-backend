package com.fitwallet.domain.card.dto.response;

import com.fitwallet.domain.card.dto.CardType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 내 카드 목록 한 건.
 * <p>
 * {@code user_card ⋈ card_product ⋈ issuer} 조인 결과를 MyBatis가 직접 채운다.
 * 어떤 테이블의 행도 아니므로 별도 엔티티를 두지 않는다.
 * <p>
 * 필드가 DB 컬럼과 이름만 다르고(snake_case → camelCase) 매핑되는 이유는
 * {@code mybatis-config.xml}의 {@code mapUnderscoreToCamelCase} 설정 때문이다.
 * <p>
 * MyBatis가 리플렉션으로 채우므로 {@code @Setter}는 붙이지 않는다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardListResponse {

    private Long userCardId;
    private String cardName;

    /** 카드사명 ({@code issuer.card_company_name}) */
    private String cardCompanyName;

    private CardType cardType;
    private String cardImageUrl;

    private String first4;
    private String last4;
    private LocalDate expiryDate;
    private Integer displayOrder;

    /** DEBIT 전용. CREDIT이면 null */
    private String bankName;
    /** DEBIT 전용. CREDIT이면 null */
    private BigDecimal balance;

    /** CREDIT 전용. DEBIT이면 null */
    private BigDecimal creditLimit;
    /** CREDIT 전용. DEBIT이면 null */
    private BigDecimal scheduledPaymentAmount;
}
