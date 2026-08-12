package com.fitwallet.domain.benefit.service;

import com.fitwallet.domain.benefit.dto.BenefitType;
import com.fitwallet.domain.benefit.dto.ValueType;
import com.fitwallet.domain.benefit.dto.response.BenefitCandidateResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 산출액 계산 단위 테스트. 순수 계산이라 목이 하나도 필요 없다.
 * <p>
 * 금액 단언은 전부 {@code isEqualByComparingTo}를 쓴다 — {@code isEqualTo}는 scale까지
 * 비교해서 {@code 3500}과 {@code 3500.00}이 다르다고 본다.
 */
class BenefitAmountCalculatorTest {

    private final BenefitAmountCalculator calculator = new BenefitAmountCalculator();

    @Test
    void 정률_캐시백은_결제금액에_요율을_곱한다() {
        BenefitCandidateResponse candidate = cashbackRate(new BigDecimal("10"));

        BigDecimal result = calculator.calculate(new BigDecimal("35000"), candidate);

        assertThat(result).isEqualByComparingTo("3500");
    }

    @Test
    void 정액_캐시백은_결제금액과_무관하게_정액_그대로다() {
        BenefitCandidateResponse candidate = cashbackFixed(new BigDecimal("2000"));

        BigDecimal result = calculator.calculate(new BigDecimal("35000"), candidate);

        assertThat(result).isEqualByComparingTo("2000");
    }

    @Test
    void 적립_혜택은_포인트로_산출한_뒤_krwPerPoint로_원화_환산한다() {
        // 35,000 × 5% = 1,750 포인트, 1포인트 = 0.8원 → 1,400원
        BenefitCandidateResponse candidate = accumulateRate(new BigDecimal("5"), new BigDecimal("0.8"));

        BigDecimal result = calculator.calculate(new BigDecimal("35000"), candidate);

        assertThat(result).isEqualByComparingTo("1400");
    }

    @Test
    void 결과는_항상_원_단위다_소수점이_붙어_나가지_않는다() {
        // DB의 DECIMAL(15,2)가 그대로 새어 나가면 같은 필드가 "4000.00"과 "100"을 섞어 내보낸다.
        BenefitCandidateResponse capped = BenefitCandidateResponse.builder()
                .serviceId(1L)
                .benefitType(BenefitType.CASHBACK)
                .valueType(ValueType.RATE)
                .valueNumber(new BigDecimal("20"))
                .perTxLimitAmount(new BigDecimal("4000.00"))
                .build();

        BigDecimal result = calculator.calculate(new BigDecimal("35000"), capped);

        assertThat(result.scale()).isZero();
        assertThat(result.toPlainString()).isEqualTo("4000");
    }

    @Test
    void 산출액이_결제금액보다_크면_결제금액까지만_준다() {
        // 1,000원짜리를 사는데 2,000원 정액 할인이 걸린다 — 할인은 결제액을 넘을 수 없다
        BenefitCandidateResponse candidate = cashbackFixed(new BigDecimal("2000"));

        BigDecimal result = calculator.calculate(new BigDecimal("1000"), candidate);

        assertThat(result).isEqualByComparingTo("1000");
    }

    @Test
    void 건당_캡이_있으면_산출액을_캡까지만_준다() {
        // 35,000 × 10% = 3,500 이지만 건당 캡이 1,000
        BenefitCandidateResponse candidate = BenefitCandidateResponse.builder()
                .serviceId(1L)
                .benefitType(BenefitType.CASHBACK)
                .valueType(ValueType.RATE)
                .valueNumber(new BigDecimal("10"))
                .perTxLimitAmount(new BigDecimal("1000"))
                .build();

        BigDecimal result = calculator.calculate(new BigDecimal("35000"), candidate);

        assertThat(result).isEqualByComparingTo("1000");
    }

    private BenefitCandidateResponse accumulateRate(BigDecimal valueNumber, BigDecimal krwPerPoint) {
        return BenefitCandidateResponse.builder()
                .serviceId(1L)
                .benefitType(BenefitType.ACCUMULATE)
                .valueType(ValueType.RATE)
                .valueNumber(valueNumber)
                .krwPerPoint(krwPerPoint)
                .build();
    }

    private BenefitCandidateResponse cashbackFixed(BigDecimal valueNumber) {
        return BenefitCandidateResponse.builder()
                .serviceId(1L)
                .benefitType(BenefitType.CASHBACK)
                .valueType(ValueType.FIXED)
                .valueNumber(valueNumber)
                .build();
    }

    private BenefitCandidateResponse cashbackRate(BigDecimal valueNumber) {
        return BenefitCandidateResponse.builder()
                .serviceId(1L)
                .benefitType(BenefitType.CASHBACK)
                .valueType(ValueType.RATE)
                .valueNumber(valueNumber)
                .build();
    }
}
