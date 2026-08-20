package com.fitwallet.domain.report.service;

import com.fitwallet.domain.report.dto.response.CardRecommendationRawResponse;
import com.fitwallet.domain.report.dto.response.CardRecommendationResponse;
import com.fitwallet.domain.report.dto.response.CategorySpendResponse;
import com.fitwallet.domain.report.dto.response.MonthlyCategorySpendRawResponse;
import com.fitwallet.domain.report.dto.response.PopularCardRawResponse;
import com.fitwallet.domain.report.mapper.BenefitReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 카드 추천 전용 엔진 (Phase 1).
 *
 * <p>현행 "이번 달 상위 2개 카테고리 요율곱"의 두 약점을 고친다.
 * <ul>
 *   <li>최근 {@value #PROJECTION_MONTHS}개월 지출의 <b>중앙값</b>으로 카테고리별 "예상 월 지출"을
 *       만든다 — 여행 같은 일회성 과소비에 덜 휘둘린다.</li>
 *   <li>전월 실적 판정을 카테고리 지출이 아니라 <b>예상 월 총지출</b>로 한다 — 실제 실적에 더 가깝다.</li>
 * </ul>
 * 예상 혜택은 후보 카드 단독 기준이다(보유 카드 대비 증분은 Phase 2, 한도 버킷 시간순
 * 정밀 시뮬·실적 달성 확률은 Phase 3).
 *
 * <p>거래 내역이 없으면(콜드스타트) 보유 수 상위의 미보유 카드로 폴백한다.
 */
@Component
@RequiredArgsConstructor
public class CardRecommendationEngine {

    private static final int RECOMMENDATION_COUNT = 2;
    private static final int PROJECTION_MONTHS = 3;
    private static final String COLD_START_DESCRIPTION = "많은 분들이 보유한 인기 카드예요.";

    private final BenefitReportMapper benefitReportMapper;

    /** 지정 연월 기준 최근 3개월 예상 지출로 카드 추천 목록을 만든다. */
    public List<CardRecommendationResponse> recommend(Long userId, String yearMonth) {
        List<String> monthKeys = recentMonthKeys(yearMonth);
        List<MonthlyCategorySpendRawResponse> rows = benefitReportMapper.getMonthlyCategorySpends(
                userId, monthKeys.get(0), monthKeys.get(monthKeys.size() - 1));

        List<CategorySpendResponse> profile = buildProjectedProfile(rows, monthKeys);
        if (profile.isEmpty()) {
            return coldStart(userId);
        }

        BigDecimal projectedTotalSpend = projectedTotalSpend(rows, monthKeys);
        return rankRecommendations(userId, profile, projectedTotalSpend);
    }

    /** [가장 오래된 달 ... 대상 달] 순으로 최근 3개월의 yyyy-MM 키를 만든다. */
    private List<String> recentMonthKeys(String yearMonth) {
        YearMonth base = YearMonth.parse(yearMonth);
        List<String> keys = new ArrayList<>();
        for (int i = PROJECTION_MONTHS - 1; i >= 0; i--) {
            keys.add(base.minusMonths(i).toString());
        }
        return keys;
    }

    /**
     * 카테고리별로 3개월치 월 지출(없는 달은 0)의 중앙값을 예상 월 지출로 잡는다.
     * 중앙값이 0인 카테고리(예: 3개월 중 한 달만 쓴 경우)는 "평소 소비"가 아니라 제외한다.
     */
    private List<CategorySpendResponse> buildProjectedProfile(
            List<MonthlyCategorySpendRawResponse> rows, List<String> monthKeys) {
        Map<Long, String> categoryNames = new LinkedHashMap<>();
        Map<Long, Map<String, BigDecimal>> spendByCategory = new LinkedHashMap<>();
        for (MonthlyCategorySpendRawResponse row : rows) {
            categoryNames.putIfAbsent(row.getCategoryId(), row.getCategoryName());
            spendByCategory
                    .computeIfAbsent(row.getCategoryId(), ignored -> new HashMap<>())
                    .put(row.getYearMonth(), row.getSpendAmount());
        }

        List<CategorySpendResponse> profile = new ArrayList<>();
        for (Map.Entry<Long, Map<String, BigDecimal>> entry : spendByCategory.entrySet()) {
            BigDecimal projected = median(monthlySeries(entry.getValue(), monthKeys));
            if (projected.signum() <= 0) {
                continue;
            }
            profile.add(CategorySpendResponse.builder()
                    .categoryId(entry.getKey())
                    .categoryName(categoryNames.get(entry.getKey()))
                    .spendAmount(projected)
                    .build());
        }
        return profile;
    }

    /** 전월 실적 판정용 예상 월 총지출 = 월별 총지출(없는 달 0)의 중앙값. */
    private BigDecimal projectedTotalSpend(List<MonthlyCategorySpendRawResponse> rows, List<String> monthKeys) {
        Map<String, BigDecimal> totalByMonth = new HashMap<>();
        for (MonthlyCategorySpendRawResponse row : rows) {
            totalByMonth.merge(row.getYearMonth(), row.getSpendAmount(), BigDecimal::add);
        }
        return median(monthlySeries(totalByMonth, monthKeys));
    }

    /** 월 키 순서대로 값을 뽑되 없는 달은 0으로 채운 3개월 시계열. */
    private List<BigDecimal> monthlySeries(Map<String, BigDecimal> byMonth, List<String> monthKeys) {
        List<BigDecimal> series = new ArrayList<>();
        for (String key : monthKeys) {
            series.add(byMonth.getOrDefault(key, BigDecimal.ZERO));
        }
        return series;
    }

    /** 값들의 중앙값. 짝수 개면 가운데 두 값의 평균. */
    private BigDecimal median(List<BigDecimal> values) {
        List<BigDecimal> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int size = sorted.size();
        int mid = size / 2;
        if (size % 2 == 1) {
            return sorted.get(mid);
        }
        return sorted.get(mid - 1).add(sorted.get(mid))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private List<CardRecommendationResponse> rankRecommendations(
            Long userId, List<CategorySpendResponse> profile, BigDecimal projectedTotalSpend) {
        List<Long> categoryIds = new ArrayList<>();
        for (CategorySpendResponse category : profile) {
            categoryIds.add(category.getCategoryId());
        }
        List<CardRecommendationRawResponse> candidates = benefitReportMapper.getRecommendedCards(userId, categoryIds);

        Map<Long, BigDecimal> expectedBenefitByCard = new HashMap<>();
        Map<Long, CardRecommendationRawResponse> cardInfoMap = new HashMap<>();

        for (CategorySpendResponse category : profile) {
            for (CardRecommendationRawResponse card : candidates) {
                if (!card.getCategoryId().equals(category.getCategoryId())) {
                    continue;
                }
                // min_prev_month_spend는 실적 무관 혜택(tier 없음)이면 NULL이다.
                // NULL은 "전월실적 조건 없음"이므로 무조건 통과시킨다. (NULL을 compareTo 하면 NPE)
                // 판정 기준은 카테고리 지출이 아니라 예상 월 총지출(실제 실적에 가깝다).
                if (card.getMinPrevMonthSpend() != null
                        && projectedTotalSpend.compareTo(card.getMinPrevMonthSpend()) < 0) {
                    continue;
                }

                BigDecimal benefit = calculateExpectedBenefit(category, card);
                expectedBenefitByCard.merge(card.getCardProductId(), benefit, BigDecimal::add);
                cardInfoMap.putIfAbsent(card.getCardProductId(), card);
            }
        }

        List<Map.Entry<Long, BigDecimal>> sortedEntries = new ArrayList<>(expectedBenefitByCard.entrySet());
        sortedEntries.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        List<CardRecommendationResponse> recommendations = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : sortedEntries) {
            if (recommendations.size() >= RECOMMENDATION_COUNT) {
                break;
            }
            recommendations.add(toCardRecommendationResponse(entry, cardInfoMap));
        }
        return recommendations;
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
