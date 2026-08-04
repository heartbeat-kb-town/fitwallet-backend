package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.CardTransactionCardInfo;
import com.fitwallet.domain.card.dto.CardTransactionSummaryType;
import com.fitwallet.domain.card.dto.CardType;
import com.fitwallet.domain.card.dto.CardMonthlyPeriod;
import com.fitwallet.domain.card.dto.CardUsageAmountSummary;
import com.fitwallet.domain.card.dto.CardUsageBenefitAllocation;
import com.fitwallet.domain.card.dto.CardUsageCardInfo;
import com.fitwallet.domain.card.dto.CardUsageRuleSet;
import com.fitwallet.domain.card.dto.CardUsageTierState;
import com.fitwallet.domain.card.dto.CardUsageTierStructure;
import com.fitwallet.domain.card.dto.MyDataCard;
import com.fitwallet.domain.card.dto.request.CardRegisterRequest;
import com.fitwallet.domain.card.dto.request.CardTransactionSearchCondition;
import com.fitwallet.domain.card.dto.request.CardTransactionSearchRequest;
import com.fitwallet.domain.card.dto.request.CardUsagePeriodCondition;
import com.fitwallet.domain.card.dto.request.CardUsageSearchRequest;
import com.fitwallet.domain.card.dto.response.CardListResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionCardResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionCursorResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionDetailResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionItemResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionSummaryResponse;
import com.fitwallet.domain.card.dto.response.CardUsageCardResponse;
import com.fitwallet.domain.card.dto.response.CardUsageDetailResponse;
import com.fitwallet.domain.card.dto.response.CardUsageSummaryResponse;
import com.fitwallet.domain.card.exception.CardErrorCode;
import com.fitwallet.domain.card.mapper.CardMapper;
import com.fitwallet.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private static final String CURSOR_DELIMITER = "|";

    private final CardMapper cardMapper;
    private final CardMonthlyPeriodResolver monthlyPeriodResolver;
    private final CardUsageRuleNormalizer usageRuleNormalizer;
    private final CardUsageTierIntegrator usageTierIntegrator;
    private final CardUsageBenefitAllocator usageBenefitAllocator;
    private final CardUsageTierStateCalculator usageTierStateCalculator;
    private final MyDataProvider myDataProvider;

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

        CardMonthlyPeriod period = monthlyPeriodResolver.resolve(
                request.getYearMonth(), card.getCardType());
        YearMonth yearMonth = period.getYearMonth();
        int requestedSize = resolveSize(request.getSize());

        LocalDateTime startAt = period.getStartAt();
        LocalDateTime endAt = period.getEndAt();
        DecodedCursor cursor = decodeCursor(request.getCursor(), cardId, yearMonth, startAt, endAt);

        CardTransactionSearchCondition condition = CardTransactionSearchCondition.builder()
                .startAt(startAt)
                .endAt(endAt)
                .cursorPaidAt(cursor == null ? null : cursor.paidAt)
                .cursorTransactionId(cursor == null ? null : cursor.transactionId)
                .limit(requestedSize + 1)
                .build();

        CardTransactionSummaryResponse paymentSummary = createPaymentSummary(
                userId, cardId, card, period.isCurrentMonth(), condition);
        CardTransactionCursorResponse transactions = createCursorResponse(
                cardId, yearMonth, requestedSize,
                cardMapper.findTransactions(userId, cardId, condition));

        return CardTransactionDetailResponse.builder()
                .card(toCardResponse(card))
                .yearMonth(yearMonth.toString())
                .availableYearMonths(period.getAvailableYearMonths())
                .paymentSummary(paymentSummary)
                .transactions(transactions)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CardUsageDetailResponse getCardUsage(
            Long userId,
            Long cardId,
            CardUsageSearchRequest request) {
        CardUsageCardInfo card = cardMapper.findUsageCardInfo(userId, cardId);
        if (card == null) {
            throw new BusinessException(CardErrorCode.CARD_NOT_FOUND);
        }

        CardMonthlyPeriod period = monthlyPeriodResolver.resolve(
                request == null ? null : request.getYearMonth(),
                card.getCardType());
        CardUsagePeriodCondition condition = CardUsagePeriodCondition.builder()
                .startAt(period.getStartAt())
                .endAt(period.getEndAt())
                .build();
        CardUsageAmountSummary amounts = cardMapper.findUsageAmounts(
                userId, cardId, condition);
        if (amounts == null || amounts.getRecognizedAmount() == null
                || amounts.getExcludedAmount() == null) {
            throw new IllegalStateException(
                    "카드 이용 실적 금액 집계 결과가 없습니다. cardId=" + cardId);
        }

        CardUsageRuleSet ruleSet = usageRuleNormalizer.normalize(
                card.getCardProductId(),
                cardMapper.findUsageBenefitRules(card.getCardProductId()));
        CardUsageTierStructure tierStructure = usageTierIntegrator.integrate(ruleSet);
        CardUsageBenefitAllocation benefitAllocation = usageBenefitAllocator.allocate(
                tierStructure, ruleSet.getBenefits());
        CardUsageTierState tierState = usageTierStateCalculator.calculate(
                amounts.getRecognizedAmount(), tierStructure, benefitAllocation);

        return CardUsageDetailResponse.builder()
                .card(CardUsageCardResponse.builder()
                        .cardName(card.getCardName())
                        .issuerName(card.getIssuerName())
                        .build())
                .yearMonth(period.getYearMonth().toString())
                .availableYearMonths(period.getAvailableYearMonths())
                .tierType(tierStructure.getTierType())
                .performanceStatus(tierState.getPerformanceStatus())
                .usageSummary(CardUsageSummaryResponse.builder()
                        .recognizedAmount(amounts.getRecognizedAmount())
                        .excludedAmount(amounts.getExcludedAmount())
                        .build())
                .currentTier(tierState.getCurrentTier())
                .nextTier(tierState.getNextTier())
                .amountUntilNextTier(tierState.getAmountUntilNextTier())
                .tierProgressRate(tierState.getTierProgressRate())
                .tiers(tierState.getTiers())
                .defaultBenefits(benefitAllocation.getDefaultBenefits())
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

    private int resolveSize(Integer requestedSize) {
        if (requestedSize == null) {
            return DEFAULT_TRANSACTION_SIZE;
        }
        if (requestedSize < MIN_TRANSACTION_SIZE || requestedSize > MAX_TRANSACTION_SIZE) {
            throw new BusinessException(CardErrorCode.INVALID_TRANSACTION_PAGE_SIZE);
        }
        return requestedSize;
    }

    private CardTransactionSummaryResponse createPaymentSummary(
            Long userId,
            Long cardId,
            CardTransactionCardInfo card,
            boolean currentMonth,
            CardTransactionSearchCondition condition) {
        if (card.getCardType() == CardType.CREDIT && currentMonth) {
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

    /**
     * 마이데이터에서 보유 카드와 최근 거래내역을 불러온다.
     * <p>
     * 회원가입 직후 온보딩과 마이페이지의 카드 불러오기에서 같은 API를 사용하므로
     * 이 메서드는 여러 번 호출될 수 있다.
     * <p>
     * Provider가 반환한 카드별로 기존 등록 여부를 확인하고, 처음 불러온 카드만 등록한다.
     * 이미 등록된 카드와 사용자가 삭제한 카드는 다시 등록하지 않는다.
     * 거래내역도 새로 등록된 카드의 내역만 함께 저장한다.
     * 새로 등록할 카드가 없어도 예외 없이 정상 종료한다.
     */
    @Override
    @Transactional
    public void connectMyData(Long userId) {
        List<MyDataCard> cards = myDataProvider.fetchCards(userId);

        int displayOrder = cardMapper.findMaxDisplayOrder(userId);
        for (MyDataCard card : cards) {
            Boolean registered = cardMapper.findDeletedFlag(userId, card.getCardProductId());
            if (registered != null) {
                continue;
            }

            displayOrder++;
            Map<String, Object> keyHolder = new HashMap<>();
            cardMapper.insertMyDataCard(userId, card, displayOrder, keyHolder);

            if (card.getTransactions() != null && !card.getTransactions().isEmpty()) {
                // keyHolder가 Map이라 MyBatis가 타입을 모른 채 JDBC 원본 값을
                // 그대로 넣는다 — MySQL 드라이버는 생성된 BIGINT를 Long이 아니라
                // BigInteger로 돌려주므로 (Long) 캐스팅은 여기서 실패한다.
                Long userCardId = ((Number) keyHolder.get("userCardId")).longValue();
                cardMapper.insertMyDataTransactions(userCardId, card.getTransactions());
            }
        }
    }
}
