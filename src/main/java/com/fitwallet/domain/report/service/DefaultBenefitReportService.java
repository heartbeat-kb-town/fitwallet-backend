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

        // 해당 월에 거래가 없으면 상위 카테고리가 비고, 빈 categoryIds를 그대로 넘기면
        // 매퍼의 IN () 이 SQL 문법 오류를 낸다. 추천할 게 없으므로 여기서 끊는다.
        if (categoryIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<CardRecommendationRawResponse> candidates = benefitReportMapper.getRecommendedCards(userId, categoryIds);

        Map<Long, BigDecimal> expectedBenefitByCard = new HashMap<>();
        Map<Long, CardRecommendationRawResponse> cardInfoMap = new HashMap<>();

        for (CategorySpendResponse category : topCategories) {
            for (CardRecommendationRawResponse card : candidates) {
                if (!card.getCategoryId().equals(category.getCategoryId())) {
                    continue;
                }
                // min_prev_month_spend는 실적 무관 혜택(tier 없음)이면 NULL이다.
                // NULL은 "전월실적 조건 없음"이므로 무조건 통과시킨다. (NULL을 compareTo
                // 하면 NPE — plan_group/실적무관 혜택 후보에서 실제로 터졌다)
                if (card.getMinPrevMonthSpend() != null
                        && category.getSpendAmount().compareTo(card.getMinPrevMonthSpend()) < 0) {
                    continue;
                }

                BigDecimal benefit = calculateExpectedBenefit(category, card);

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

    /**
     * 카드의 예상 혜택을 원화로 계산한다.
     * - RATE: 지출액 × 요율 / 100, FIXED: value_number 그대로
     * - point_currency_id가 있으면(포인트 적립) krw_per_point를 곱해 원화로 환산
     * - 한도(limit_value)도 limit_basis가 POINT면 같이 환산해서 비교
     */
    private BigDecimal calculateExpectedBenefit(CategorySpendResponse category, CardRecommendationRawResponse card) {
        BigDecimal raw = "RATE".equals(card.getValueType())
                ? category.getSpendAmount()
                .multiply(card.getDiscountRate())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : card.getDiscountRate();

        BigDecimal benefit = card.getPointCurrencyId() != null
                ? raw.multiply(card.getKrwPerPoint())
                : raw;

        if (card.getLimitValue() != null) {
            BigDecimal limitInWon = ("POINT".equals(card.getLimitBasis()) && card.getPointCurrencyId() != null)
                    ? card.getLimitValue().multiply(card.getKrwPerPoint())
                    : card.getLimitValue();
            if (benefit.compareTo(limitInWon) > 0) {
                benefit = limitInWon;
            }
        }

        return benefit;
    }

    private CardRecommendationResponse toCardRecommendationResponse(
            Map.Entry<Long, BigDecimal> entry,
            Map<Long, CardRecommendationRawResponse> cardInfoMap
    ) {
        CardRecommendationRawResponse info = cardInfoMap.get(entry.getKey());

        return CardRecommendationResponse.builder()
                .cardProductId(info.getCardProductId())
                .cardName(info.getCardName())
                .cardImageUrl(info.getCardImageUrl())
                .expectedBenefit(entry.getValue())
                .description(buildDescription(info))
                .build();
    }

    /**
     * 카드 추천 카드에 붙는 설명 문구.
     * - RATE는 "%할인/적립", FIXED는 "N원/N포인트 적립"으로 구분
     * - 전월실적 조건은 0원(조건 없음)이면 문구에서 생략
     * - 한도(limit_value)는 benefit_tier/benefit_limit이 없는 혜택이면 null일 수 있어 있을 때만 표기,
     *   포인트 기준 한도(limit_basis=POINT)면 "포인트"로 단위 표기
     */
    private String buildDescription(CardRecommendationRawResponse info) {
        StringBuilder description = new StringBuilder();
        description.append(info.getCategoryName()).append(" ");

        boolean isPoint = info.getPointCurrencyId() != null;
        if ("RATE".equals(info.getValueType())) {
            description.append(info.getDiscountRate().stripTrailingZeros().toPlainString())
                    .append(isPoint ? "% 적립" : "% 할인");
        } else {
            description.append(String.format("%,d%s", info.getDiscountRate().intValue(), isPoint ? "포인트" : "원"))
                    .append(isPoint ? " 적립" : " 할인");
        }

        if (info.getMinPrevMonthSpend() != null && info.getMinPrevMonthSpend().compareTo(BigDecimal.ZERO) > 0) {
            description.append(String.format(", 전월 실적 %,d원 이상", info.getMinPrevMonthSpend().intValue()));
        }

        if (info.getLimitValue() != null) {
            String unit = "POINT".equals(info.getLimitBasis()) ? "포인트" : "원";
            description.append(String.format(", 월 최대 %,d%s 한도", info.getLimitValue().intValue(), unit));
        }

        return description.toString();
    }
}