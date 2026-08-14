package com.fitwallet.domain.card.service;

import com.fitwallet.domain.benefit.dto.BenefitType;
import com.fitwallet.domain.benefit.dto.LimitBasis;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitCategoryTarget;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitRule;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitUnit;
import com.fitwallet.domain.card.exception.CardErrorCode;
import com.fitwallet.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Objects;

@Component
@RequiredArgsConstructor
class CardMonthlyBenefitDisplayFormatter {

    private final CardBenefitValueLabelFormatter benefitValueLabelFormatter;

    BigDecimal displayLimitValue(
            CardMonthlyBenefitRule definition,
            CardMonthlyBenefitRule limit,
            BigDecimal value) {
        if (limit.getLimitBasis() == LimitBasis.COUNT) {
            return whole(value);
        }
        if (definition.getBenefitType() == BenefitType.ACCUMULATE) {
            if (limit.getLimitBasis() == LimitBasis.AMOUNT) {
                return whole(value.divide(validPointRate(definition.getKrwPerPoint()),
                        12, RoundingMode.DOWN));
            }
            if (limit.getLimitBasis() == LimitBasis.POINT) {
                return whole(value);
            }
        }
        if (definition.getBenefitType() == BenefitType.CASHBACK
                && limit.getLimitBasis() == LimitBasis.AMOUNT) {
            return money(value);
        }
        throw invalidData();
    }

    CardMonthlyBenefitUnit limitUnit(
            CardMonthlyBenefitRule definition, LimitBasis basis) {
        if (basis == LimitBasis.COUNT) {
            return CardMonthlyBenefitUnit.COUNT;
        }
        if (definition.getBenefitType() == BenefitType.ACCUMULATE) {
            return CardMonthlyBenefitUnit.POINT;
        }
        if (definition.getBenefitType() == BenefitType.CASHBACK && basis == LimitBasis.AMOUNT) {
            return CardMonthlyBenefitUnit.KRW;
        }
        throw invalidData();
    }

    CardMonthlyBenefitUnit valueUnit(CardMonthlyBenefitRule definition) {
        if (definition.getValueType() == com.fitwallet.domain.benefit.dto.ValueType.RATE) {
            return CardMonthlyBenefitUnit.PERCENT;
        }
        return definition.getBenefitType() == BenefitType.ACCUMULATE
                ? CardMonthlyBenefitUnit.POINT : CardMonthlyBenefitUnit.KRW;
    }

    String valueLabel(CardMonthlyBenefitRule definition) {
        return benefitValueLabelFormatter.formatValueWithAction(
                definition.getBenefitName(), definition.getBenefitType(),
                definition.getValueType(), definition.getValueNumber(),
                definition.getPointCurrencyName());
    }

    BigDecimal perTransactionLimitValue(CardMonthlyBenefitRule definition) {
        return definition.getPerTransactionLimitAmount() == null
                ? null : whole(definition.getPerTransactionLimitAmount());
    }

    String perTransactionLimitLabel(CardMonthlyBenefitRule definition) {
        BigDecimal value = perTransactionLimitValue(definition);
        if (value == null) {
            return null;
        }
        CardMonthlyBenefitUnit unit = definition.getBenefitType() == BenefitType.ACCUMULATE
                ? CardMonthlyBenefitUnit.POINT : CardMonthlyBenefitUnit.KRW;
        return "건당 최대 " + format(value) + unitSuffix(unit);
    }

    BigDecimal receivedDisplayValue(
            CardMonthlyBenefitRule definition, BigDecimal receivedKrw) {
        if (definition.getBenefitType() == BenefitType.ACCUMULATE) {
            return whole(receivedKrw.divide(
                    validPointRate(definition.getKrwPerPoint()), 12, RoundingMode.DOWN));
        }
        return money(receivedKrw);
    }

    String receivedLabel(CardMonthlyBenefitRule definition, BigDecimal receivedKrw) {
        BigDecimal value = receivedDisplayValue(definition, receivedKrw);
        String unit = definition.getBenefitType() == BenefitType.ACCUMULATE ? "P" : "원";
        String action = definition.getBenefitType() == BenefitType.ACCUMULATE ? " 적립" : " 할인";
        return "총 " + format(value) + unit + action;
    }

    String categoryDisplayName(
            CardMonthlyBenefitCategoryTarget target, String benefitName) {
        if (!Objects.equals(target.getCategoryName(), "편의점/마트")) {
            return target.getCategoryName();
        }
        if (benefitName.contains("편의점")) {
            return "편의점";
        }
        if (benefitName.contains("할인마트") || benefitName.contains("마트")) {
            return "마트";
        }
        return target.getCategoryName();
    }

    String displayQualifier(String benefitName) {
        if (benefitName == null || benefitName.isBlank()) {
            throw invalidData();
        }
        String compact = benefitName.replace(" ", "");
        if (compact.contains("기본혜택")) {
            return null;
        }
        if (compact.contains("추가혜택")) {
            return compact.contains("주말") ? "주말 추가혜택" : "추가혜택";
        }
        if (benefitName.contains("더해드림")) {
            return "더해드림";
        }
        if (benefitName.contains("챙겨드림")) {
            return "챙겨드림";
        }
        if (benefitName.startsWith("특별 ")) {
            return qualifierPrefix(benefitName);
        }
        if (benefitName.startsWith("일반 ")) {
            return qualifierPrefix(benefitName);
        }
        int separatorIndex = benefitName.indexOf(" - ");
        return separatorIndex > 0 ? benefitName.substring(0, separatorIndex).trim() : null;
    }

    String limitLabel(BigDecimal used, BigDecimal limit, CardMonthlyBenefitUnit unit) {
        return format(used) + unitSuffix(unit) + " / " + format(limit) + unitSuffix(unit);
    }

    String usedLabel(BigDecimal used, CardMonthlyBenefitUnit unit) {
        return format(used) + unitSuffix(unit);
    }

    BigDecimal money(BigDecimal value) {
        return value.setScale(0, RoundingMode.DOWN);
    }

    private String qualifierPrefix(String benefitName) {
        int dashIndex = benefitName.indexOf(" - ");
        int parenthesisIndex = benefitName.indexOf(" (");
        int endIndex = benefitName.length();
        if (dashIndex > 0) {
            endIndex = Math.min(endIndex, dashIndex);
        }
        if (parenthesisIndex > 0) {
            endIndex = Math.min(endIndex, parenthesisIndex);
        }
        return benefitName.substring(0, endIndex).trim();
    }

    private String unitSuffix(CardMonthlyBenefitUnit unit) {
        return switch (unit) {
            case PERCENT -> "%";
            case KRW -> "원";
            case POINT -> "P";
            case COUNT -> "회";
        };
    }

    private BigDecimal validPointRate(BigDecimal rate) {
        if (rate == null || rate.signum() <= 0) {
            throw invalidData();
        }
        return rate;
    }

    private BigDecimal whole(BigDecimal value) {
        return value.setScale(0, RoundingMode.DOWN);
    }

    private String format(BigDecimal value) {
        return new DecimalFormat("#,##0.##").format(value);
    }

    private BusinessException invalidData() {
        return new BusinessException(CardErrorCode.INVALID_CARD_MONTHLY_BENEFIT_DATA);
    }
}
