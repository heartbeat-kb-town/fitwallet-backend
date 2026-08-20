package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.CardTransactionCardInfo;
import com.fitwallet.domain.card.dto.CardListSortType;
import com.fitwallet.domain.card.dto.CardSummaryCardInfo;
import com.fitwallet.domain.card.dto.CardType;
import com.fitwallet.domain.card.dto.CardMonthlyPeriod;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitPeriod;
import com.fitwallet.domain.card.dto.CardUsageAmountSummary;
import com.fitwallet.domain.card.dto.CardUsageBenefitRule;
import com.fitwallet.domain.card.dto.CardUsageCardInfo;
import com.fitwallet.domain.card.dto.CardUsageTierState;
import com.fitwallet.domain.card.dto.MyDataCard;
import com.fitwallet.domain.card.dto.request.CardDisplayOrderUpdateRequest;
import com.fitwallet.domain.card.dto.request.CardRegisterRequest;
import com.fitwallet.domain.card.dto.request.CardListSearchRequest;
import com.fitwallet.domain.card.dto.request.CardRecentTransactionSearchCondition;
import com.fitwallet.domain.card.dto.request.CardTransactionSearchCondition;
import com.fitwallet.domain.card.dto.request.CardTransactionSearchRequest;
import com.fitwallet.domain.card.dto.request.CardUsagePeriodCondition;
import com.fitwallet.domain.card.dto.request.CardUsageSearchRequest;
import com.fitwallet.domain.card.dto.response.CardListResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitResponse;
import com.fitwallet.domain.card.dto.response.CardEventCardResponse;
import com.fitwallet.domain.card.dto.response.CardEventItemResponse;
import com.fitwallet.domain.card.dto.response.CardEventResponse;
import com.fitwallet.domain.card.dto.response.CardSummaryAmountResponse;
import com.fitwallet.domain.card.dto.response.CardSummaryResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionDetailResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionItemResponse;
import com.fitwallet.domain.card.dto.response.CardSummaryTransactionResponse;
import com.fitwallet.domain.card.dto.response.CardUsageDetailResponse;
import com.fitwallet.domain.card.exception.CardErrorCode;
import com.fitwallet.domain.card.mapper.CardMapper;
import com.fitwallet.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private final CardMapper cardMapper;
    private final CardMonthlyPeriodResolver monthlyPeriodResolver;
    private final CardMonthlyBenefitPeriodResolver monthlyBenefitPeriodResolver;
    private final CardMonthlyBenefitCalculator monthlyBenefitCalculator;
    private final CardTransactionProcessor cardTransactionProcessor;
    private final CardSummaryAssembler cardSummaryAssembler;
    private final CardUsageCalculator cardUsageCalculator;
    private final MyDataProvider myDataProvider;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<CardListResponse> findMyCards(Long userId, CardListSearchRequest request) {
        CardListSortType sortType = request == null || request.getSort() == null
                ? CardListSortType.DISPLAY_ORDER
                : request.getSort();
        return cardMapper.findByUserId(userId, sortType, currentCreditPaymentCondition());
    }

    @Override
    @Transactional(readOnly = true)
    public CardMonthlyBenefitResponse getCardMonthlyBenefit(Long userId, Long cardId) {
        CardSummaryCardInfo card = cardMapper.findSummaryCardInfo(userId, cardId);
        if (card == null) {
            throw new BusinessException(CardErrorCode.CARD_NOT_FOUND);
        }

        CardMonthlyBenefitPeriod period = monthlyBenefitPeriodResolver.resolve();
        CardUsagePeriodCondition performanceCondition = CardUsagePeriodCondition.builder()
                .startAt(period.getPerformanceStartAt())
                .endAt(period.getPerformanceEndAt())
                .build();
        CardUsageAmountSummary performanceAmounts = cardMapper.findUsageAmounts(
                userId, cardId, performanceCondition);
        if (performanceAmounts == null || performanceAmounts.getRecognizedAmount() == null) {
            throw new BusinessException(CardErrorCode.INVALID_CARD_MONTHLY_BENEFIT_DATA);
        }
        List<CardUsageBenefitRule> performanceRules =
                cardMapper.findUsageBenefitRules(card.getCardProductId());
        CardUsageTierState performanceTierState = cardUsageCalculator.calculateTierState(
                card.getCardProductId(), performanceAmounts.getRecognizedAmount(), performanceRules);

        CardUsagePeriodCondition benefitCondition = CardUsagePeriodCondition.builder()
                .startAt(period.getBenefitStartAt())
                .endAt(period.getBenefitEndAt())
                .build();
        return monthlyBenefitCalculator.calculate(
                card,
                period,
                performanceAmounts.getRecognizedAmount(),
                performanceTierState,
                cardMapper.findMonthlyBenefitRules(card.getCardProductId()),
                cardMapper.findMonthlyBenefitCategoryTargets(card.getCardProductId()),
                cardMapper.findMonthlyBenefitBrandTargets(card.getCardProductId()),
                cardMapper.findMonthlyBenefitTargetUsages(userId, cardId, benefitCondition));
    }

    @Override
    @Transactional(readOnly = true)
    public CardSummaryResponse findCardSummary(Long userId, Long cardId) {
        CardSummaryCardInfo card = cardMapper.findSummaryCardInfo(userId, cardId);
        if (card == null) {
            throw new BusinessException(CardErrorCode.CARD_NOT_FOUND);
        }

        LocalDate today = LocalDate.now(clock);
        CardMonthlyPeriod monthlyPeriod = monthlyPeriodResolver.resolve(null, card.getCardType());
        BigDecimal creditUsageAmount = null;
        if (card.getCardType() == CardType.CREDIT) {
            CardTransactionSearchCondition amountCondition = CardTransactionSearchCondition.builder()
                    .startAt(monthlyPeriod.getStartAt())
                    .endAt(monthlyPeriod.getEndAt())
                    .build();
            creditUsageAmount = cardMapper.sumTransactionAmount(
                    userId, cardId, amountCondition);
        }
        CardSummaryAmountResponse amountSummary = cardSummaryAssembler.createAmount(
                card, monthlyPeriod, today, creditUsageAmount);

        CardRecentTransactionSearchCondition recentCondition =
                CardRecentTransactionSearchCondition.builder()
                        .startAt(today.minusDays(1).atStartOfDay())
                        .endAt(today.plusDays(1).atStartOfDay())
                        .build();

        CardUsageCardInfo usageCard = CardUsageCardInfo.builder()
                .cardProductId(card.getCardProductId())
                .cardName(card.getCardName())
                .issuerName(card.getIssuerName())
                .cardType(card.getCardType())
                .build();
        CardUsageDetailResponse usageDetail = createCardUsageDetail(
                userId, cardId, null, usageCard, null);

        List<CardSummaryTransactionResponse> recentTransactions =
                cardMapper.findRecentTransactions(
                        userId, cardId, recentCondition);
        return cardSummaryAssembler.createResponse(
                card, amountSummary, recentTransactions, usageDetail);
    }

    @Override
    @Transactional(readOnly = true)
    public CardEventResponse findCardEvents(Long userId, Long cardId) {
        CardSummaryCardInfo card = cardMapper.findSummaryCardInfo(userId, cardId);
        if (card == null) {
            throw new BusinessException(CardErrorCode.CARD_NOT_FOUND);
        }

        LocalDate today = LocalDate.now(clock);
        List<CardEventItemResponse> events = cardMapper.findCardEventItems(userId, cardId, today);
        if (events == null) {
            events = List.of();
        }
        for (CardEventItemResponse event : events) {
            if (event.getSummary() == null || event.getSummary().isBlank()) {
                throw new BusinessException(CardErrorCode.INVALID_CARD_EVENT_DATA);
            }
        }

        CardEventCardResponse cardResponse = CardEventCardResponse.builder()
                .cardId(card.getCardId())
                .cardProductId(card.getCardProductId())
                .cardName(card.getCardName())
                .issuerName(card.getIssuerName())
                .build();
        return CardEventResponse.builder()
                .card(cardResponse)
                .eventCount(events.size())
                .events(events)
                .build();
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

        LocalDateTime oldestTransactionPaidAt =
                cardMapper.findOldestTransactionPaidAt(userId, cardId);
        CardMonthlyPeriod period = monthlyPeriodResolver.resolve(
                request.getYearMonth(), card.getCardType(), oldestTransactionPaidAt);
        CardTransactionProcessor.PreparedTransactionQuery query =
                cardTransactionProcessor.prepareQuery(cardId, request, period);
        BigDecimal totalAmount = card.getCardType() == CardType.CREDIT
                ? cardMapper.sumScheduledPaymentAmount(
                        userId, cardId, query.getSummaryCondition())
                : cardMapper.sumTransactionAmount(
                        userId, cardId, query.getSummaryCondition());
        List<CardTransactionItemResponse> transactions = cardMapper.findTransactions(
                userId, cardId, query.getPageCondition());
        return cardTransactionProcessor.createResponse(card, query, totalAmount, transactions);
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

        LocalDateTime oldestTransactionPaidAt =
                cardMapper.findOldestTransactionPaidAt(userId, cardId);
        return createCardUsageDetail(
                userId,
                cardId,
                request == null ? null : request.getYearMonth(),
                card,
                oldestTransactionPaidAt);
    }

    private CardUsageDetailResponse createCardUsageDetail(
            Long userId,
            Long cardId,
            String requestedYearMonth,
            CardUsageCardInfo card,
            LocalDateTime oldestTransactionPaidAt) {
        CardMonthlyPeriod period = monthlyPeriodResolver.resolve(
                requestedYearMonth, card.getCardType(), oldestTransactionPaidAt);
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

        List<CardUsageBenefitRule> rules =
                cardMapper.findUsageBenefitRules(card.getCardProductId());
        return cardUsageCalculator.calculateDetail(card, period, amounts, rules);
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

        return cardMapper.findByUserIdAndCardProductId(
                userId, request.getCardProductId(), currentCreditPaymentCondition());
    }

    private CardTransactionSearchCondition currentCreditPaymentCondition() {
        CardMonthlyPeriod period = monthlyPeriodResolver.resolve(null, CardType.CREDIT);
        return CardTransactionSearchCondition.builder()
                .startAt(period.getStartAt())
                .endAt(period.getEndAt())
                .build();
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

    /**
     * 보유 카드 표시 순서를 변경한다.
     * <p>
     * 요청 목록이 보유 카드(삭제되지 않은 것) 전체와 정확히 일치해야 한다 — 개수가 다르거나
     * (일부 누락·중복), 남의 카드나 삭제된 카드 ID가 섞였으면 거부한다. 일부만 반영하면
     * 나머지 카드의 순서가 정의되지 않은 채로 남기 때문이다.
     */
    @Override
    @Transactional
    public void updateCardsDisplayOrder(Long userId, CardDisplayOrderUpdateRequest request) {
        List<Long> requestedIds = request.getUserCardIds();
        Set<Long> requestedIdSet = new HashSet<>(requestedIds);
        Set<Long> ownedIdSet = new HashSet<>(cardMapper.findUserCardIds(userId));

        if (requestedIds.size() != requestedIdSet.size() || !requestedIdSet.equals(ownedIdSet)) {
            throw new BusinessException(CardErrorCode.INVALID_CARD_DISPLAY_ORDER);
        }

        // 빈 리스트면 매퍼를 안 태운다. XML의 foreach가 분기 없는 CASE와 빈 IN()을 만들어
        // MySQL 문법 오류가 난다 — 카드가 없는 사용자의 정상적인 no-op 요청이라 여기서 막는다.
        if (requestedIds.isEmpty()) {
            return;
        }

        cardMapper.updateCardsDisplayOrder(userId, requestedIds);
    }
}
