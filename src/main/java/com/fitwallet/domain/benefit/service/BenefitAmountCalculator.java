package com.fitwallet.domain.benefit.service;

import com.fitwallet.domain.benefit.dto.BenefitType;
import com.fitwallet.domain.benefit.dto.ValueType;
import com.fitwallet.domain.benefit.dto.response.BenefitCandidateResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 혜택 하나가 이번 결제에서 만들어 내는 금액을 계산한다. 결과는 원화와 네이티브 두 축을
 * 함께 담은 {@link BenefitAmount}다 — 쓰임이 갈리는 이유는 그 클래스 주석에 있다.
 * <p>
 * Mapper·시계·트랜잭션에 의존하지 않는 순수 계산이다. 조회는 서비스가, 산수는 여기가 한다 —
 * 그래서 목 없이 단위 테스트할 수 있고, payment 도메인도 같은 계산을 재사용한다.
 * <p>
 * <b>계산은 전부 네이티브 축에서 한다.</b> 원화로 들어오는 제약({@code remainingKrw}·결제액)은
 * {@code krwPerPoint}로 나눠 네이티브로 내린 뒤 건다. 마지막에 한 번만 곱해 원화를 만들므로
 * 두 값이 언제나 같은 혜택을 가리킨다.
 * <ol>
 *   <li>원시값 — {@code RATE}는 {@code amount × valueNumber / 100}, {@code FIXED}는 {@code valueNumber}.
 *       <b>{@code ACCUMULATE}면 이 값의 단위는 포인트다</b></li>
 *   <li>건당 캡 — {@code perTxLimitAmount}가 있으면 그 값으로 자른다. 이 컬럼은 원화가 아니라
 *       행 자신의 {@code benefit_type} 단위라(ACCUMULATE=포인트, CASHBACK=원) 그대로 비교하면 된다.
 *       {@code service_id 72·73}의 "건당 1,000 마이신한포인트"가 그 예다</li>
 *   <li>결제액 상한 — 혜택은 결제액을 넘을 수 없다(1,000원 결제에 2,000원 정액 할인)</li>
 *   <li>한도 잔여 — 일·월·년 한도에 남은 금액으로 자른다. 잔여를 원화로 환산하고 여러 한도의
 *       최솟값을 고르는 일은 조회가 필요하므로 {@code DefaultBenefitService}가 하고,
 *       여기는 받은 값을 쓰기만 한다</li>
 *   <li>원화 환산 — {@code ACCUMULATE}면 {@code krwPerPoint}를 곱한다. {@code CASHBACK}은 이미 원이다</li>
 * </ol>
 * <b>2단계를 환산 뒤로 미루면 원화와 포인트를 {@code min()}으로 비교하게 된다.</b> 지금
 * {@code point_currency}가 전부 {@code krw_per_point = 1.0000}이라 값이 같아 드러나지 않을 뿐이다 —
 * 이 순서를 검증하는 테스트는 반드시 1이 아닌 {@code krwPerPoint}를 쓴다.
 * <p>
 * 절사({@link RoundingMode#DOWN})는 "이번 결제에 실제로 받는 금액"이라 카드사 관례를 따른 것이고,
 * 원화 제약을 네이티브로 내릴 때도 같은 방향이라 한도를 넘겨 주는 일이 없다.
 * {@code DefaultBenefitReportService}는 월 지출 <i>추정치</i>라 scale 2 {@code HALF_UP}을
 * 쓰는데, 성격이 다른 값이므로 일부러 맞추지 않았다.
 */
@Component
public class BenefitAmountCalculator {

    private static final BigDecimal PERCENT_DIVISOR = BigDecimal.valueOf(100);

    /**
     * @param amount       결제 예정 금액(0보다 크다 — 검증은 호출부가 끝냈다고 본다)
     * @param candidate    후보 혜택. {@code ACCUMULATE}면 {@code krwPerPoint}가 반드시 채워져 있어야 한다
     * @param remainingKrw 이 혜택에 남은 한도(원화 환산). <b>{@code null}이면 금액으로 자를 한도가 없다는 뜻</b>이다 —
     *                     한도가 아예 안 걸렸거나 {@code COUNT} 기준뿐인 경우다. 음수는 0으로 본다(소진)
     * @return 원화·네이티브 두 축의 기대혜택액. {@code min_tx_amount} 미달 판정은 들어 있지 않다(호출부가 한다)
     */
    public BenefitAmount calculate(BigDecimal amount, BenefitCandidateResponse candidate, BigDecimal remainingKrw) {
        boolean accumulate = candidate.getBenefitType() == BenefitType.ACCUMULATE;
        BigDecimal krwPerPoint = accumulate ? candidate.getKrwPerPoint() : BigDecimal.ONE;

        BigDecimal raw = candidate.getValueType() == ValueType.RATE
                ? amount.multiply(candidate.getValueNumber())
                        .divide(PERCENT_DIVISOR, 0, RoundingMode.DOWN)
                : candidate.getValueNumber();

        // 캡은 raw와 같은 단위다(ACCUMULATE면 포인트) — 환산 없이 그대로 비교한다.
        BigDecimal clipped = candidate.getPerTxLimitAmount() != null
                ? raw.min(candidate.getPerTxLimitAmount())
                : raw;

        clipped = clipped.min(toNative(amount, krwPerPoint));
        if (remainingKrw != null) {
            clipped = clipped.min(toNative(remainingKrw.max(BigDecimal.ZERO), krwPerPoint));
        }

        // 단위 절사. 캡·한도는 DECIMAL(15,2)라 그대로 두면 같은 응답 필드가
        // "4000.00"과 "100"을 섞어 내보낸다. 원(포인트) 미만 혜택은 어차피 받을 수 없다.
        BigDecimal nativeAmount = clipped.setScale(0, RoundingMode.DOWN);
        BigDecimal krw = accumulate
                ? nativeAmount.multiply(krwPerPoint).setScale(0, RoundingMode.DOWN)
                : nativeAmount;

        return new BenefitAmount(krw, nativeAmount);
    }

    /** 원화 제약을 네이티브 축으로 내린다. {@code CASHBACK}은 {@code krwPerPoint = 1}이라 값이 그대로다. */
    private BigDecimal toNative(BigDecimal krw, BigDecimal krwPerPoint) {
        return krw.divide(krwPerPoint, 0, RoundingMode.DOWN);
    }
}
