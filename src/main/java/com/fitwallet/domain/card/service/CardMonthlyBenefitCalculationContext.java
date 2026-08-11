package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.CardMonthlyBenefitRule;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
}
