package com.fitwallet.domain.report.service;

import com.fitwallet.domain.card.dto.request.CardListSearchRequest;
import com.fitwallet.domain.card.dto.response.CardListResponse;
import com.fitwallet.domain.card.service.CardService;
import com.fitwallet.domain.report.dto.response.*;
import com.fitwallet.domain.report.mapper.BenefitReportMapper;
import com.fitwallet.domain.report.mapper.MissedBenefitMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultBenefitReportService implements BenefitReportService {

    private final BenefitReportMapper benefitReportMapper;
    // 놓친 혜택 분해(앱 미사용/카드 선택 손실)는 놓친 혜택 상세와 같은 집계라 매퍼를 재사용한다.
    private final MissedBenefitMapper missedBenefitMapper;
    // 카드 혜택 현황 캐러셀의 보유 카드 목록(앞면)은 카드 도메인 조회를 그대로 재사용한다.
    private final CardService cardService;
    // 카드 추천은 전용 엔진에 위임한다.
    private final CardRecommendationEngine cardRecommendationEngine;

    @Override
    @Transactional(readOnly = true)
    public BenefitSummaryResponse getBenefitSummary(Long userId, String yearMonth) {
        ReceivedBenefitSummaryResponse received = benefitReportMapper.getReceivedBenefitSummary(userId, yearMonth);
        MissedSummaryResponse missed = missedBenefitMapper.getMissedSummary(userId, yearMonth);
        BigDecimal totalMissed = missed.getAppUnusedAmount().add(missed.getCardMismatchAmount());

        List<CardListResponse> cards = cardService.findMyCards(userId, new CardListSearchRequest());
        List<CardRecommendationResponse> recommendations = cardRecommendationEngine.recommend(userId, yearMonth);

        return BenefitSummaryResponse.builder()
                .totalReceivedBenefit(received.getTotalReceivedBenefit())
                .totalDiscountAmount(received.getTotalDiscountAmount())
                .totalPoint(received.getTotalPoint())
                .totalMissedBenefit(totalMissed)
                .appUnusedAmount(missed.getAppUnusedAmount())
                .cardMismatchAmount(missed.getCardMismatchAmount())
                .cards(cards)
                .recommendations(recommendations)
                .build();
    }
}
