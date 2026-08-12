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
import com.fitwallet.domain.benefit.exception.BenefitErrorCode;
import com.fitwallet.domain.benefit.mapper.BenefitMapper;
import com.fitwallet.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    /** tie-break: valueType(FIXED→RATE) → benefitType(CASHBACK→ACCUMULATE) → serviceId 오름차순. */
    private static final Comparator<BenefitCandidateResponse> TIE_BREAK_ORDER =
            Comparator.<BenefitCandidateResponse, Integer>comparing(c -> c.getValueType() == ValueType.FIXED ? 0 : 1)
                    .thenComparing(c -> c.getBenefitType() == BenefitType.CASHBACK ? 0 : 1)
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

        BenefitStoreResponse store = benefitMapper.findStore(resolvedStoreId);
        if (store == null) {
            throw new BusinessException(BenefitErrorCode.STORE_NOT_FOUND);
        }

        List<BenefitUserCardResponse> userCards = benefitMapper.findUserCards(userId);
        if (userCards.isEmpty()) {
            return ExpectedBenefitResponse.builder()
                    .store(toStoreResponse(store))
                    .hasCard(false)
                    .cards(List.of())
                    .build();
        }

        List<Long> userCardIds = userCards.stream()
                .map(BenefitUserCardResponse::getUserCardId)
                .collect(Collectors.toList());
        Map<Long, BigDecimal> prevMonthSpends = resolvePrevMonthSpends(userCardIds);

        List<CardBenefitResponse> cards = userCards.stream()
                .map(card -> evaluateCard(card, store,
                        prevMonthSpends.getOrDefault(card.getUserCardId(), BigDecimal.ZERO), resolvedAmount))
                .collect(Collectors.toList());

        sortByStatusGroup(cards);

        return ExpectedBenefitResponse.builder()
                .store(toStoreResponse(store))
                .hasCard(true)
                .cards(cards)
                .build();
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

    private CardBenefitResponse evaluateCard(BenefitUserCardResponse card, BenefitStoreResponse store,
                                              BigDecimal prevMonthSpend, BigDecimal amount) {
        List<BenefitCandidateResponse> candidates = benefitMapper.findCandidates(
                card.getCardProductId(), prevMonthSpend, store.getBrandId(), store.getCategoryId());

        if (candidates.isEmpty()) {
            return buildCard(card, CardBenefitStatus.NO_BENEFIT,
                    reason(BenefitReasonCode.NO_BENEFIT_FOR_STORE, NO_BENEFIT_MESSAGE), null);
        }

        List<BenefitCandidateResponse> tierOkCandidates = candidates.stream()
                .filter(c -> Boolean.TRUE.equals(c.getTierOk()))
                .collect(Collectors.toList());

        if (tierOkCandidates.isEmpty()) {
            return buildCard(card, CardBenefitStatus.CONDITION_NOT_MET,
                    reason(BenefitReasonCode.PREV_SPEND_NOT_MET, PREV_SPEND_NOT_MET_MESSAGE), null);
        }

        // 건당 최소 이용금액 게이트. 전월실적 다음, 한도 판정 앞이다 — 실적이 아예 안 되면
        // 금액을 올려도 소용없으므로 PREV_SPEND_NOT_MET이 먼저 나가야 한다.
        List<BenefitCandidateResponse> minTxOkCandidates = tierOkCandidates.stream()
                .filter(c -> meetsMinTxAmount(c, amount))
                .collect(Collectors.toList());

        if (minTxOkCandidates.isEmpty()) {
            return buildCard(card, CardBenefitStatus.CONDITION_NOT_MET,
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
            return buildCard(card, CardBenefitStatus.AVAILABLE, null, buildDetail(winner, amount));
        }

        BenefitLimitResponse exhaustedLimit = resolveExhaustedLimit(winner.exhaustedLimits());
        String message = limitExhaustedMessage(exhaustedLimit.getLimitPeriod(), exhaustedLimit.getLimitBasis());
        return buildCard(card, CardBenefitStatus.CONDITION_NOT_MET,
                reason(BenefitReasonCode.LIMIT_EXHAUSTED, message), buildDetail(winner, amount));
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
            BigDecimal usedValue = resolveUsedValue(limit, usage, candidate.getKrwPerPoint());
            BigDecimal remainingValue = limit.getLimitValue().subtract(usedValue);

            if (remainingValue.compareTo(BigDecimal.ZERO) <= 0) {
                exhaustedLimits.add(limit);
            }
            remainingKrw = minRemaining(remainingKrw,
                    toRemainingKrw(limit.getLimitBasis(), remainingValue, candidate.getKrwPerPoint()));
        }
        return new CandidateEvaluation(candidate, !exhaustedLimits.isEmpty(), exhaustedLimits, remainingKrw);
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

    /** POINT는 원 사용액을 {@code krwPerPoint}로 나눠 포인트 단위로 환산한다(반올림 HALF_UP). */
    private BigDecimal resolveUsedValue(BenefitLimitResponse limit, BenefitUsageResponse usage,
                                         BigDecimal krwPerPoint) {
        return switch (limit.getLimitBasis()) {
            case AMOUNT -> usage.getUsedAmount();
            case POINT -> usage.getUsedAmount()
                    .divide(krwPerPoint, limit.getLimitValue().scale(), RoundingMode.HALF_UP);
            case COUNT -> BigDecimal.valueOf(usage.getUsedCount());
        };
    }

    /**
     * 카드 한 장이 내놓을 혜택 하나를 고른다.
     * <p>
     * <b>금액을 알면 산출액이 가장 큰 후보가 이긴다.</b> {@code TIE_BREAK_ORDER}는 산출액이
     * 같을 때만 쓰는 진짜 tie-break로 내려간다 — 금액을 모르던 시절엔 이게 1차 기준이라
     * 100,000원 결제에서 "100원 정액"이 "2% 정률"을 이기는 일이 있었다.
     */
    private CandidateEvaluation pickWinner(List<CandidateEvaluation> pool, BigDecimal amount) {
        if (amount == null) {
            return pool.stream()
                    .min(Comparator.comparing(CandidateEvaluation::candidate, TIE_BREAK_ORDER))
                    .orElseThrow();
        }
        Comparator<CandidateEvaluation> byExpectedAmountDesc = Comparator
                .comparing((CandidateEvaluation e) ->
                        benefitAmountCalculator.calculate(amount, e.candidate(), e.remainingKrw()))
                .reversed();
        return pool.stream()
                .min(byExpectedAmountDesc.thenComparing(CandidateEvaluation::candidate, TIE_BREAK_ORDER))
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
     * {@code amount}가 없으면 {@code expectedAmount}는 채우지 않는다 — 금액을 모르는 조회다.
     * <p>
     * 한도가 소진된 후보로도 불린다({@code LIMIT_EXHAUSTED}). 그때 잔여는 0 이하라
     * {@code expectedAmount}도 0이 된다 — 못 받는 혜택에 금액이 실리지 않는다.
     */
    private BenefitDetailResponse buildDetail(CandidateEvaluation evaluation, BigDecimal amount) {
        BenefitCandidateResponse candidate = evaluation.candidate();
        return BenefitDetailResponse.builder()
                .benefitServiceId(candidate.getServiceId())
                .benefitName(candidate.getBenefitName())
                .displayText(buildDisplayText(candidate))
                .expectedAmount(amount == null
                        ? null
                        : benefitAmountCalculator.calculate(amount, candidate, evaluation.remainingKrw()))
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

    private void sortByStatusGroup(List<CardBenefitResponse> cards) {
        cards.sort(Comparator.comparingInt(c -> STATUS_GROUP_RANK.get(c.getStatus())));
    }

    /** tie-break·소진 판정 계산용 내부 홀더. 응답 DTO가 아니므로 record 대신 일반 클래스로 둔다. */
    private static final class CandidateEvaluation {
        private final BenefitCandidateResponse candidate;
        private final boolean exhausted;
        private final List<BenefitLimitResponse> exhaustedLimits;
        /** 원화 환산 한도 잔여. {@code null}이면 금액으로 자를 한도가 없다. */
        private final BigDecimal remainingKrw;

        CandidateEvaluation(BenefitCandidateResponse candidate, boolean exhausted,
                             List<BenefitLimitResponse> exhaustedLimits, BigDecimal remainingKrw) {
            this.candidate = candidate;
            this.exhausted = exhausted;
            this.exhaustedLimits = exhaustedLimits;
            this.remainingKrw = remainingKrw;
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
    }
}
