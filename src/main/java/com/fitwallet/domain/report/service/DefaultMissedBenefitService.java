package com.fitwallet.domain.report.service;

import com.fitwallet.domain.report.dto.LossType;
import com.fitwallet.domain.report.dto.response.MissedCategoryDetailResponse;
import com.fitwallet.domain.report.dto.response.MissedCategoryGroupResponse;
import com.fitwallet.domain.report.dto.response.MissedSummaryResponse;
import com.fitwallet.domain.report.dto.response.MissedTransactionDetailResponse;
import com.fitwallet.domain.report.dto.response.MissedTransactionRawResponse;
import com.fitwallet.domain.report.mapper.MissedBenefitMapper;
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
public class DefaultMissedBenefitService implements MissedBenefitService {

    private final MissedBenefitMapper missedBenefitMapper;

    @Override
    @Transactional(readOnly = true)
    public MissedCategoryDetailResponse getMissedBenefitDetail(Long userId, String yearMonth, LossType lossType) {
        MissedSummaryResponse summary = missedBenefitMapper.getMissedSummary(userId, yearMonth);
        BigDecimal appUnused = summary.getAppUnusedAmount();
        BigDecimal cardMismatch = summary.getCardMismatchAmount();

        List<MissedTransactionRawResponse> rawTransactions =
                missedBenefitMapper.getMissedTransactions(userId, yearMonth, lossType.getIsUsedApp());

        List<MissedCategoryGroupResponse> categories = groupByCategory(rawTransactions);

        return MissedCategoryDetailResponse.builder()
                // 상단 총액은 탭과 무관하게 두 손실을 합쳐 보여준다 (디자인의 "이번 달 총 놓친 혜택")
                .totalMissedBenefit(appUnused.add(cardMismatch))
                .appUnusedAmount(appUnused)
                .cardMismatchAmount(cardMismatch)
                .lossType(lossType.name())
                // 카테고리 상세는 선택한 탭(lossType)의 거래만 담는다
                .categories(categories)
                .build();
    }

    private List<MissedCategoryGroupResponse> groupByCategory(List<MissedTransactionRawResponse> rawTransactions) {
        Map<Long, List<MissedTransactionRawResponse>> groupedByCategory = new LinkedHashMap<>();

        for (MissedTransactionRawResponse raw : rawTransactions) {
            List<MissedTransactionRawResponse> list = groupedByCategory.get(raw.getCategoryId());
            if (list == null) {
                list = new ArrayList<>();
                groupedByCategory.put(raw.getCategoryId(), list);
            }
            list.add(raw);
        }

        List<MissedCategoryGroupResponse> result = new ArrayList<>();

        for (Map.Entry<Long, List<MissedTransactionRawResponse>> entry : groupedByCategory.entrySet()) {
            List<MissedTransactionRawResponse> txList = entry.getValue();

            BigDecimal missedSum = BigDecimal.ZERO;
            List<MissedTransactionDetailResponse> transactions = new ArrayList<>();

            for (MissedTransactionRawResponse tx : txList) {
                missedSum = missedSum.add(tx.getDiffAmount());
                transactions.add(MissedTransactionDetailResponse.builder()
                        .approvedAt(tx.getApprovedAt())
                        .storeName(tx.getStoreName())
                        .usedCardName(tx.getUsedCardName())
                        .paidAmount(tx.getPaidAmount())
                        .alternativeCardName(tx.getAlternativeCardName())
                        .discountRate(tx.getDiscountRate())
                        .diffAmount(tx.getDiffAmount())
                        .build());
            }

            MissedTransactionRawResponse first = txList.get(0);

            result.add(MissedCategoryGroupResponse.builder()
                    .categoryId(first.getCategoryId())
                    .categoryName(first.getCategoryName())
                    .missedCount(txList.size())
                    .missedAmount(missedSum)
                    .transactions(transactions)
                    .build());
        }

        return result;
    }
}
