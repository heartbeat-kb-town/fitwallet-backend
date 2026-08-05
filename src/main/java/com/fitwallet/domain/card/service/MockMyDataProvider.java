package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.MyDataCard;
import com.fitwallet.domain.card.dto.MyDataTransaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 실제 마이데이터 API 대신 시드 데이터를 기반으로 카드와 거래내역 목데이터를 반환한다.
 *
 * cardProductId와 storeId는 각각 card_product, store 테이블에
 * 미리 저장된 ID를 사용한다.
 */
@Component
public class MockMyDataProvider implements MyDataProvider {

    @Override
    public List<MyDataCard> fetchCards(Long userId) {
        return List.of(
                MyDataCard.builder()
                        .cardProductId(47L) // KB국민 청춘대로 톡톡카드 (CREDIT)
                        .first4("5327")
                        .last4("9001")
                        .expiryDate(LocalDate.of(2029, 6, 30))
                        .creditLimit(BigDecimal.valueOf(3_000_000))
                        .scheduledPaymentAmount(BigDecimal.valueOf(280_000))
                        .transactions(List.of(
                                transaction(12_000, 2L, LocalDateTime.of(2026, 5, 3, 8, 40)),
                                transaction(15_000, 1L, LocalDateTime.of(2026, 5, 12, 9, 10)),
                                transaction(27_500, 4L, LocalDateTime.of(2026, 5, 19, 19, 20)),
                                transaction(9_800, 5L, LocalDateTime.of(2026, 6, 2, 8, 5)),
                                transaction(42_000, 3L, LocalDateTime.of(2026, 6, 20, 12, 30)),
                                transaction(35_000, 7L, LocalDateTime.of(2026, 6, 11, 20, 15)),
                                transaction(6_300, 8L, LocalDateTime.of(2026, 6, 28, 14, 50)),
                                transaction(8_500, 6L, LocalDateTime.of(2026, 7, 18, 15, 5)),
                                transaction(41_000, 9L, LocalDateTime.of(2026, 7, 5, 11, 30)),
                                transaction(14_200, 10L, LocalDateTime.of(2026, 7, 25, 17, 40))
                        ))
                        .build(),
                MyDataCard.builder()
                        .cardProductId(15L) // 신한카드 All Pass (CREDIT)
                        .first4("4092")
                        .last4("9002")
                        .expiryDate(LocalDate.of(2028, 3, 31))
                        .creditLimit(BigDecimal.valueOf(2_000_000))
                        .scheduledPaymentAmount(BigDecimal.valueOf(150_000))
                        .transactions(List.of(
                                transaction(8_700, 1L, LocalDateTime.of(2026, 5, 6, 9, 50)),
                                transaction(23_000, 2L, LocalDateTime.of(2026, 5, 25, 18, 40)),
                                transaction(19_500, 6L, LocalDateTime.of(2026, 5, 14, 13, 20)),
                                transaction(33_000, 3L, LocalDateTime.of(2026, 6, 3, 19, 5)),
                                transaction(5_400, 9L, LocalDateTime.of(2026, 6, 17, 8, 30)),
                                transaction(47_000, 5L, LocalDateTime.of(2026, 6, 25, 20, 45)),
                                transaction(61_000, 4L, LocalDateTime.of(2026, 7, 2, 19, 20)),
                                transaction(12_800, 10L, LocalDateTime.of(2026, 7, 9, 9, 15)),
                                transaction(26_000, 7L, LocalDateTime.of(2026, 7, 20, 12, 50)),
                                transaction(9_300, 8L, LocalDateTime.of(2026, 7, 29, 15, 30))
                        ))
                        .build(),
                MyDataCard.builder()
                        .cardProductId(20L) // 신한카드 Pick E 체크 (DEBIT)
                        .first4("4092")
                        .last4("9003")
                        .expiryDate(LocalDate.of(2030, 1, 31))
                        .bankName("신한은행")
                        .balance(BigDecimal.valueOf(410_000))
                        .transactions(List.of(
                                transaction(15_000, 1L, LocalDateTime.of(2026, 5, 8, 8, 20)),
                                transaction(22_000, 3L, LocalDateTime.of(2026, 5, 21, 18, 10)),
                                transaction(6_200, 5L, LocalDateTime.of(2026, 6, 3, 8, 15)),
                                transaction(8_900, 9L, LocalDateTime.of(2026, 6, 9, 9, 40)),
                                transaction(31_000, 2L, LocalDateTime.of(2026, 6, 19, 19, 30)),
                                transaction(4_700, 10L, LocalDateTime.of(2026, 6, 27, 14, 5)),
                                transaction(26_500, 6L, LocalDateTime.of(2026, 7, 6, 12, 40)),
                                transaction(11_200, 4L, LocalDateTime.of(2026, 7, 14, 9, 55)),
                                transaction(18_300, 8L, LocalDateTime.of(2026, 7, 23, 17, 20)),
                                transaction(19_800, 7L, LocalDateTime.of(2026, 7, 29, 13, 50))
                        ))
                        .build(),
                MyDataCard.builder()
                        .cardProductId(21L) // 현대카드 Z everyday (CREDIT)
                        .first4("4155")
                        .last4("9004")
                        .expiryDate(LocalDate.of(2029, 5, 31))
                        .creditLimit(BigDecimal.valueOf(1_500_000))
                        .scheduledPaymentAmount(BigDecimal.valueOf(95_000))
                        .transactions(List.of(
                                transaction(9_600, 2L, LocalDateTime.of(2026, 5, 4, 8, 50)),
                                transaction(21_000, 5L, LocalDateTime.of(2026, 5, 16, 18, 30)),
                                transaction(34_500, 8L, LocalDateTime.of(2026, 5, 30, 20, 10)),
                                transaction(45_000, 1L, LocalDateTime.of(2026, 6, 1, 19, 40)),
                                transaction(7_800, 6L, LocalDateTime.of(2026, 6, 13, 9, 20)),
                                transaction(28_500, 3L, LocalDateTime.of(2026, 6, 22, 13, 10)),
                                transaction(12_000, 9L, LocalDateTime.of(2026, 7, 10, 11, 45)),
                                transaction(16_400, 10L, LocalDateTime.of(2026, 7, 1, 8, 25)),
                                transaction(33_000, 4L, LocalDateTime.of(2026, 7, 17, 20, 5)),
                                transaction(5_900, 7L, LocalDateTime.of(2026, 7, 27, 14, 30))
                        ))
                        .build(),
                MyDataCard.builder()
                        .cardProductId(43L) // KB국민 노리 체크카드 (DEBIT)
                        .first4("5327")
                        .last4("9005")
                        .expiryDate(LocalDate.of(2028, 7, 31))
                        .bankName("KB국민은행")
                        .balance(BigDecimal.valueOf(1_150_000))
                        .transactions(List.of(
                                transaction(13_500, 3L, LocalDateTime.of(2026, 5, 2, 8, 10)),
                                transaction(6_700, 5L, LocalDateTime.of(2026, 5, 11, 9, 30)),
                                transaction(24_000, 8L, LocalDateTime.of(2026, 5, 26, 18, 50)),
                                transaction(18_900, 2L, LocalDateTime.of(2026, 6, 7, 13, 40)),
                                transaction(9_900, 10L, LocalDateTime.of(2026, 6, 15, 7, 55)),
                                transaction(41_000, 6L, LocalDateTime.of(2026, 6, 24, 19, 15)),
                                transaction(8_200, 9L, LocalDateTime.of(2026, 7, 3, 8, 35)),
                                transaction(27_300, 1L, LocalDateTime.of(2026, 7, 22, 17, 30)),
                                transaction(29_500, 4L, LocalDateTime.of(2026, 7, 13, 12, 20)),
                                transaction(15_600, 7L, LocalDateTime.of(2026, 7, 28, 15, 45))
                        ))
                        .build()
        );
    }

    private MyDataTransaction transaction(long amount, Long storeId, LocalDateTime paidAt) {
        return MyDataTransaction.builder()
                .amount(BigDecimal.valueOf(amount))
                .storeId(storeId)
                .paidAt(paidAt)
                .build();
    }
}
