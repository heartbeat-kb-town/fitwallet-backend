package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.CardMonthlyPeriod;
import com.fitwallet.domain.card.dto.CardSummaryCardInfo;
import com.fitwallet.domain.card.dto.CardType;
import com.fitwallet.domain.card.dto.CardUsageTierType;
import com.fitwallet.domain.card.dto.response.CardSummaryAmountResponse;
import com.fitwallet.domain.card.dto.response.CardSummaryCardResponse;
import com.fitwallet.domain.card.dto.response.CardSummaryResponse;
import com.fitwallet.domain.card.dto.response.CardSummaryTierResponse;
import com.fitwallet.domain.card.dto.response.CardSummaryTransactionResponse;
import com.fitwallet.domain.card.dto.response.CardSummaryUsageResponse;
import com.fitwallet.domain.card.dto.response.CardUsageDetailResponse;
import com.fitwallet.domain.card.dto.response.CardUsageTierSummaryResponse;
import com.fitwallet.domain.card.exception.CardErrorCode;
import com.fitwallet.global.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
public class CardSummaryAssembler {

    CardSummaryAmountResponse createAmount(
            CardSummaryCardInfo card,
            CardMonthlyPeriod monthlyPeriod,
            LocalDate today,
            BigDecimal creditUsageAmount) {
        if (card.getCardType() == CardType.CREDIT) {
            return CardSummaryAmountResponse.builder()
                    .creditUsageAmount(creditUsageAmount)
                    .asOfDate(monthlyPeriod.getEndAt().toLocalDate().minusDays(1))
                    .build();
        }
        if (card.getBalance() == null
                || card.getBankName() == null
                || card.getBankName().isBlank()) {
            throw new BusinessException(CardErrorCode.INVALID_CARD_SUMMARY_DATA);
        }
        return CardSummaryAmountResponse.builder()
                .balance(card.getBalance())
                .bankName(card.getBankName())
                .asOfDate(today)
                .build();
    }

    CardSummaryResponse createResponse(
            CardSummaryCardInfo card,
            CardSummaryAmountResponse amountSummary,
            List<CardSummaryTransactionResponse> recentTransactions,
            CardUsageDetailResponse usageDetail) {
        return CardSummaryResponse.builder()
                .card(toSummaryCard(card))
                .amountSummary(amountSummary)
                .recentTransactions(recentTransactions)
                .usageSummary(toSummaryUsage(usageDetail))
                .build();
    }

    private CardSummaryCardResponse toSummaryCard(CardSummaryCardInfo card) {
        return CardSummaryCardResponse.builder()
                .cardId(card.getCardId())
                .cardProductId(card.getCardProductId())
                .cardName(card.getCardName())
                .issuerName(card.getIssuerName())
                .cardImageUrl(card.getCardImageUrl())
                .cardType(card.getCardType())
                .build();
    }

    private CardSummaryUsageResponse toSummaryUsage(CardUsageDetailResponse usage) {
        boolean noRequirement = usage.getTierType() == CardUsageTierType.NO_REQUIREMENT;
        return CardSummaryUsageResponse.builder()
                .yearMonth(usage.getYearMonth())
                .tierType(usage.getTierType())
                .performanceStatus(usage.getPerformanceStatus())
                .recognizedAmount(noRequirement
                        ? null : usage.getUsageSummary().getRecognizedAmount())
                .currentTier(toSummaryTier(usage.getCurrentTier()))
                .nextTier(toSummaryTier(usage.getNextTier()))
                .amountUntilNextTier(usage.getAmountUntilNextTier())
                .tierProgressRate(usage.getTierProgressRate())
                .build();
    }

    private CardSummaryTierResponse toSummaryTier(CardUsageTierSummaryResponse tier) {
        if (tier == null) {
            return null;
        }
        return CardSummaryTierResponse.builder()
                .tierName(tier.getTierName())
                .build();
    }
}
