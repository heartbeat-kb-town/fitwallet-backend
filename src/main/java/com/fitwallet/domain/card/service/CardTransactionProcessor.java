package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.CardMonthlyPeriod;
import com.fitwallet.domain.card.dto.CardTransactionCardInfo;
import com.fitwallet.domain.card.dto.CardTransactionSummaryType;
import com.fitwallet.domain.card.dto.CardType;
import com.fitwallet.domain.card.dto.request.CardTransactionSearchCondition;
import com.fitwallet.domain.card.dto.request.CardTransactionSearchRequest;
import com.fitwallet.domain.card.dto.response.CardTransactionCardResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionCursorResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionDetailResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionItemResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionSummaryResponse;
import com.fitwallet.domain.card.exception.CardErrorCode;
import com.fitwallet.global.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
public class CardTransactionProcessor {

    private static final int DEFAULT_TRANSACTION_SIZE = 20;
    private static final int MIN_TRANSACTION_SIZE = 1;
    private static final int MAX_TRANSACTION_SIZE = 100;
    private static final String CURSOR_DELIMITER = "|";

    PreparedTransactionQuery prepareQuery(
            Long cardId,
            CardTransactionSearchRequest request,
            CardMonthlyPeriod period) {
        int requestedSize = resolveSize(request.getSize());
        LocalDateTime startAt = period.getStartAt();
        LocalDateTime endAt = period.getEndAt();
        DecodedCursor cursor = decodeCursor(
                request.getCursor(), cardId, period.getYearMonth(), startAt, endAt);

        CardTransactionSearchCondition summaryCondition = CardTransactionSearchCondition.builder()
                .startAt(startAt)
                .endAt(endAt)
                .build();
        CardTransactionSearchCondition pageCondition = CardTransactionSearchCondition.builder()
                .startAt(startAt)
                .endAt(endAt)
                .cursorPaidAt(cursor == null ? null : cursor.paidAt)
                .cursorTransactionId(cursor == null ? null : cursor.transactionId)
                .limit(requestedSize + 1)
                .build();
        return new PreparedTransactionQuery(
                period, summaryCondition, pageCondition, requestedSize);
    }

    CardTransactionDetailResponse createResponse(
            CardTransactionCardInfo card,
            PreparedTransactionQuery query,
            BigDecimal totalAmount,
            List<CardTransactionItemResponse> fetchedTransactions) {
        return CardTransactionDetailResponse.builder()
                .card(toCardResponse(card))
                .yearMonth(query.period.getYearMonth().toString())
                .availableYearMonths(query.period.getAvailableYearMonths())
                .paymentSummary(CardTransactionSummaryResponse.builder()
                        .summaryType(card.getCardType() == CardType.CREDIT
                                ? CardTransactionSummaryType.SCHEDULED_PAYMENT
                                : CardTransactionSummaryType.MONTHLY_PAYMENT_AMOUNT)
                        .amount(totalAmount)
                        .build())
                .transactions(createCursorResponse(
                        card.getCardId(), query.period.getYearMonth(),
                        query.requestedSize, fetchedTransactions))
                .build();
    }

    private int resolveSize(Integer requestedSize) {
        if (requestedSize == null) {
            return DEFAULT_TRANSACTION_SIZE;
        }
        if (requestedSize < MIN_TRANSACTION_SIZE || requestedSize > MAX_TRANSACTION_SIZE) {
            throw new BusinessException(CardErrorCode.INVALID_TRANSACTION_PAGE_SIZE);
        }
        return requestedSize;
    }

    private CardTransactionCursorResponse createCursorResponse(
            Long cardId,
            YearMonth yearMonth,
            int requestedSize,
            List<CardTransactionItemResponse> fetchedTransactions) {
        boolean hasNext = fetchedTransactions.size() > requestedSize;
        int contentSize = Math.min(fetchedTransactions.size(), requestedSize);
        List<CardTransactionItemResponse> content =
                new ArrayList<>(fetchedTransactions.subList(0, contentSize));

        String nextCursor = null;
        if (hasNext) {
            CardTransactionItemResponse lastTransaction = content.get(content.size() - 1);
            nextCursor = encodeCursor(cardId, yearMonth, lastTransaction);
        }

        return CardTransactionCursorResponse.builder()
                .content(content)
                .size(content.size())
                .hasNext(hasNext)
                .nextCursor(nextCursor)
                .build();
    }

    private CardTransactionCardResponse toCardResponse(CardTransactionCardInfo card) {
        return CardTransactionCardResponse.builder()
                .cardId(card.getCardId())
                .cardProductId(card.getCardProductId())
                .cardName(card.getCardName())
                .issuerName(card.getIssuerName())
                .cardImageUrl(card.getCardImageUrl())
                .cardType(card.getCardType())
                .maskedRearNumber(card.getMaskedRearNumber())
                .build();
    }

    private DecodedCursor decodeCursor(
            String encodedCursor,
            Long requestedCardId,
            YearMonth requestedYearMonth,
            LocalDateTime startAt,
            LocalDateTime endAt) {
        if (encodedCursor == null) {
            return null;
        }

        try {
            String rawCursor = new String(
                    Base64.getUrlDecoder().decode(encodedCursor), StandardCharsets.UTF_8);
            String[] values = rawCursor.split("\\|", -1);
            if (values.length != 4) {
                throw new IllegalArgumentException();
            }

            Long cardId = Long.valueOf(values[0]);
            YearMonth yearMonth = YearMonth.parse(values[1]);
            LocalDateTime paidAt = LocalDateTime.parse(values[2]);
            Long transactionId = Long.valueOf(values[3]);

            if (!requestedCardId.equals(cardId)
                    || !requestedYearMonth.equals(yearMonth)
                    || transactionId <= 0
                    || paidAt.isBefore(startAt)
                    || !paidAt.isBefore(endAt)) {
                throw new IllegalArgumentException();
            }
            return new DecodedCursor(paidAt, transactionId);
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new BusinessException(CardErrorCode.INVALID_TRANSACTION_CURSOR);
        }
    }

    private String encodeCursor(
            Long cardId,
            YearMonth yearMonth,
            CardTransactionItemResponse transaction) {
        String rawCursor = String.join(
                CURSOR_DELIMITER,
                cardId.toString(),
                yearMonth.toString(),
                transaction.getPaidAt().toString(),
                transaction.getTransactionId().toString());
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(rawCursor.getBytes(StandardCharsets.UTF_8));
    }

    static final class PreparedTransactionQuery {

        private final CardMonthlyPeriod period;
        private final CardTransactionSearchCondition summaryCondition;
        private final CardTransactionSearchCondition pageCondition;
        private final int requestedSize;

        private PreparedTransactionQuery(
                CardMonthlyPeriod period,
                CardTransactionSearchCondition summaryCondition,
                CardTransactionSearchCondition pageCondition,
                int requestedSize) {
            this.period = period;
            this.summaryCondition = summaryCondition;
            this.pageCondition = pageCondition;
            this.requestedSize = requestedSize;
        }

        CardTransactionSearchCondition getSummaryCondition() {
            return summaryCondition;
        }

        CardTransactionSearchCondition getPageCondition() {
            return pageCondition;
        }
    }

    private static final class DecodedCursor {

        private final LocalDateTime paidAt;
        private final Long transactionId;

        private DecodedCursor(LocalDateTime paidAt, Long transactionId) {
            this.paidAt = paidAt;
            this.transactionId = transactionId;
        }
    }
}
