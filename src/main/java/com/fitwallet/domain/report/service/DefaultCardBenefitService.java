package com.fitwallet.domain.report.service;

import com.fitwallet.domain.report.dto.BenefitType;
import com.fitwallet.domain.report.dto.response.CardBenefitDetailResponse;
import com.fitwallet.domain.report.dto.response.CardSummaryResponse;
import com.fitwallet.domain.report.dto.response.CategoryTransactionGroupResponse;
import com.fitwallet.domain.report.dto.response.CategoryTransactionRawResponse;
import com.fitwallet.domain.report.dto.response.TransactionDetailResponse;
import com.fitwallet.domain.report.exception.CardBenefitErrorCode;
import com.fitwallet.domain.report.mapper.CardBenefitMapper;
import com.fitwallet.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DefaultCardBenefitService implements CardBenefitService {

    private final CardBenefitMapper cardBenefitMapper;

    @Override
    @Transactional(readOnly = true)
    public CardBenefitDetailResponse getCardBenefitDetail(Long userId, Long userCardId, String yearMonth) {
        CardSummaryResponse summary = cardBenefitMapper.getCardSummary(userId, userCardId, yearMonth);

        if (summary == null) {
            throw new BusinessException(CardBenefitErrorCode.CARD_NOT_FOUND);
        }

        List<CategoryTransactionRawResponse> rawTransactions =
                cardBenefitMapper.getCategoryTransactions(userId, userCardId, yearMonth);

        List<CategoryTransactionGroupResponse> categories = groupByCategory(rawTransactions);

        return CardBenefitDetailResponse.builder()
                .cardName(summary.getCardName())
                .cardImageUrl(summary.getCardImageUrl())
                .maskedCardNumber(summary.getMaskedCardNumber())
                .totalDiscount(summary.getTotalDiscount())
                .totalPoint(summary.getTotalPoint())
                .totalSpend(summary.getTotalSpend())
                .categories(categories)
                .build();
    }

    private List<CategoryTransactionGroupResponse> groupByCategory(
            List<CategoryTransactionRawResponse> rawTransactions
    ) {
        Map<Long, List<CategoryTransactionRawResponse>> groupedByCategory = new LinkedHashMap<>();

        for (CategoryTransactionRawResponse raw : rawTransactions) {
            List<CategoryTransactionRawResponse> list = groupedByCategory.get(raw.getCategoryId());
            if (list == null) {
                list = new ArrayList<>();
                groupedByCategory.put(raw.getCategoryId(), list);
            }
            list.add(raw);
        }

        List<CategoryTransactionGroupResponse> result = new ArrayList<>();

        for (Map.Entry<Long, List<CategoryTransactionRawResponse>> entry : groupedByCategory.entrySet()) {
            List<CategoryTransactionRawResponse> txList = entry.getValue();

            // 원화 할인과 포인트 적립은 단위가 달라 합계를 따로 낸다 (환산하지 않는다)
            BigDecimal discountSum = BigDecimal.ZERO;
            BigDecimal pointSum = BigDecimal.ZERO;
            List<TransactionDetailResponse> transactions = new ArrayList<>();

            for (CategoryTransactionRawResponse tx : txList) {
                if (tx.getBenefitType() == BenefitType.ACCUMULATE) {
                    pointSum = pointSum.add(tx.getBenefitAmount());
                } else {
                    discountSum = discountSum.add(tx.getBenefitAmount());
                }
                transactions.add(TransactionDetailResponse.builder()
                        .approvedAt(tx.getApprovedAt())
                        .storeName(tx.getStoreName())
                        .benefitType(tx.getBenefitType())
                        .benefitRate(tx.getBenefitRate())
                        .paidAmount(tx.getPaidAmount())
                        .benefitAmount(tx.getBenefitAmount())
                        .build());
            }

            CategoryTransactionRawResponse first = txList.get(0);

            result.add(CategoryTransactionGroupResponse.builder()
                    .categoryId(first.getCategoryId())
                    .categoryName(first.getCategoryName())
                    .usageCount(txList.size())
                    .discountAmount(discountSum)
                    .pointAmount(pointSum)
                    .transactions(transactions)
                    .build());
        }

        return result;
    }
}