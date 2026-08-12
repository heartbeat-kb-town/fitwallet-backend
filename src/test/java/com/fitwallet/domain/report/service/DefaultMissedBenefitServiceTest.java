package com.fitwallet.domain.report.service;

import com.fitwallet.domain.report.dto.LossType;
import com.fitwallet.domain.report.dto.response.MissedCategoryDetailResponse;
import com.fitwallet.domain.report.dto.response.MissedCategoryGroupResponse;
import com.fitwallet.domain.report.dto.response.MissedSummaryResponse;
import com.fitwallet.domain.report.dto.response.MissedTransactionRawResponse;
import com.fitwallet.domain.report.mapper.MissedBenefitMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultMissedBenefitServiceTest {

    @Mock
    private MissedBenefitMapper missedBenefitMapper;

    @InjectMocks
    private DefaultMissedBenefitService missedBenefitService;

    private MissedTransactionRawResponse rawTx(Long categoryId, String categoryName,
                                               String store, BigDecimal diff) {
        return MissedTransactionRawResponse.builder()
                .categoryId(categoryId)
                .categoryName(categoryName)
                .approvedAt(LocalDateTime.of(2026, 7, 15, 0, 0))
                .storeName(store)
                .usedCardName("카카오뱅크")
                .paidAmount(BigDecimal.valueOf(32000))
                .alternativeCardName("KB Gold & More")
                .discountRate(BigDecimal.valueOf(7))
                .diffAmount(diff)
                .build();
    }

    @Test
    void 총_놓친혜택은_앱미사용과_카드선택_손실의_합이다() {
        Long userId = 1L;
        String yearMonth = "2026-07";

        when(missedBenefitMapper.getMissedSummary(userId, yearMonth))
                .thenReturn(MissedSummaryResponse.builder()
                        .appUnusedAmount(BigDecimal.valueOf(16132))
                        .cardMismatchAmount(BigDecimal.valueOf(20319))
                        .build());
        when(missedBenefitMapper.getMissedTransactions(userId, yearMonth, 0))
                .thenReturn(List.of());

        MissedCategoryDetailResponse response =
                missedBenefitService.getMissedBenefitDetail(userId, yearMonth, LossType.APP_UNUSED);

        assertThat(response.getAppUnusedAmount()).isEqualByComparingTo(BigDecimal.valueOf(16132));
        assertThat(response.getCardMismatchAmount()).isEqualByComparingTo(BigDecimal.valueOf(20319));
        assertThat(response.getTotalMissedBenefit()).isEqualByComparingTo(BigDecimal.valueOf(36451));
        assertThat(response.getLossType()).isEqualTo("APP_UNUSED");
    }

    @Test
    void 선택한_손실종류에_맞는_is_used_app으로_거래를_조회한다() {
        Long userId = 1L;
        String yearMonth = "2026-07";

        when(missedBenefitMapper.getMissedSummary(userId, yearMonth))
                .thenReturn(MissedSummaryResponse.builder()
                        .appUnusedAmount(BigDecimal.ZERO)
                        .cardMismatchAmount(BigDecimal.valueOf(20319))
                        .build());
        when(missedBenefitMapper.getMissedTransactions(userId, yearMonth, 1))
                .thenReturn(List.of());

        missedBenefitService.getMissedBenefitDetail(userId, yearMonth, LossType.CARD_MISMATCH);

        // CARD_MISMATCH → is_used_app = 1 로 조회해야 한다
        verify(missedBenefitMapper).getMissedTransactions(userId, yearMonth, 1);
    }

    @Test
    void 같은_카테고리의_놓친_거래는_한_그룹으로_묶여_건수와_금액이_합산된다() {
        Long userId = 1L;
        String yearMonth = "2026-07";

        when(missedBenefitMapper.getMissedSummary(userId, yearMonth))
                .thenReturn(MissedSummaryResponse.builder()
                        .appUnusedAmount(BigDecimal.valueOf(6790))
                        .cardMismatchAmount(BigDecimal.ZERO)
                        .build());
        when(missedBenefitMapper.getMissedTransactions(userId, yearMonth, 0))
                .thenReturn(List.of(
                        rawTx(1L, "외식", "배달의민족", BigDecimal.valueOf(2240)),
                        rawTx(1L, "외식", "스시조 강남점", BigDecimal.valueOf(4550))
                ));

        MissedCategoryDetailResponse response =
                missedBenefitService.getMissedBenefitDetail(userId, yearMonth, LossType.APP_UNUSED);

        assertThat(response.getCategories()).hasSize(1);
        MissedCategoryGroupResponse category = response.getCategories().get(0);
        assertThat(category.getCategoryName()).isEqualTo("외식");
        assertThat(category.getMissedCount()).isEqualTo(2);
        assertThat(category.getMissedAmount()).isEqualByComparingTo(BigDecimal.valueOf(6790));
        assertThat(category.getTransactions()).hasSize(2);
    }
}
