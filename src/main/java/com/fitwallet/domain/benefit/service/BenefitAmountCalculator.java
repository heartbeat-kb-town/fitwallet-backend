package com.fitwallet.domain.benefit.service;

import com.fitwallet.domain.benefit.dto.BenefitType;
import com.fitwallet.domain.benefit.dto.ValueType;
import com.fitwallet.domain.benefit.dto.response.BenefitCandidateResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 혜택 하나가 이번 결제에서 만들어 내는 <b>원화 환산 기대혜택액</b>을 계산한다.
 * <p>
 * Mapper·시계·트랜잭션에 의존하지 않는 순수 계산이다. 조회는 서비스가, 산수는 여기가 한다 —
 * 그래서 목 없이 단위 테스트할 수 있고, payment 도메인도 같은 계산을 재사용할 수 있다.
 * <p>
 * <b>단계 순서가 계약이다.</b> 순서가 바뀌면 답이 달라진다.
 * <ol>
 *   <li>원시값 — {@code RATE}는 {@code amount × valueNumber / 100}, {@code FIXED}는 {@code valueNumber}.
 *       단위 절사({@link RoundingMode#DOWN}). <b>{@code ACCUMULATE}면 이 값의 단위는 포인트다</b></li>
 *   <li>건당 캡 — {@code perTxLimitAmount}가 있으면 그 값으로 자른다. <b>환산 전에 건다</b> —
 *       이 컬럼은 원화가 아니라 행 자신의 {@code benefit_type} 단위다(ACCUMULATE=포인트, CASHBACK=원).
 *       {@code service_id 72·73}의 "건당 1,000 마이신한포인트"가 그 예다</li>
 *   <li>원화 환산 — {@code ACCUMULATE}면 {@code krwPerPoint}를 곱한다. {@code CASHBACK}은 이미 원이다</li>
 *   <li>한도 잔여 — 일·월·년 한도에 남은 금액으로 자른다. 잔여를 원화로 환산하고 여러 한도의
 *       최솟값을 고르는 일은 조회가 필요하므로 {@code DefaultBenefitService}가 하고,
 *       여기는 받은 값을 쓰기만 한다</li>
 *   <li>결제액 상한 — 혜택은 결제액을 넘을 수 없다(1,000원 결제에 2,000원 정액 할인)</li>
 * </ol>
 * <b>2단계와 3단계를 뒤집으면 원화와 포인트를 {@code min()}으로 비교하게 된다.</b> 지금
 * {@code point_currency}가 전부 {@code krw_per_point = 1.0000}이라 값이 같아 드러나지 않을 뿐이다 —
 * 이 순서를 검증하는 테스트는 반드시 1이 아닌 {@code krwPerPoint}를 쓴다.
 * <p>
 * 1단계에서 {@code DOWN}을 쓰는 것은 "이번 결제에 실제로 받는 금액"이라 카드사 관례대로 절사하기
 * 때문이다. {@code DefaultBenefitReportService}는 월 지출 <i>추정치</i>라 scale 2 {@code HALF_UP}을
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
     * @return 원화 환산 기대혜택액. {@code min_tx_amount} 미달 판정은 아직 들어 있지 않다(#182)
     */
    public BigDecimal calculate(BigDecimal amount, BenefitCandidateResponse candidate, BigDecimal remainingKrw) {
        BigDecimal raw = candidate.getValueType() == ValueType.RATE
                ? amount.multiply(candidate.getValueNumber())
                        .divide(PERCENT_DIVISOR, 0, RoundingMode.DOWN)
                : candidate.getValueNumber();

        // 캡은 환산 전에 건다 — perTxLimitAmount는 raw와 같은 단위다(ACCUMULATE면 포인트).
        BigDecimal capped = candidate.getPerTxLimitAmount() != null
                ? raw.min(candidate.getPerTxLimitAmount())
                : raw;

        BigDecimal krw = candidate.getBenefitType() == BenefitType.ACCUMULATE
                ? capped.multiply(candidate.getKrwPerPoint())
                : capped;

        BigDecimal clipped = remainingKrw != null
                ? krw.min(remainingKrw.max(BigDecimal.ZERO))
                : krw;

        // 원 단위로 통일한다. 캡·한도는 DECIMAL(15,2)라 그대로 두면 같은 응답 필드가
        // "4000.00"과 "100"을 섞어 내보낸다. 원 미만 혜택은 어차피 받을 수 없다.
        return clipped.min(amount).setScale(0, RoundingMode.DOWN);
    }
}
