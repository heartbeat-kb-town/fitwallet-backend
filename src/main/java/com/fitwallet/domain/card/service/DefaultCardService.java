package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.CardTransactionCardInfo;
import com.fitwallet.domain.card.dto.CardTransactionSummaryType;
import com.fitwallet.domain.card.dto.CardType;
import com.fitwallet.domain.card.dto.request.CardRegisterRequest;
import com.fitwallet.domain.card.dto.request.CardTransactionSearchCondition;
import com.fitwallet.domain.card.dto.request.CardTransactionSearchRequest;
import com.fitwallet.domain.card.dto.response.CardListResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionCardResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionCursorResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionDetailResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionItemResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionSummaryResponse;
import com.fitwallet.domain.card.exception.CardErrorCode;
import com.fitwallet.domain.card.mapper.CardMapper;
import com.fitwallet.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * {@code @Transactional}은 인터페이스가 아니라 여기, 구현체 메서드에 붙인다.
 * 지금은 JDK 동적 프록시라 인터페이스에 붙여도 동작하지만, 나중에 CGLIB
 * (proxy-target-class="true")로 바뀌면 인터페이스의 애너테이션은 조용히 무시된다.
 * <p>
 * 컨트롤러에 붙이면 애초에 안 걸린다 — {@code <tx:annotation-driven>}이 root-context에
 * 있고 컨트롤러는 servlet-context에서 스캔되기 때문이다.
 */
@Service
@RequiredArgsConstructor
public class DefaultCardService implements CardService {

    private static final int DEFAULT_TRANSACTION_SIZE = 20;
    private static final int MIN_TRANSACTION_SIZE = 1;
    private static final int MAX_TRANSACTION_SIZE = 100;
    private static final int AVAILABLE_MONTH_COUNT = 3;
    private static final String CURSOR_DELIMITER = "|";

    private final CardMapper cardMapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<CardListResponse> findMyCards(Long userId) {
        return cardMapper.findByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public CardListResponse findMyCard(Long userId, Long userCardId) {
        CardListResponse card = cardMapper.findByUserIdAndUserCardId(userId, userCardId);
        if (card == null) {
            throw new BusinessException(CardErrorCode.CARD_NOT_FOUND);
        }
        return card;
    }

    @Override
    @Transactional(readOnly = true)
    public CardTransactionDetailResponse getCardTransactions(
            Long userId,
            Long cardId,
            CardTransactionSearchRequest request) {
        CardTransactionCardInfo card = cardMapper.findTransactionCardInfo(userId, cardId);
        if (card == null) {
            throw new BusinessException(CardErrorCode.CARD_NOT_FOUND);
        }

        LocalDate today = LocalDate.now(clock);
        YearMonth currentYearMonth = YearMonth.from(today);
        YearMonth yearMonth = resolveYearMonth(request.getYearMonth(), currentYearMonth);
        int requestedSize = resolveSize(request.getSize());

        LocalDateTime startAt = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime endAt = resolveEndAt(card.getCardType(), yearMonth, currentYearMonth, today);
        DecodedCursor cursor = decodeCursor(request.getCursor(), cardId, yearMonth, startAt, endAt);

        CardTransactionSearchCondition condition = CardTransactionSearchCondition.builder()
                .startAt(startAt)
                .endAt(endAt)
                .cursorPaidAt(cursor == null ? null : cursor.paidAt)
                .cursorTransactionId(cursor == null ? null : cursor.transactionId)
                .limit(requestedSize + 1)
                .build();

        CardTransactionSummaryResponse paymentSummary = createPaymentSummary(
                userId, cardId, card, yearMonth, currentYearMonth, condition);
        CardTransactionCursorResponse transactions = createCursorResponse(
                cardId, yearMonth, requestedSize,
                cardMapper.findTransactions(userId, cardId, condition));

        return CardTransactionDetailResponse.builder()
                .card(toCardResponse(card))
                .yearMonth(yearMonth.toString())
                .availableYearMonths(createAvailableYearMonths(currentYearMonth))
                .paymentSummary(paymentSummary)
                .transactions(transactions)
                .build();
    }

    /**
     * 카드를 등록한다.
     * <p>
     * {@code (user_id, card_product_id)}에 UNIQUE가 걸려 있고 삭제는 소프트 삭제라,
     * 예전에 지운 카드를 다시 등록하면 새 행을 넣지 않고 기존 행을 되살린다.
     */
    @Override
    @Transactional
    public CardListResponse register(Long userId, CardRegisterRequest request) {
        Boolean deleted = cardMapper.findDeletedFlag(userId, request.getCardProductId());
        if (Boolean.FALSE.equals(deleted)) {
            throw new BusinessException(CardErrorCode.CARD_ALREADY_REGISTERED);
        }

        int displayOrder = cardMapper.findMaxDisplayOrder(userId) + 1;
        if (deleted == null) {
            cardMapper.insertUserCard(userId, request, displayOrder);
        } else {
            cardMapper.reactivateUserCard(userId, request, displayOrder);
        }

        return cardMapper.findByUserIdAndCardProductId(userId, request.getCardProductId());
    }

    private YearMonth resolveYearMonth(String requestedYearMonth, YearMonth currentYearMonth) {
        if (requestedYearMonth == null || requestedYearMonth.isBlank()) {
            return currentYearMonth;
        }

        final YearMonth yearMonth;
        try {
            yearMonth = YearMonth.parse(requestedYearMonth);
        } catch (DateTimeException exception) {
            throw new BusinessException(CardErrorCode.INVALID_YEAR_MONTH);
        }

        if (yearMonth.isAfter(currentYearMonth)
                || yearMonth.isBefore(currentYearMonth.minusMonths(AVAILABLE_MONTH_COUNT - 1L))) {
            throw new BusinessException(CardErrorCode.YEAR_MONTH_OUT_OF_RANGE);
        }
        return yearMonth;
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

    private LocalDateTime resolveEndAt(CardType cardType, YearMonth yearMonth,
                                       YearMonth currentYearMonth, LocalDate today) {
        if (!yearMonth.equals(currentYearMonth)) {
            return yearMonth.plusMonths(1).atDay(1).atStartOfDay();
        }
        if (cardType == CardType.CREDIT) {
            return today.atStartOfDay();
        }
        return today.plusDays(1).atStartOfDay();
    }

    private CardTransactionSummaryResponse createPaymentSummary(
            Long userId,
            Long cardId,
            CardTransactionCardInfo card,
            YearMonth yearMonth,
            YearMonth currentYearMonth,
            CardTransactionSearchCondition condition) {
        if (card.getCardType() == CardType.CREDIT && yearMonth.equals(currentYearMonth)) {
            BigDecimal scheduledPaymentAmount = card.getScheduledPaymentAmount();
            if (scheduledPaymentAmount == null) {
                throw new BusinessException(CardErrorCode.INVALID_CARD_PAYMENT_DATA);
            }
            return CardTransactionSummaryResponse.builder()
                    .summaryType(CardTransactionSummaryType.SCHEDULED_PAYMENT)
                    .amount(scheduledPaymentAmount)
                    .build();
        }

        return CardTransactionSummaryResponse.builder()
                .summaryType(CardTransactionSummaryType.MONTHLY_PAYMENT_AMOUNT)
                .amount(cardMapper.sumTransactionAmount(userId, cardId, condition))
                .build();
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

    private List<String> createAvailableYearMonths(YearMonth currentYearMonth) {
        List<String> availableYearMonths = new ArrayList<>(AVAILABLE_MONTH_COUNT);
        for (int index = 0; index < AVAILABLE_MONTH_COUNT; index++) {
            availableYearMonths.add(currentYearMonth.minusMonths(index).toString());
        }
        return availableYearMonths;
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

    private static final class DecodedCursor {

        private final LocalDateTime paidAt;
        private final Long transactionId;

        private DecodedCursor(LocalDateTime paidAt, Long transactionId) {
            this.paidAt = paidAt;
            this.transactionId = transactionId;
        }
    }
}
