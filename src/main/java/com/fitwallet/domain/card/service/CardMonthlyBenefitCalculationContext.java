package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.CardMonthlyBenefitRule;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitLimitResponse;
import com.fitwallet.domain.card.exception.CardErrorCode;
import com.fitwallet.global.exception.BusinessException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class CardMonthlyBenefitCalculationContext {

    private CardMonthlyBenefitCalculationContext() {
    }

    static final class SelectedService {
        final CardMonthlyBenefitRule definition;
        final List<CardMonthlyBenefitRule> limits;

        SelectedService(CardMonthlyBenefitRule definition, List<CardMonthlyBenefitRule> limits) {
            this.definition = definition;
            this.limits = limits;
        }

        Long sharedLimitGroupId() {
            Set<Long> groupIds = new LinkedHashSet<>();
            for (CardMonthlyBenefitRule limit : limits) {
                if (!limit.isShared()) {
                    continue;
                }
                if (definition.getServicePlanGroupId() == null
                        || !Objects.equals(definition.getServicePlanGroupId(),
                        limit.getLimitPlanGroupId())) {
                    throw new BusinessException(CardErrorCode.INVALID_CARD_MONTHLY_BENEFIT_DATA);
                }
                groupIds.add(limit.getLimitPlanGroupId());
            }
            if (groupIds.size() > 1) {
                throw new BusinessException(CardErrorCode.INVALID_CARD_MONTHLY_BENEFIT_DATA);
            }
            return groupIds.isEmpty() ? null : groupIds.iterator().next();
        }
    }

    static final class UsageIndex {
        final Map<Long, UsageTotals> service = new HashMap<>();
        final Map<String, UsageTotals> category = new HashMap<>();
        final Map<String, UsageTotals> brand = new HashMap<>();
        final Map<Long, Set<Long>> planGroupServices = new HashMap<>();
    }

    static final class UsageTotals {
        static final UsageTotals ZERO = new UsageTotals();

        long count;
        BigDecimal payment = BigDecimal.ZERO;
        BigDecimal received = BigDecimal.ZERO;

        UsageTotals() {
        }

        UsageTotals(long count, BigDecimal payment, BigDecimal received) {
            this.count = count;
            this.payment = payment;
            this.received = received;
        }

        void add(UsageTotals other) {
            count += other.count;
            payment = payment.add(other.payment);
            received = received.add(other.received);
        }
    }

    static final class SummaryAmounts {
        static final SummaryAmounts ZERO = new SummaryAmounts(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        final BigDecimal total;
        final BigDecimal potential;
        final BigDecimal received;

        SummaryAmounts(BigDecimal total, BigDecimal potential, BigDecimal received) {
            this.total = total;
            this.potential = potential;
            this.received = received;
        }
    }

    static final class LimitUsageResult {
        final BigDecimal rawUsed;
        final BigDecimal rawRemaining;

        LimitUsageResult(BigDecimal rawUsed, BigDecimal rawRemaining) {
            this.rawUsed = rawUsed;
            this.rawRemaining = rawRemaining;
        }
    }

    /** 기존 항목 조립 과정에서 계산된 월 한도 결과를 호출 순서대로 모은다. */
    static final class LimitUsageSnapshotBuilder {
        private final List<LimitUsageObservation> observations = new ArrayList<>();

        void record(
                SelectedService service,
                CardMonthlyBenefitRule limit,
                LimitUsageResult result,
                CardMonthlyBenefitLimitResponse response) {
            observations.add(new LimitUsageObservation(service, limit, result, response));
        }

        LimitUsageSnapshot freeze() {
            Map<Long, LimitUsageObservation> observationsByLimitId = new LinkedHashMap<>();
            for (LimitUsageObservation observation : observations) {
                LimitUsageObservation previous = observationsByLimitId.putIfAbsent(
                        observation.limit.getLimitId(), observation);
                if (previous != null && !previous.sameResult(observation)) {
                    throw new BusinessException(CardErrorCode.INVALID_CARD_MONTHLY_BENEFIT_DATA);
                }
            }
            return new LimitUsageSnapshot(Map.copyOf(observationsByLimitId));
        }
    }

    static final class LimitUsageSnapshot {
        private final Map<Long, LimitUsageObservation> observationsByLimitId;

        private LimitUsageSnapshot(Map<Long, LimitUsageObservation> observationsByLimitId) {
            this.observationsByLimitId = observationsByLimitId;
        }

        LimitUsageObservation get(Long limitId) {
            LimitUsageObservation observation = observationsByLimitId.get(limitId);
            if (observation == null) {
                throw new BusinessException(CardErrorCode.INVALID_CARD_MONTHLY_BENEFIT_DATA);
            }
            return observation;
        }
    }

    static final class LimitUsageObservation {
        final SelectedService service;
        final CardMonthlyBenefitRule limit;
        final LimitUsageResult result;
        final CardMonthlyBenefitLimitResponse response;

        private LimitUsageObservation(
                SelectedService service,
                CardMonthlyBenefitRule limit,
                LimitUsageResult result,
                CardMonthlyBenefitLimitResponse response) {
            this.service = service;
            this.limit = limit;
            this.result = result;
            this.response = response;
        }

        private boolean sameResult(LimitUsageObservation other) {
            return Objects.equals(limit.getLimitPlanGroupId(), other.limit.getLimitPlanGroupId())
                    && limit.getLimitBasis() == other.limit.getLimitBasis()
                    && limit.getLimitValue().compareTo(other.limit.getLimitValue()) == 0
                    && result.rawUsed.compareTo(other.result.rawUsed) == 0
                    && result.rawRemaining.compareTo(other.result.rawRemaining) == 0
                    && response.getLimitValue().compareTo(other.response.getLimitValue()) == 0
                    && response.getUsedValue().compareTo(other.response.getUsedValue()) == 0
                    && response.getRemainingValue().compareTo(other.response.getRemainingValue()) == 0
                    && response.getLimitUnit() == other.response.getLimitUnit()
                    && response.getLimitStatus() == other.response.getLimitStatus()
                    && response.isShared() == other.response.isShared();
        }
    }
}
