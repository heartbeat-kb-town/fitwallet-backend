package com.fitwallet.domain.report.service;

import com.fitwallet.domain.report.dto.response.*;
import com.fitwallet.domain.report.mapper.BenefitReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DefaultBenefitReportService implements BenefitReportService {

    private static final int RECOMMENDATION_COUNT = 2;

    private final BenefitReportMapper benefitReportMapper;

    @Override
    @Transactional(readOnly = true)
    public BenefitSummaryResponse getBenefitSummary(Long userId, String yearMonth) {
        BigDecimal totalReceived = benefitReportMapper.getTotalReceivedBenefit(userId, yearMonth);
        BigDecimal totalMissed = benefitReportMapper.getTotalMissedBenefit(userId, yearMonth);
        List<CategoryBenefitResponse> categories = benefitReportMapper.getCategoryBenefits(userId, yearMonth);

        List<CardRecommendationResponse> recommendations = getRecommendations(userId, yearMonth);

        return BenefitSummaryResponse.builder()
                .totalReceivedBenefit(totalReceived)
                .totalMissedBenefit(totalMissed)
                .categories(categories)
                .recommendations(recommendations)
                .build();
    }

    private List<CardRecommendationResponse> getRecommendations(Long userId, String yearMonth) {
        List<CategorySpendResponse> topCategories =
                benefitReportMapper.getTopSpendingCategories(userId, yearMonth, RECOMMENDATION_COUNT);

        List<Long> categoryIds = new ArrayList<>();
        for (CategorySpendResponse category : topCategories) {
            categoryIds.add(category.getCategoryId());
        }

        List<CardRecommendationRawResponse> candidates = benefitReportMapper.getRecommendedCards(userId, categoryIds);

        Map<Long, BigDecimal> expectedBenefitByCard = new HashMap<>();
        Map<Long, CardRecommendationRawResponse> cardInfoMap = new HashMap<>();

        for (CategorySpendResponse category : topCategories) {
            for (CardRecommendationRawResponse card : candidates) {
                if (!card.getCategoryId().equals(category.getCategoryId())) {
                    continue;
                }
                if (category.getSpendAmount().compareTo(card.getMinPrevMonthSpend()) < 0) {
                    continue;
                }

                BigDecimal benefit = category.getSpendAmount()
                        .multiply(card.getDiscountRate())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                if (card.getLimitValue() != null && benefit.compareTo(card.getLimitValue()) > 0) {
                    benefit = card.getLimitValue();
                }

                BigDecimal existing = expectedBenefitByCard.get(card.getCardProductId());
                if (existing == null) {
                    expectedBenefitByCard.put(card.getCardProductId(), benefit);
                } else {
                    expectedBenefitByCard.put(card.getCardProductId(), existing.add(benefit));
                }

                if (!cardInfoMap.containsKey(card.getCardProductId())) {
                    cardInfoMap.put(card.getCardProductId(), card);
                }
            }
        }

        List<Map.Entry<Long, BigDecimal>> sortedEntries = new ArrayList<>(expectedBenefitByCard.entrySet());
        sortedEntries.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        List<CardRecommendationResponse> recommendations = new ArrayList<>();
        int count = 0;
        for (Map.Entry<Long, BigDecimal> entry : sortedEntries) {
            if (count >= RECOMMENDATION_COUNT) {
                break;
            }
            recommendations.add(toCardRecommendationResponse(entry, cardInfoMap));
            count++;
        }

        return recommendations;
    }

    private CardRecommendationResponse toCardRecommendationResponse(
            Map.Entry<Long, BigDecimal> entry,
            Map<Long, CardRecommendationRawResponse> cardInfoMap
    ) {
        CardRecommendationRawResponse info = cardInfoMap.get(entry.getKey());
        String description = String.format("%s %s%% 할인, 전월 실적 %,d원 이상, 월 최대 %,d원 한도",
                info.getCategoryName(),
                info.getDiscountRate().stripTrailingZeros().toPlainString(),
                info.getMinPrevMonthSpend().intValue(),
                info.getLimitValue().intValue());

        return CardRecommendationResponse.builder()
                .cardProductId(info.getCardProductId())
                .cardName(info.getCardName())
                .cardImageUrl(info.getCardImageUrl())
                .expectedBenefit(entry.getValue())
                .description(description)
                .build();
    }
}