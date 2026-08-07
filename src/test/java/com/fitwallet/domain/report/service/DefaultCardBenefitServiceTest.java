package com.fitwallet.domain.report.service;

import com.fitwallet.domain.report.dto.response.*;
import com.fitwallet.domain.report.exception.CardBenefitErrorCode;
import com.fitwallet.domain.report.mapper.CardBenefitMapper;
import com.fitwallet.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultCardBenefitServiceTest {

    @Mock
    private CardBenefitMapper cardBenefitMapper;

    @InjectMocks
    private DefaultCardBenefitService cardBenefitService;

    @Test
    void 존재하지_않거나_소유자가_아닌_카드를_조회하면_예외가_발생한다() {
        Long userId = 1L;
        Long userCardId = 999L;
        String yearMonth = "2026-04";

        when(cardBenefitMapper.getCardSummary(userId, userCardId, yearMonth))
                .thenReturn(null);

        assertThatThrownBy(() -> cardBenefitService.getCardBenefitDetail(userId, userCardId, yearMonth))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CardBenefitErrorCode.CARD_NOT_FOUND);
    }

    @Test
    void 카드_요약과_카테고리별_결제건을_함께_조회한다() {
        Long userId = 1L;
        Long userCardId = 1L;
        String yearMonth = "2026-04";

        CardSummaryResponse summary = CardSummaryResponse.builder()
                .cardName("KB Gold & More")
                .cardImageUrl("https://.../card.png")
                .maskedCardNumber("**** 1234")
                .totalDiscount(BigDecimal.valueOf(12500))
                .totalSpend(BigDecimal.valueOf(950000))
                .build();

        CategoryTransactionRawResponse tx1 = CategoryTransactionRawResponse.builder()
                .categoryId(1L)
                .categoryName("외식")
                .approvedAt(LocalDateTime.of(2026, 7, 15, 0, 0))
                .storeName("배달의민족")
                .discountRate(BigDecimal.valueOf(7))
                .paidAmount(BigDecimal.valueOf(32000))
                .benefitAmount(BigDecimal.valueOf(2240))
                .build();

        CategoryTransactionRawResponse tx2 = CategoryTransactionRawResponse.builder()
                .categoryId(1L)
                .categoryName("외식")
                .approvedAt(LocalDateTime.of(2026, 7, 11, 0, 0))
                .storeName("스시조 강남점")
                .discountRate(BigDecimal.valueOf(7))
                .paidAmount(BigDecimal.valueOf(65000))
                .benefitAmount(BigDecimal.valueOf(4550))
                .build();

        when(cardBenefitMapper.getCardSummary(userId, userCardId, yearMonth))
                .thenReturn(summary);
        when(cardBenefitMapper.getCategoryTransactions(userId, userCardId, yearMonth))
                .thenReturn(List.of(tx1, tx2));

        CardBenefitDetailResponse response =
                cardBenefitService.getCardBenefitDetail(userId, userCardId, yearMonth);

        assertThat(response.getCardName()).isEqualTo("KB Gold & More");
        assertThat(response.getTotalDiscount()).isEqualByComparingTo(BigDecimal.valueOf(12500));
        assertThat(response.getCategories()).hasSize(1);

        CategoryTransactionGroupResponse category = response.getCategories().get(0);
        assertThat(category.getCategoryId()).isEqualTo(1L);
        assertThat(category.getUsageCount()).isEqualTo(2);
        assertThat(category.getBenefitAmount()).isEqualByComparingTo(BigDecimal.valueOf(6790));
        assertThat(category.getTransactions()).hasSize(2);
    }

    @Test
    void 서로_다른_카테고리의_결제건은_각각_다른_그룹으로_묶인다() {
        Long userId = 1L;
        Long userCardId = 1L;
        String yearMonth = "2026-04";

        CardSummaryResponse summary = CardSummaryResponse.builder()
                .cardName("KB Gold & More")
                .cardImageUrl("https://.../card.png")
                .maskedCardNumber("**** 1234")
                .totalDiscount(BigDecimal.valueOf(6500))
                .totalSpend(BigDecimal.valueOf(140000))
                .build();

        CategoryTransactionRawResponse foodTx = CategoryTransactionRawResponse.builder()
                .categoryId(1L)
                .categoryName("외식")
                .approvedAt(LocalDateTime.of(2026, 7, 15, 0, 0))
                .storeName("배달의민족")
                .discountRate(BigDecimal.valueOf(7))
                .paidAmount(BigDecimal.valueOf(32000))
                .benefitAmount(BigDecimal.valueOf(2240))
                .build();

        CategoryTransactionRawResponse martTx = CategoryTransactionRawResponse.builder()
                .categoryId(2L)
                .categoryName("마트")
                .approvedAt(LocalDateTime.of(2026, 7, 20, 0, 0))
                .storeName("이마트 역삼점")
                .discountRate(BigDecimal.valueOf(5))
                .paidAmount(BigDecimal.valueOf(58000))
                .benefitAmount(BigDecimal.valueOf(2900))
                .build();

        when(cardBenefitMapper.getCardSummary(userId, userCardId, yearMonth))
                .thenReturn(summary);
        when(cardBenefitMapper.getCategoryTransactions(userId, userCardId, yearMonth))
                .thenReturn(List.of(foodTx, martTx));

        CardBenefitDetailResponse response =
                cardBenefitService.getCardBenefitDetail(userId, userCardId, yearMonth);

        assertThat(response.getCategories()).hasSize(2);
        assertThat(response.getCategories())
                .extracting(CategoryTransactionGroupResponse::getCategoryName)
                .containsExactly("외식", "마트");
    }
}