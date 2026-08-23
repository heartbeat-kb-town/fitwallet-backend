package com.fitwallet.domain.report.service;

import com.fitwallet.domain.report.dto.response.*;
import com.fitwallet.domain.report.mapper.BenefitReportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
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

    /**
     * 카테고리별 추천 후보 카탈로그. 앱 생애에 한 번만 채운다.
     * <p>
     * 후보 쿼리가 조인하는 7개 테이블이 <b>전부 참조 데이터</b>다 — Flyway로만 바뀌고 그건
     * 재기동을 동반한다. 그런데 요청마다 다시 돌면서 종속 서브쿼리를 244회씩 실행했다
     * (혜택 서비스 61개 × 카테고리 {@value #RECOMMENDATION_COUNT}개 × 서브쿼리 2개).
     * 100VU 실측에서 이 쿼리만 p50 17.7ms · <b>p95 128.4ms</b>로 엔드포인트 p95(113ms)를
     * 혼자 넘겼다.
     * <p>
     * ⚠️ <b>인스턴스마다 따로 갖는다.</b> 지금은 단일 인스턴스라 무해하고, 늘어나도 각자
     * 한 번씩 채울 뿐 값은 같다(참조 데이터라 모든 인스턴스가 같은 답을 본다).
     * <p>
     * ⚠️ {@code card_product}·{@code benefit_*}를 바꾸면 낡는다. 그때는 재기동한다 —
     * 그 테이블들은 Flyway로만 바뀌고 Flyway가 어차피 재기동에서 도므로 실질 제약이 아니다.
     */
    private volatile Map<Long, List<CardRecommendationRawResponse>> catalogByCategory;

    /**
     * 요청에 필요한 카테고리의 후보만 꺼내고 보유 카드를 걸러낸다.
     * <p>
     * 예전에는 두 조건을 SQL의 {@code WHERE}로 걸었다. 무거운 조인이 사용자와 무관한데도
     * 요청마다 다시 돌던 이유가 그것이다.
     */
    private List<CardRecommendationRawResponse> recommendationCandidates(Long userId, List<Long> categoryIds) {
        Map<Long, List<CardRecommendationRawResponse>> catalog = catalog();
        Set<Long> owned = new HashSet<>(benefitReportMapper.findOwnedCardProductIds(userId));

        List<CardRecommendationRawResponse> candidates = new ArrayList<>();
        for (Long categoryId : categoryIds) {
            for (CardRecommendationRawResponse card : catalog.getOrDefault(categoryId, List.of())) {
                if (!owned.contains(card.getCardProductId())) {
                    candidates.add(card);
                }
            }
        }
        return candidates;
    }

    /**
     * 카탈로그를 처음 쓸 때 채운다.
     * <p>
     * 두 스레드가 동시에 들어오면 둘 다 채울 수 있다. 같은 답을 만드는 순수 조회라 무해하고,
     * 그걸 막으려고 락을 걸면 첫 요청들이 줄을 서게 되어 오히려 손해다.
     */
    private Map<Long, List<CardRecommendationRawResponse>> catalog() {
        Map<Long, List<CardRecommendationRawResponse>> cached = catalogByCategory;
        if (cached != null) {
            return cached;
        }
        Map<Long, List<CardRecommendationRawResponse>> built = new HashMap<>();
        for (CardRecommendationRawResponse card : benefitReportMapper.getAllRecommendationCandidates()) {
            built.computeIfAbsent(card.getCategoryId(), ignored -> new ArrayList<>()).add(card);
        }
        log.info("추천 후보 카탈로그를 채웠다 — 카테고리 {}개 · 후보 {}건",
                built.size(), built.values().stream().mapToInt(List::size).sum());
        catalogByCategory = built;
        return built;
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

        List<CardRecommendationRawResponse> candidates = recommendationCandidates(userId, categoryIds);

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