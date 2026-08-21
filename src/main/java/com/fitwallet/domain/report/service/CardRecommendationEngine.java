package com.fitwallet.domain.report.service;

import com.fitwallet.domain.report.dto.response.CardRecommendationRawResponse;
import com.fitwallet.domain.report.dto.response.CardRecommendationResponse;
import com.fitwallet.domain.report.dto.response.CategorySpendResponse;
import com.fitwallet.domain.report.dto.response.PopularCardRawResponse;
import com.fitwallet.domain.report.mapper.BenefitReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 카드 추천 전용 엔진 (Phase 1·2).
 *
 * <p>현행 "이번 달 상위 2개 카테고리 요율곱"의 약점을 고친다.
 * <ul>
 *   <li>지정한 이번 달(yearMonth)의 카테고리 지출을 <b>예상 지출</b>로 삼는다. 상위 2개로 제한하지 않고
 *       거래가 있는 전 카테고리를 본다. 전월 실적 판정도 이번 달 총지출로 한다.
 *       (Phase 1은 최근 3개월 중앙값을 썼으나, 분기마다 고정적인 일회성 지출이 중앙값 0으로 잘려
 *       해당 카테고리 추천을 못 받는 문제가 있어 이번 달 기준으로 되돌렸다.)</li>
 *   <li>(Phase 2) 순위를 후보 단독 혜택이 아니라 <b>보유 카드 대비 증분(한계 혜택)</b>으로 매긴다.
 *       한 결제엔 카드 한 장만 쓰므로, 카테고리별로 후보가 내 기존 최선(baseline)을 이기는 만큼만
 *       이득이다: {@code marginal = max(0, 후보 혜택 − baseline)}, 증분 = Σ marginal. 이미 잘
 *       커버되는 카테고리의 카드는 증분이 0이라 자연 제외된다.</li>
 * </ul>
 * 정밀 한도 버킷 시간순 시뮬·실적 달성 확률은 Phase 3(이번 범위 밖), 연회비는 스키마에 없어 제외한다.
 *
 * <p>거래 내역이 없으면(콜드스타트) 보유 수 상위의 미보유 카드로 폴백한다.
 */
@Component
@RequiredArgsConstructor
public class CardRecommendationEngine {

    private static final int RECOMMENDATION_COUNT = 2;
    private static final String COLD_START_DESCRIPTION = "많은 분들이 보유한 인기 카드예요.";

    private final BenefitReportMapper benefitReportMapper;

    /** 지정한 이번 달(yearMonth)의 카테고리 지출을 예상 지출로 삼아 카드 추천 목록을 만든다. */
    public List<CardRecommendationResponse> recommend(Long userId, String yearMonth) {
        List<CategorySpendResponse> profile = benefitReportMapper.getCategorySpends(userId, yearMonth);
        if (profile.isEmpty()) {
            return coldStart(userId);
        }

        BigDecimal totalSpend = BigDecimal.ZERO;
        for (CategorySpendResponse category : profile) {
            totalSpend = totalSpend.add(category.getSpendAmount());
        }
        return rankRecommendations(userId, profile, totalSpend);
    }

    private List<CardRecommendationResponse> rankRecommendations(
            Long userId, List<CategorySpendResponse> profile, BigDecimal totalSpend) {
        List<Long> categoryIds = new ArrayList<>();
        for (CategorySpendResponse category : profile) {
            categoryIds.add(category.getCategoryId());
        }
        List<CardRecommendationRawResponse> candidates = benefitReportMapper.getRecommendedCards(userId, categoryIds);
        List<CardRecommendationRawResponse> ownedBenefits = benefitReportMapper.getOwnedCardBenefits(userId, categoryIds);

        // 카테고리별 baseline = 보유 카드들이 그 카테고리에서 주는 최선 혜택(실적 통과분만). 없으면 0.
        Map<Long, BigDecimal> baselineByCategory =
                baselineByCategory(profile, ownedBenefits, totalSpend);

        // 후보 카드별 증분 = Σ max(0, 후보 혜택 − baseline). 이미 잘 커버되는 카테고리는 0으로 묻힌다.
        Map<Long, BigDecimal> marginalByCard = new HashMap<>();
        Map<Long, CardRecommendationRawResponse> cardInfoMap = new HashMap<>();

        for (CategorySpendResponse category : profile) {
            BigDecimal baseline = baselineByCategory.getOrDefault(category.getCategoryId(), BigDecimal.ZERO);
            for (CardRecommendationRawResponse card : candidates) {
                if (!card.getCategoryId().equals(category.getCategoryId())) {
                    continue;
                }
                if (!passesPerformance(card, totalSpend)) {
                    continue;
                }
                BigDecimal marginal = calculateExpectedBenefit(category, card).subtract(baseline).max(BigDecimal.ZERO);
                marginalByCard.merge(card.getCardProductId(), marginal, BigDecimal::add);
                cardInfoMap.putIfAbsent(card.getCardProductId(), card);
            }
        }

        List<Map.Entry<Long, BigDecimal>> sortedEntries = new ArrayList<>(marginalByCard.entrySet());
        // 증분 내림차순, 동률이면 card_product_id 오름차순으로 결정적 정렬
        sortedEntries.sort((a, b) -> {
            int byValue = b.getValue().compareTo(a.getValue());
            return byValue != 0 ? byValue : Long.compare(a.getKey(), b.getKey());
        });

        List<CardRecommendationResponse> recommendations = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : sortedEntries) {
            // 증분이 0인 후보(내 기존 카드로 이미 다 커버됨)는 추천 가치가 없어 제외한다.
            if (entry.getValue().signum() <= 0) {
                break;
            }
            if (recommendations.size() >= RECOMMENDATION_COUNT) {
                break;
            }
            recommendations.add(toCardRecommendationResponse(entry, cardInfoMap));
        }
        return recommendations;
    }

    /** 카테고리별 baseline: 보유 카드 혜택 중 실적을 통과하는 것들의 최댓값(원화). */
    private Map<Long, BigDecimal> baselineByCategory(
            List<CategorySpendResponse> profile,
            List<CardRecommendationRawResponse> ownedBenefits,
            BigDecimal totalSpend) {
        Map<Long, BigDecimal> baseline = new HashMap<>();
        for (CategorySpendResponse category : profile) {
            BigDecimal best = BigDecimal.ZERO;
            for (CardRecommendationRawResponse owned : ownedBenefits) {
                if (!owned.getCategoryId().equals(category.getCategoryId())) {
                    continue;
                }
                if (!passesPerformance(owned, totalSpend)) {
                    continue;
                }
                best = best.max(calculateExpectedBenefit(category, owned));
            }
            baseline.put(category.getCategoryId(), best);
        }
        return baseline;
    }

    /**
     * 전월 실적 조건 통과 여부. 판정 기준은 카테고리 지출이 아니라 이번 달 총지출(실제 실적에 가깝다).
     * min_prev_month_spend가 NULL이면 실적 무관 혜택이라 무조건 통과한다(NULL을 compareTo 하면 NPE).
     */
    private boolean passesPerformance(CardRecommendationRawResponse card, BigDecimal totalSpend) {
        return card.getMinPrevMonthSpend() == null
                || totalSpend.compareTo(card.getMinPrevMonthSpend()) >= 0;
    }

    /** 콜드스타트: 예상 지출을 낼 거래가 없으면 보유 수 상위 미보유 카드로 채운다. */
    private List<CardRecommendationResponse> coldStart(Long userId) {
        List<PopularCardRawResponse> popular =
                benefitReportMapper.getPopularUnownedCards(userId, RECOMMENDATION_COUNT);

        List<CardRecommendationResponse> recommendations = new ArrayList<>();
        for (PopularCardRawResponse card : popular) {
            recommendations.add(CardRecommendationResponse.builder()
                    .cardProductId(card.getCardProductId())
                    .cardName(card.getCardName())
                    .cardImageUrl(card.getCardImageUrl())
                    // 예상 지출이 없어 혜택을 추정할 수 없다 — 금액은 비우고 인기 기반임을 문구로 알린다.
                    .expectedBenefit(null)
                    .description(COLD_START_DESCRIPTION)
                    .build());
        }
        return recommendations;
    }

    /**
     * 후보 카드의 예상 혜택을 원화로 계산한다.
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
            Map.Entry<Long, BigDecimal> entry, Map<Long, CardRecommendationRawResponse> cardInfoMap) {
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
     * 추천 카드에 붙는 설명 문구.
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
