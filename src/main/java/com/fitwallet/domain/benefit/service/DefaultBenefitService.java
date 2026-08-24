package com.fitwallet.domain.benefit.service;

import com.fitwallet.domain.benefit.dto.BenefitReasonCode;
import com.fitwallet.domain.benefit.dto.BenefitType;
import com.fitwallet.domain.benefit.dto.CardBenefitStatus;
import com.fitwallet.domain.benefit.dto.LimitBasis;
import com.fitwallet.domain.benefit.dto.LimitPeriod;
import com.fitwallet.domain.benefit.dto.ValueType;
import com.fitwallet.domain.benefit.dto.response.BenefitCandidateResponse;
import com.fitwallet.domain.benefit.dto.response.BenefitLimitResponse;
import com.fitwallet.domain.benefit.dto.response.BenefitPrevMonthSpendResponse;
import com.fitwallet.domain.benefit.dto.response.BenefitStoreResponse;
import com.fitwallet.domain.benefit.dto.response.BenefitUsageResponse;
import com.fitwallet.domain.benefit.dto.response.BenefitUserCardResponse;
import com.fitwallet.domain.benefit.dto.response.BenefitDetailResponse;
import com.fitwallet.domain.benefit.dto.response.BenefitReasonResponse;
import com.fitwallet.domain.benefit.dto.response.CardBenefitResponse;
import com.fitwallet.domain.benefit.dto.response.ExpectedBenefitResponse;
import com.fitwallet.domain.benefit.dto.response.ExpectedBenefitStoreResponse;
import com.fitwallet.domain.benefit.dto.response.PaymentBenefitResponse;
import com.fitwallet.domain.benefit.exception.BenefitErrorCode;
import com.fitwallet.domain.benefit.mapper.BenefitMapper;
import com.fitwallet.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
public class DefaultBenefitService implements BenefitService {

    private static final String NO_BENEFIT_MESSAGE = "이 결제에는 적용되는 혜택이 없어요.";
    private static final String PREV_SPEND_NOT_MET_MESSAGE = "전월실적 조건이 부족해서 혜택을 받을 수 없어요.";

    /** 카드 목록 정렬 기준. enum 선언 순서(ordinal)에 기대지 않고 명시적으로 둔다. */
    private static final Map<CardBenefitStatus, Integer> STATUS_GROUP_RANK = Map.of(
            CardBenefitStatus.AVAILABLE, 0,
            CardBenefitStatus.CONDITION_NOT_MET, 1,
            CardBenefitStatus.NO_BENEFIT, 2);

    /** 소진된 한도가 여러 개일 때 메시지로 고를 우선순위. */
    private static final List<LimitPeriod> EXHAUSTED_PRIORITY = List.of(
            LimitPeriod.DAY, LimitPeriod.MONTH, LimitPeriod.YEAR);

    /**
     * 금액을 모를 때 혜택끼리 매기는 우선순위 —
     * valueType(FIXED→RATE) → benefitType(CASHBACK→ACCUMULATE) → valueNumber 내림차순 → serviceId 오름차순.
     * <p>
     * 앞의 두 단이 <b>단위를 먼저 갈라놓기 때문에</b> valueNumber 단에서 원과 포인트 개수를 직접 비교하는
     * 일은 생기지 않는다 — 정액 캐시백끼리는 원, 정액 적립끼리는 포인트 개수, 정률끼리는 %끼리만 비교한다.
     * <p>
     * 카드 <b>안</b>에서 대표 혜택을 고를 때({@link #pickWinner})와 카드 <b>사이</b>를 정렬할 때
     * ({@link #sortAndRank}) 같은 comparator를 쓴다. 두 기준이 갈리면 3,000원 혜택을 가진 카드가
     * 500원짜리로 대표돼 뒤로 밀리는 모순이 생긴다.
     */
    private static final Comparator<BenefitCandidateResponse> BENEFIT_PRIORITY =
            Comparator.<BenefitCandidateResponse, Integer>comparing(c -> c.getValueType() == ValueType.FIXED ? 0 : 1)
                    .thenComparing(c -> c.getBenefitType() == BenefitType.CASHBACK ? 0 : 1)
                    .thenComparing(BenefitCandidateResponse::getValueNumber, Comparator.<BigDecimal>reverseOrder())
                    .thenComparing(BenefitCandidateResponse::getServiceId);

    private final BenefitMapper benefitMapper;
    private final BenefitAmountCalculator benefitAmountCalculator;

    /**
     * 금액을 모르는 조회. 3-인자 오버로드에 {@code null}을 넘긴다.
     * <p>
     * {@code @Transactional}을 여기에도 붙이는 건 형식이 아니다 — 아래 호출은 프록시를 거치지 않는
     * 자기 호출(self-invocation)이라, 이 메서드에 없으면 3-인자 쪽 애너테이션이 걸리지 않는다.
     */
    @Override
    @Transactional(readOnly = true)
    public ExpectedBenefitResponse findExpectedBenefits(Long userId, String storeId) {
        return findExpectedBenefits(userId, storeId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public ExpectedBenefitResponse findExpectedBenefits(Long userId, String storeId, String amount) {
        Long resolvedStoreId = resolveStoreId(storeId);
        BigDecimal resolvedAmount = resolveAmount(amount);

        BenefitStoreResponse store = findStore(resolvedStoreId);

        List<CardEvaluation> evaluations = evaluateCards(userId, store, resolvedAmount);
        if (evaluations.isEmpty()) {
            return ExpectedBenefitResponse.builder()
                    .store(toStoreResponse(store))
                    .hasCard(false)
                    .cards(List.of())
                    .build();
        }

        return ExpectedBenefitResponse.builder()
                .store(toStoreResponse(store))
                .hasCard(true)
                .cards(sortAndRank(evaluations, resolvedAmount))
                .build();
    }

    /**
     * 판정 자체는 {@code findExpectedBenefits}와 같은 {@link #evaluateCards}를 탄다 —
     * 두 화면이 다른 금액을 답하지 않게 하는 것이 이 메서드의 존재 이유이므로 계산을 복제하지 않는다.
     * 여기서만 다른 것은 <b>결과를 어떤 모양으로 내보내느냐</b>뿐이다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<PaymentBenefitResponse> findPaymentBenefits(Long userId, Long storeId, BigDecimal amount) {
        BenefitStoreResponse store = findStore(storeId);

        return evaluateCards(userId, store, amount).stream()
                .filter(evaluation -> evaluation.status() == CardBenefitStatus.AVAILABLE)
                .map(evaluation -> toPaymentBenefit(evaluation, amount))
                // Stream.sorted는 안정 정렬이라 동점이면 카드 표시 순서가 그대로 남는다.
                .sorted(Comparator.comparing(PaymentBenefitResponse::getExpectedAmount).reversed())
                .collect(Collectors.toList());
    }

    private BenefitStoreResponse findStore(Long storeId) {
        BenefitStoreResponse store = benefitMapper.findStore(storeId);
        if (store == null) {
            throw new BusinessException(BenefitErrorCode.STORE_NOT_FOUND);
        }
        return store;
    }

    /** 보유 카드가 없으면 빈 목록이다 — 호출부가 {@code hasCard=false}와 구분해 쓴다. */
    private List<CardEvaluation> evaluateCards(Long userId, BenefitStoreResponse store, BigDecimal amount) {
        List<BenefitUserCardResponse> userCards = benefitMapper.findUserCards(userId);
        if (userCards.isEmpty()) {
            return List.of();
        }

        List<Long> userCardIds = userCards.stream()
                .map(BenefitUserCardResponse::getUserCardId)
                .collect(Collectors.toList());
        Map<Long, BigDecimal> prevMonthSpends = resolvePrevMonthSpends(userCardIds);

        return userCards.stream()
                .map(card -> evaluateCard(card, store,
                        prevMonthSpends.getOrDefault(card.getUserCardId(), BigDecimal.ZERO), amount))
                .collect(Collectors.toList());
    }

    private Long resolveStoreId(String storeId) {
        if (storeId == null || storeId.isBlank()) {
            throw new BusinessException(BenefitErrorCode.STORE_ID_REQUIRED);
        }
        try {
            return Long.valueOf(storeId.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(BenefitErrorCode.STORE_ID_REQUIRED);
        }
    }

    /**
     * 결제 예정 금액. 컨트롤러가 {@code String}으로 넘긴 값을 여기서 파싱한다.
     * <p>
     * 값이 없으면({@code null}·공백) <b>금액을 모르는 조회</b>로 보고 {@code null}을 돌려준다 —
     * 홈 화면이 가맹점만 알고 금액을 모르는 시점에 이렇게 부른다. 에러가 아니다.
     */
    private BigDecimal resolveAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(amount.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(BenefitErrorCode.AMOUNT_INVALID);
        }
        if (parsed.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(BenefitErrorCode.AMOUNT_INVALID);
        }
        return parsed;
    }

    private ExpectedBenefitStoreResponse toStoreResponse(BenefitStoreResponse store) {
        return ExpectedBenefitStoreResponse.builder()
                .storeId(store.getStoreId())
                .storeName(store.getStoreName())
                .build();
    }

    private Map<Long, BigDecimal> resolvePrevMonthSpends(List<Long> userCardIds) {
        return benefitMapper.findPrevMonthSpends(userCardIds).stream()
                .collect(Collectors.toMap(
                        BenefitPrevMonthSpendResponse::getUserCardId,
                        BenefitPrevMonthSpendResponse::getPrevMonthSpend));
    }

    private CardEvaluation evaluateCard(BenefitUserCardResponse card, BenefitStoreResponse store,
                                         BigDecimal prevMonthSpend, BigDecimal amount) {
        List<BenefitCandidateResponse> candidates = benefitMapper.findCandidates(
                card.getCardProductId(), prevMonthSpend, store.getBrandId(), store.getCategoryId());

        if (candidates.isEmpty()) {
            return new CardEvaluation(card, CardBenefitStatus.NO_BENEFIT,
                    reason(BenefitReasonCode.NO_BENEFIT_FOR_STORE, NO_BENEFIT_MESSAGE), null);
        }

        List<BenefitCandidateResponse> tierOkCandidates = candidates.stream()
                .filter(c -> Boolean.TRUE.equals(c.getTierOk()))
                .collect(Collectors.toList());

        if (tierOkCandidates.isEmpty()) {
            return new CardEvaluation(card, CardBenefitStatus.CONDITION_NOT_MET,
                    reason(BenefitReasonCode.PREV_SPEND_NOT_MET, PREV_SPEND_NOT_MET_MESSAGE), null);
        }

        // 건당 최소 이용금액 게이트. 전월실적 다음, 한도 판정 앞이다 — 실적이 아예 안 되면
        // 금액을 올려도 소용없으므로 PREV_SPEND_NOT_MET이 먼저 나가야 한다.
        List<BenefitCandidateResponse> minTxOkCandidates = tierOkCandidates.stream()
                .filter(c -> meetsMinTxAmount(c, amount))
                .collect(Collectors.toList());

        if (minTxOkCandidates.isEmpty()) {
            return new CardEvaluation(card, CardBenefitStatus.CONDITION_NOT_MET,
                    reason(BenefitReasonCode.MIN_TX_AMOUNT_NOT_MET,
                            minTxNotMetMessage(tierOkCandidates)), null);
        }

        List<CandidateEvaluation> evaluations = minTxOkCandidates.stream()
                .map(c -> evaluateCandidateLimits(card.getUserCardId(), c, prevMonthSpend))
                .collect(Collectors.toList());

        List<CandidateEvaluation> availableGroup = evaluations.stream()
                .filter(e -> !e.exhausted())
                .collect(Collectors.toList());
        boolean anyAvailable = !availableGroup.isEmpty();

        CandidateEvaluation winner = pickWinner(anyAvailable ? availableGroup : evaluations, amount);

        if (anyAvailable) {
            return new CardEvaluation(card, CardBenefitStatus.AVAILABLE, null, winner);
        }

        BenefitLimitResponse exhaustedLimit = resolveExhaustedLimit(winner.exhaustedLimits());
        String message = limitExhaustedMessage(exhaustedLimit.getLimitPeriod(), exhaustedLimit.getLimitBasis());
        return new CardEvaluation(card, CardBenefitStatus.CONDITION_NOT_MET,
                reason(BenefitReasonCode.LIMIT_EXHAUSTED, message), winner);
    }

    private CardBenefitResponse toCardResponse(CardEvaluation evaluation, Map<Long, BigDecimal> expectedAmounts) {
        BenefitDetailResponse benefit = evaluation.winner() == null
                ? null
                : buildDetail(evaluation.winner().candidate(),
                        expectedAmounts.get(evaluation.card().getUserCardId()));
        return buildCard(evaluation.card(), evaluation.status(), evaluation.reason(), benefit);
    }

    /** {@code AVAILABLE}인 카드만 들어온다 — 그때 {@code winner}는 반드시 있다. */
    private PaymentBenefitResponse toPaymentBenefit(CardEvaluation evaluation, BigDecimal amount) {
        CandidateEvaluation winner = evaluation.winner();
        BenefitCandidateResponse candidate = winner.candidate();
        BenefitAmount benefitAmount = benefitAmountCalculator.calculate(amount, candidate, winner.remainingKrw());

        return PaymentBenefitResponse.builder()
                .userCardId(evaluation.card().getUserCardId())
                .benefitServiceId(candidate.getServiceId())
                .tierId(winner.tierId())
                .benefitType(candidate.getBenefitType())
                .expectedAmount(benefitAmount.getKrw())
                .nativeAmount(benefitAmount.getNativeAmount())
                .build();
    }

    /**
     * 이번 결제 1건이 건당 최소 이용금액을 넘는지. 넘지 못하면 그 혜택은 <b>아예 발생하지 않는다</b> —
     * 덜 받는 게 아니라 후보에서 빠진다.
     * <p>
     * 금액을 모르는 조회({@code amount == null})면 게이트를 건너뛴다. "조건 없음"은 DDL상
     * {@code NULL}이 아니라 {@code 0}이지만, 방어적으로 {@code null}도 조건 없음으로 본다.
     */
    private boolean meetsMinTxAmount(BenefitCandidateResponse candidate, BigDecimal amount) {
        if (amount == null || candidate.getMinTxAmount() == null) {
            return true;
        }
        return amount.compareTo(candidate.getMinTxAmount()) >= 0;
    }

    /**
     * 문턱이 <b>가장 낮은</b> 후보를 안내한다 — 사용자가 조금만 더 쓰면 되는 쪽을 알려주는 게 쓸모 있다.
     * 호출 시점에 전부 미달이므로 최솟값도 결제금액보다 크다.
     */
    private String minTxNotMetMessage(List<BenefitCandidateResponse> candidates) {
        BigDecimal lowestThreshold = candidates.stream()
                .map(BenefitCandidateResponse::getMinTxAmount)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        return formatThousands(lowestThreshold) + "원 이상 결제해야 받을 수 있는 혜택이에요.";
    }

    private CandidateEvaluation evaluateCandidateLimits(Long userCardId, BenefitCandidateResponse candidate,
                                                          BigDecimal prevMonthSpend) {
        List<BenefitLimitResponse> limits =
                benefitMapper.findLimits(candidate.getPlanGroupId(), candidate.getServiceId(), prevMonthSpend);

        List<BenefitLimitResponse> exhaustedLimits = new ArrayList<>();
        BigDecimal remainingKrw = null;
        for (BenefitLimitResponse limit : limits) {
            // PER_TRANSACTION은 판정에 쓰지 않는다 — 시드 0건이고, 건당 캡의 정본은
            // benefit_service.per_tx_limit_amount(33건)라 BenefitAmountCalculator가 ③단계에서 반영한다.
            if (limit.getLimitPeriod() == LimitPeriod.PER_TRANSACTION) {
                continue;
            }
            LocalDateTime periodStart = resolvePeriodStart(limit.getLimitPeriod());
            BenefitUsageResponse usage = benefitMapper.findUsage(userCardId, limit.getTierId(), periodStart);
            BigDecimal usedValue = resolveUsedValue(limit, usage);
            BigDecimal remainingValue = limit.getLimitValue().subtract(usedValue);

            if (remainingValue.compareTo(BigDecimal.ZERO) <= 0) {
                exhaustedLimits.add(limit);
            }
            remainingKrw = minRemaining(remainingKrw,
                    toRemainingKrw(limit.getLimitBasis(), remainingValue, candidate.getKrwPerPoint()));
        }
        return new CandidateEvaluation(candidate, !exhaustedLimits.isEmpty(), exhaustedLimits,
                remainingKrw, resolveTierId(limits));
    }

    /**
     * 한도 사용량 집계 키({@code payment_transaction.applied_tier_id})로 쓸 tier.
     * <p>
     * {@code findLimits}는 전월실적 구간이 반열린 인터벌이라 tier 하나만 뽑고, 그 tier에 붙은
     * 한도 행을 전부 돌려준다 — 어느 행을 봐도 {@code tier_id}는 같다. 한도가 아예 없으면
     * 집계할 대상도 없으므로 {@code null}이다.
     */
    private Long resolveTierId(List<BenefitLimitResponse> limits) {
        return limits.isEmpty() ? null : limits.get(0).getTierId();
    }

    /**
     * 한도 잔여를 <b>원화</b>로 환산한다. {@code null}은 "금액으로 자를 수 없음"이지 "잔여 0"이 아니다.
     * <p>
     * {@code COUNT}는 축이 달라 금액으로 환산할 수 없다 — 1회라도 남았으면 그 결제는 만액을 받고,
     * 0회면 소진이라 이미 {@code exhaustedLimits}가 잡는다.
     */
    private BigDecimal toRemainingKrw(LimitBasis basis, BigDecimal remainingValue, BigDecimal krwPerPoint) {
        return switch (basis) {
            case AMOUNT -> remainingValue;
            case POINT -> remainingValue.multiply(krwPerPoint);
            case COUNT -> remainingValue.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ZERO : null;
        };
    }

    /** 한 tier에 한도가 여러 개면 가장 빡빡한 것이 이긴다. {@code null}(무제한)은 최솟값 계산에서 빠진다. */
    private BigDecimal minRemaining(BigDecimal current, BigDecimal candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null ? candidate : current.min(candidate);
    }

    private LocalDateTime resolvePeriodStart(LimitPeriod period) {
        LocalDate today = LocalDate.now();
        return switch (period) {
            case DAY -> today.atStartOfDay();
            case MONTH -> today.withDayOfMonth(1).atStartOfDay();
            case YEAR -> today.withDayOfYear(1).atStartOfDay();
            case PER_TRANSACTION -> throw new IllegalStateException("PER_TRANSACTION has no period start");
        };
    }

    /**
     * 이미 쌓인 사용량을 {@code limit_value}와 같은 축으로 맞춘다.
     * <p>
     * {@code AMOUNT}와 {@code POINT}가 같은 식인 것은 실수가 아니다 — {@code findUsage}가 합산하는
     * {@code payment_transaction.discount_amount}는 <b>행 자신의 단위</b>이고(CASHBACK=원,
     * ACCUMULATE=포인트 개수), {@code applied_tier_id}로 묶여 있어 한 tier 안에는 같은 단위만 모인다.
     * 즉 {@code POINT} 기준 한도가 걸린 tier의 사용액은 이미 포인트 개수라 환산할 것이 없다.
     */
    private BigDecimal resolveUsedValue(BenefitLimitResponse limit, BenefitUsageResponse usage) {
        return switch (limit.getLimitBasis()) {
            case AMOUNT, POINT -> usage.getUsedAmount();
            case COUNT -> BigDecimal.valueOf(usage.getUsedCount());
        };
    }

    /**
     * 카드 한 장이 내놓을 혜택 하나를 고른다.
     * <p>
     * <b>금액을 알면 산출액이 가장 큰 후보가 이긴다.</b> {@code BENEFIT_PRIORITY}는 산출액이
     * 같을 때만 쓰는 tie-break로 내려간다 — 금액을 모르던 시절엔 이게 1차 기준이라
     * 100,000원 결제에서 "100원 정액"이 "2% 정률"을 이기는 일이 있었다.
     * <p>
     * <b>금액을 모르면 {@code BENEFIT_PRIORITY}가 그대로 1차 기준이다.</b> 같은 종류 혜택이
     * 여러 개면 값이 큰 쪽이 이 카드의 대표가 된다 — 카드 사이를 정렬하는 기준과 같아야 한다.
     */
    private CandidateEvaluation pickWinner(List<CandidateEvaluation> pool, BigDecimal amount) {
        if (amount == null) {
            return pool.stream()
                    .min(Comparator.comparing(CandidateEvaluation::candidate, BENEFIT_PRIORITY))
                    .orElseThrow();
        }
        Comparator<CandidateEvaluation> byExpectedAmountDesc = Comparator
                .comparing((CandidateEvaluation e) ->
                        benefitAmountCalculator.calculate(amount, e.candidate(), e.remainingKrw()).getKrw())
                .reversed();
        return pool.stream()
                .min(byExpectedAmountDesc.thenComparing(CandidateEvaluation::candidate, BENEFIT_PRIORITY))
                .orElseThrow();
    }

    private BenefitLimitResponse resolveExhaustedLimit(List<BenefitLimitResponse> exhaustedLimits) {
        return exhaustedLimits.stream()
                .min(Comparator.comparingInt(l -> EXHAUSTED_PRIORITY.indexOf(l.getLimitPeriod())))
                .orElseThrow();
    }

    /** {@code limitPeriod × limitBasis} 9조합의 소진 안내 문구. {@code PER_TRANSACTION}은 호출되지 않는다. */
    private String limitExhaustedMessage(LimitPeriod period, LimitBasis basis) {
        return switch (period) {
            case DAY -> switch (basis) {
                case AMOUNT -> "오늘 받을 수 있는 할인 한도를 모두 사용했어요.";
                case POINT -> "오늘 적립 가능한 포인트를 모두 적립했어요.";
                case COUNT -> "오늘 받을 수 있는 혜택 횟수를 모두 사용했어요.";
            };
            case MONTH -> switch (basis) {
                case AMOUNT -> "이번 달 받을 수 있는 할인 한도를 모두 사용했어요.";
                case POINT -> "이번 달 적립 가능한 포인트를 모두 적립했어요.";
                case COUNT -> "이번 달 받을 수 있는 혜택 횟수를 모두 사용했어요.";
            };
            case YEAR -> switch (basis) {
                case AMOUNT -> "올해 받을 수 있는 할인 한도를 모두 사용했어요.";
                case POINT -> "올해 적립 가능한 포인트를 모두 적립했어요.";
                case COUNT -> "올해 받을 수 있는 혜택 횟수를 모두 사용했어요.";
            };
            case PER_TRANSACTION -> throw new IllegalStateException("PER_TRANSACTION은 소진 판정 대상이 아니다");
        };
    }

    /**
     * {@code expectedAmount}는 {@link #resolveExpectedAmounts}가 미리 계산해 둔 값이다.
     * 금액을 모르는 조회면 {@code null}이 들어온다 — 그대로 비워 내보낸다.
     * <p>
     * 한도가 소진된 후보로도 불린다({@code LIMIT_EXHAUSTED}). 그때 잔여는 0 이하라
     * {@code expectedAmount}도 0이 된다 — 못 받는 혜택에 금액이 실리지 않는다.
     */
    private BenefitDetailResponse buildDetail(BenefitCandidateResponse candidate, BigDecimal expectedAmount) {
        return BenefitDetailResponse.builder()
                .benefitServiceId(candidate.getServiceId())
                .benefitName(candidate.getBenefitName())
                .displayText(buildDisplayText(candidate))
                .expectedAmount(expectedAmount)
                .build();
    }

    private String buildDisplayText(BenefitCandidateResponse candidate) {
        boolean fixed = candidate.getValueType() == ValueType.FIXED;
        if (candidate.getBenefitType() == BenefitType.CASHBACK) {
            return fixed
                    ? formatThousands(candidate.getValueNumber()) + "원 할인"
                    : formatPlain(candidate.getValueNumber()) + "% 할인";
        }
        return fixed
                ? formatPlain(candidate.getValueNumber()) + " " + candidate.getCurrencyName() + " 적립"
                : formatPlain(candidate.getValueNumber()) + "% " + candidate.getCurrencyName() + " 적립";
    }

    private static final DecimalFormat THOUSANDS_FORMAT =
            new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));

    private String formatThousands(BigDecimal value) {
        return THOUSANDS_FORMAT.format(value);
    }

    private String formatPlain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private BenefitReasonResponse reason(BenefitReasonCode code, String message) {
        return BenefitReasonResponse.builder().code(code).message(message).build();
    }

    private CardBenefitResponse buildCard(BenefitUserCardResponse card, CardBenefitStatus status,
                                           BenefitReasonResponse reason, BenefitDetailResponse benefit) {
        return CardBenefitResponse.builder()
                .userCardId(card.getUserCardId())
                .cardName(card.getCardName())
                .cardCompanyName(card.getCardCompanyName())
                .cardImageUrl(card.getCardImageUrl())
                .status(status)
                .reason(reason)
                .benefit(benefit)
                .build();
    }

    /**
     * 카드 목록을 정렬하고 순위를 매긴다. 정렬 기준은 3단이다 —
     * {@code status} 그룹 → <b>혜택 우열</b> → 기존 표시 순서.
     * <p>
     * 두 번째 단이 금액 유무로 갈린다. 금액을 알면 {@code expectedAmount} 내림차순이고,
     * 모르면 {@link #BENEFIT_PRIORITY}(정액→정률, 할인→적립, 값이 큰 쪽)를 쓴다.
     * 금액을 몰라도 혜택의 <b>종류와 크기</b>는 알 수 있으므로 순위를 못 매길 이유가 없다.
     * <p>
     * 세 번째 단은 코드가 아니라 {@code List.sort}가 <b>안정 정렬</b>이라는 성질이 만든다.
     * 앞의 두 기준이 같으면 {@code findUserCards}가 준 순서(= {@code display_order})가 그대로 남는다.
     * <p>
     * 정렬 대상이 응답 DTO가 아니라 {@link CardEvaluation}인 것은 필수다 —
     * {@code CardBenefitResponse}에는 {@code valueType}·{@code valueNumber}가 없어
     * 금액 없는 조회의 정렬 키를 만들 수 없다.
     */
    private List<CardBenefitResponse> sortAndRank(List<CardEvaluation> evaluations, BigDecimal amount) {
        Map<Long, BigDecimal> expectedAmounts = resolveExpectedAmounts(evaluations, amount);

        Comparator<CardEvaluation> order =
                Comparator.<CardEvaluation>comparingInt(e -> STATUS_GROUP_RANK.get(e.status()))
                        .thenComparing(amount == null
                                ? byBenefitPriority()
                                : byExpectedAmountDesc(expectedAmounts));

        List<CardEvaluation> sorted = new ArrayList<>(evaluations);
        sorted.sort(order);
        return assignRanks(sorted, amount, expectedAmounts);
    }

    /**
     * 카드별 원화 기대혜택액을 <b>한 번만</b> 계산해 둔다. 정렬 키와 응답 DTO가 같은 값을 쓰게 하려는
     * 것이므로, 금액을 모르는 조회면 계산할 것이 없어 빈 맵이다({@code expectedAmount}는 그대로 {@code null}).
     */
    private Map<Long, BigDecimal> resolveExpectedAmounts(List<CardEvaluation> evaluations, BigDecimal amount) {
        if (amount == null) {
            return Map.of();
        }
        return evaluations.stream()
                .filter(evaluation -> evaluation.winner() != null)
                .collect(Collectors.toMap(
                        evaluation -> evaluation.card().getUserCardId(),
                        evaluation -> benefitAmountCalculator.calculate(
                                amount, evaluation.winner().candidate(), evaluation.winner().remainingKrw()).getKrw()));
    }

    /** 후보가 아예 없는 카드({@code NO_BENEFIT} 등)는 비교할 혜택이 없으므로 뒤로 보낸다. */
    private static Comparator<CardEvaluation> byBenefitPriority() {
        return Comparator.comparing(DefaultBenefitService::winnerCandidate,
                Comparator.nullsLast(BENEFIT_PRIORITY));
    }

    private static Comparator<CardEvaluation> byExpectedAmountDesc(Map<Long, BigDecimal> expectedAmounts) {
        return Comparator.comparing(
                (CardEvaluation evaluation) -> expectedAmounts.get(evaluation.card().getUserCardId()),
                Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private static BenefitCandidateResponse winnerCandidate(CardEvaluation evaluation) {
        return evaluation.winner() == null ? null : evaluation.winner().candidate();
    }

    /**
     * 정렬이 끝난 목록을 응답 DTO로 바꾸면서 순위를 매긴다.
     * <b>동점은 같은 순위를 주고 다음 순위를 건너뛴다</b>(1, 1, 3).
     * <p>
     * {@code AVAILABLE}이 아닌 카드는 순위를 세지도 부여하지도 않는다 — 받지 못하는 혜택에 등수를
     * 매기면 "3위 카드"가 실제로는 못 쓰는 카드가 된다.
     * <p>
     * {@code @Setter}를 쓰지 않으므로(§4) 순위가 붙는 카드만 {@code toBuilder()}로 새로 만든다.
     */
    private List<CardBenefitResponse> assignRanks(List<CardEvaluation> sorted, BigDecimal amount,
                                                   Map<Long, BigDecimal> expectedAmounts) {
        List<CardBenefitResponse> ranked = new ArrayList<>(sorted.size());
        int position = 0;
        int currentRank = 0;
        CardEvaluation previous = null;

        for (CardEvaluation evaluation : sorted) {
            CardBenefitResponse card = toCardResponse(evaluation, expectedAmounts);
            if (evaluation.status() != CardBenefitStatus.AVAILABLE || evaluation.winner() == null) {
                ranked.add(card);
                continue;
            }
            position++;
            if (previous == null || !sameRank(previous, evaluation, amount, expectedAmounts)) {
                currentRank = position;
                previous = evaluation;
            }
            ranked.add(card.toBuilder().rank(currentRank).build());
        }
        return ranked;
    }

    /**
     * 두 카드가 같은 순위인지. 정렬 2단과 <b>같은 축으로</b> 판정해야 한다 —
     * 금액을 알면 산출액이 같을 때, 모르면 혜택의 종류와 값이 모두 같을 때다.
     * <p>
     * {@code serviceId}는 정렬을 결정짓기만 할 뿐 동점 판정에 쓰지 않는다. 화면에 같아 보이는
     * 두 혜택에 굳이 1위·2위를 갈라 줄 이유가 없다.
     */
    private boolean sameRank(CardEvaluation previous, CardEvaluation current, BigDecimal amount,
                              Map<Long, BigDecimal> expectedAmounts) {
        if (amount != null) {
            return expectedAmounts.get(previous.card().getUserCardId())
                    .compareTo(expectedAmounts.get(current.card().getUserCardId())) == 0;
        }
        BenefitCandidateResponse before = previous.winner().candidate();
        BenefitCandidateResponse after = current.winner().candidate();
        return before.getValueType() == after.getValueType()
                && before.getBenefitType() == after.getBenefitType()
                && before.getValueNumber().compareTo(after.getValueNumber()) == 0;
    }

    /** tie-break·소진 판정 계산용 내부 홀더. 응답 DTO가 아니므로 record 대신 일반 클래스로 둔다. */
    private static final class CandidateEvaluation {
        private final BenefitCandidateResponse candidate;
        private final boolean exhausted;
        private final List<BenefitLimitResponse> exhaustedLimits;
        /** 원화 환산 한도 잔여. {@code null}이면 금액으로 자를 한도가 없다. */
        private final BigDecimal remainingKrw;
        /** 한도 사용량 집계 키. {@code null}이면 이 혜택에 한도가 걸려 있지 않다. */
        private final Long tierId;

        CandidateEvaluation(BenefitCandidateResponse candidate, boolean exhausted,
                             List<BenefitLimitResponse> exhaustedLimits, BigDecimal remainingKrw, Long tierId) {
            this.candidate = candidate;
            this.exhausted = exhausted;
            this.exhaustedLimits = exhaustedLimits;
            this.remainingKrw = remainingKrw;
            this.tierId = tierId;
        }

        BigDecimal remainingKrw() {
            return remainingKrw;
        }

        BenefitCandidateResponse candidate() {
            return candidate;
        }

        boolean exhausted() {
            return exhausted;
        }

        List<BenefitLimitResponse> exhaustedLimits() {
            return exhaustedLimits;
        }

        Long tierId() {
            return tierId;
        }
    }

    /**
     * 카드 한 장의 판정 결과. 화면용({@link CardBenefitResponse})과 결제용
     * ({@link PaymentBenefitResponse}) 두 모양이 <b>같은 판정에서 갈라져 나오도록</b> 두는 중간 표현이다.
     * <p>
     * {@code winner}가 {@code null}이면 판정할 후보 자체가 없었다는 뜻이다
     * ({@code NO_BENEFIT}·{@code PREV_SPEND_NOT_MET}·{@code MIN_TX_AMOUNT_NOT_MET}).
     */
    private static final class CardEvaluation {
        private final BenefitUserCardResponse card;
        private final CardBenefitStatus status;
        private final BenefitReasonResponse reason;
        private final CandidateEvaluation winner;

        CardEvaluation(BenefitUserCardResponse card, CardBenefitStatus status,
                        BenefitReasonResponse reason, CandidateEvaluation winner) {
            this.card = card;
            this.status = status;
            this.reason = reason;
            this.winner = winner;
        }

        BenefitUserCardResponse card() {
            return card;
        }

        CardBenefitStatus status() {
            return status;
        }

        BenefitReasonResponse reason() {
            return reason;
        }

        CandidateEvaluation winner() {
            return winner;
        }
    }
}
