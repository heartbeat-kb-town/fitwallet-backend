package com.fitwallet.domain.card.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 마이데이터 연동으로 받아온 카드 한 장. 우리 API 응답 DTO가 아니라 외부 데이터 형태다.
 * <p>
 * CREDIT/DEBIT 구분은 여기 담지 않는다 — {@code cardProductId}로 이미 결정되는 값이라
 * 따로 들고 있으면 둘이 어긋날 수 있다. 저장할 때 {@code card_product.card_type}을 조회해서 판단한다.
 */
@Getter
@Builder
@AllArgsConstructor
public class MyDataCard {

    private final Long cardProductId;
    private final String first4;
    private final String last4;
    private final LocalDate expiryDate;

    /** DEBIT일 때만 채워진다. CREDIT이면 null. */
    private final String bankName;
    private final BigDecimal balance;

    /** CREDIT일 때만 채워진다. DEBIT이면 null. */
    private final BigDecimal creditLimit;
    private final BigDecimal scheduledPaymentAmount;

    private final List<MyDataTransaction> transactions;
}
