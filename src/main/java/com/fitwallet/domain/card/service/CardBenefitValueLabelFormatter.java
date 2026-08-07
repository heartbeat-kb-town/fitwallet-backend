package com.fitwallet.domain.card.service;

import com.fitwallet.domain.benefit.dto.BenefitType;
import com.fitwallet.domain.benefit.dto.ValueType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** 이용 실적과 월 혜택 응답에서 공통으로 사용하는 혜택값 표시 문구 생성기. */
@Component
public class CardBenefitValueLabelFormatter {

    public String formatValue(
            String benefitName,
            BenefitType benefitType,
            ValueType valueType,
            BigDecimal valueNumber,
            String pointCurrencyName) {
        String label;
        if (valueType == ValueType.RATE) {
            label = formatPlain(valueNumber) + "%";
        } else if (benefitType == BenefitType.CASHBACK) {
            label = formatThousands(valueNumber) + "원";
        } else {
            label = formatThousands(valueNumber) + " " + pointCurrencyName;
        }

        if (valueType == ValueType.FIXED && benefitName.contains("주유")) {
            return "리터당 " + label;
        }
        return label;
    }

    public String formatValueWithAction(
            String benefitName,
            BenefitType benefitType,
            ValueType valueType,
            BigDecimal valueNumber,
            String pointCurrencyName) {
        String action = benefitType == BenefitType.ACCUMULATE ? " 적립" : " 할인";
        return formatValue(
                benefitName,
                benefitType,
                valueType,
                valueNumber,
                pointCurrencyName) + action;
    }

    private String formatThousands(BigDecimal value) {
        DecimalFormat format = new DecimalFormat(
                "#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(value);
    }

    private String formatPlain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
