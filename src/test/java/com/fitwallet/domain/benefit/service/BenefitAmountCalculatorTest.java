package com.fitwallet.domain.benefit.service;

import com.fitwallet.domain.benefit.dto.BenefitType;
import com.fitwallet.domain.benefit.dto.ValueType;
import com.fitwallet.domain.benefit.dto.response.BenefitCandidateResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 산출액 계산 단위 테스트. 순수 계산이라 목이 하나도 필요 없다.
 * <p>
 * 금액 단언은 전부 {@code isEqualByComparingTo}를 쓴다 — {@code isEqualTo}는 scale까지
 * 비교해서 {@code 3500}과 {@code 3500.00}이 다르다고 본다.
 * <p>
 * <b>{@code krwPerPoint}는 1을 쓰지 않는다.</b> 시드의 {@code point_currency}가 전부
 * {@code 1.0000}이라 원화와 포인트가 같은 숫자로 나와, 축이 뒤바뀌어도 green이 뜬다.
 */
class BenefitAmountCalculatorTest {

    /** 금액으로 자를 한도가 없다는 뜻. "잔여 0"이 아니다. */
    private static final BigDecimal NO_LIMIT = null;

    private final BenefitAmountCalculator calculator = new BenefitAmountCalculator();

    @Test
    void 정률_캐시백은_결제금액에_요율을_곱한다() {
        BenefitCandidateResponse candidate = cashbackRate(new BigDecimal("10"));

        BenefitAmount result = calculator.calculate(new BigDecimal("35000"), candidate, NO_LIMIT);

        assertThat(result.getKrw()).isEqualByComparingTo("3500");
    }

    @Test
    void 정액_캐시백은_결제금액과_무관하게_정액_그대로다() {
        BenefitCandidateResponse candidate = cashbackFixed(new BigDecimal("2000"));

        BenefitAmount result = calculator.calculate(new BigDecimal("35000"), candidate, NO_LIMIT);

        assertThat(result.getKrw()).isEqualByComparingTo("2000");
    }

    @Test
    void 적립_혜택은_포인트로_산출한_뒤_krwPerPoint로_원화_환산한다() {
        // 35,000 × 5% = 1,750 포인트, 1포인트 = 0.8원 → 1,400원
        BenefitCandidateResponse candidate = accumulateRate(new BigDecimal("5"), new BigDecimal("0.8"));

        BenefitAmount result = calculator.calculate(new BigDecimal("35000"), candidate, NO_LIMIT);

        assertThat(result.getKrw()).isEqualByComparingTo("1400");
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

        BenefitAmount result = calculator.calculate(new BigDecimal("35000"), capped, NO_LIMIT);

        assertThat(result.getKrw().scale()).isZero();
        assertThat(result.getKrw().toPlainString()).isEqualTo("4000");
    }

    @Test
    void 산출액이_결제금액보다_크면_결제금액까지만_준다() {
        // 1,000원짜리를 사는데 2,000원 정액 할인이 걸린다 — 할인은 결제액을 넘을 수 없다
        BenefitCandidateResponse candidate = cashbackFixed(new BigDecimal("2000"));

        BenefitAmount result = calculator.calculate(new BigDecimal("1000"), candidate, NO_LIMIT);

        assertThat(result.getKrw()).isEqualByComparingTo("1000");
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

        BenefitAmount result = calculator.calculate(new BigDecimal("35000"), candidate, NO_LIMIT);

        assertThat(result.getKrw()).isEqualByComparingTo("1000");
    }

    @Test
    void 적립_혜택의_건당_캡은_포인트_개수라_원화_환산_전에_건다() {
        // service_id 72 모델 — RATE 10%, 건당 1,000 마이신한포인트.
        // 30,000 × 10% = 3,000P → 캡 1,000P → 1포인트 2원 → 2,000원.
        // 환산 뒤에 캡을 걸면 min(6000원, 1000)이 되어 1,000원으로 절반이 깎인다.
        BenefitCandidateResponse candidate = accumulateRateCapped(
                new BigDecimal("10"), new BigDecimal("2"), new BigDecimal("1000"));

        BenefitAmount result = calculator.calculate(new BigDecimal("30000"), candidate, NO_LIMIT);

        assertThat(result.getKrw()).isEqualByComparingTo("2000");
    }

    @Test
    void krwPerPoint가_1보다_작아도_건당_캡은_포인트_축에서_걸린다() {
        // 30,000 × 10% = 3,000P → 캡 1,000P → 1포인트 0.5원 → 500원.
        // 환산 뒤에 캡을 걸면 min(1500원, 1000)이 되어 1,000원으로 과다 지급된다.
        BenefitCandidateResponse candidate = accumulateRateCapped(
                new BigDecimal("10"), new BigDecimal("0.5"), new BigDecimal("1000"));

        BenefitAmount result = calculator.calculate(new BigDecimal("30000"), candidate, NO_LIMIT);

        assertThat(result.getKrw()).isEqualByComparingTo("500");
    }

    @Test
    void 적립_혜택의_건당_캡과_한도_잔여는_각자의_축에서_걸린다() {
        // 캡은 포인트 축(3,000P → 1,000P), 잔여는 원화 축(1,000P × 2 = 2,000원 vs 잔여 1,500원)
        BenefitCandidateResponse candidate = accumulateRateCapped(
                new BigDecimal("10"), new BigDecimal("2"), new BigDecimal("1000"));

        BenefitAmount result = calculator.calculate(new BigDecimal("30000"), candidate, new BigDecimal("1500"));

        assertThat(result.getKrw()).isEqualByComparingTo("1500");
    }

    @ParameterizedTest(name = "잔여 {0}원 → expectedAmount {1}원")
    @CsvSource({
            "3500, 3500",   // 딱 맞음
            "3499, 3499",   // 1원 모자람 — 잔여만큼만
            "1,    1",       // 극단
            "0,    0",       // 소진
            "-1,   0"        // 음수 잔여는 0으로 본다
    })
    void 한도_잔여가_있으면_잔여까지만_준다(String remaining, String expected) {
        // 35,000 × 10% = 3,500 이 산출액이고, 잔여가 그보다 적으면 잔여가 이긴다
        BenefitCandidateResponse candidate = cashbackRate(new BigDecimal("10"));

        BenefitAmount result = calculator.calculate(new BigDecimal("35000"), candidate, new BigDecimal(remaining));

        assertThat(result.getKrw()).isEqualByComparingTo(expected);
    }

    @Test
    void 잔여가_산출액보다_많으면_산출액_그대로다() {
        BenefitCandidateResponse candidate = cashbackRate(new BigDecimal("10"));

        BenefitAmount result = calculator.calculate(new BigDecimal("35000"), candidate, new BigDecimal("20000"));

        assertThat(result.getKrw()).isEqualByComparingTo("3500");
    }

    @Test
    void 적립_혜택의_잔여는_이미_원화로_환산돼_들어온다() {
        // 35,000 × 5% = 1,750 포인트 → 1,400원. 잔여 800원이면 800원까지만.
        BenefitCandidateResponse candidate = accumulateRate(new BigDecimal("5"), new BigDecimal("0.8"));

        BenefitAmount result = calculator.calculate(new BigDecimal("35000"), candidate, new BigDecimal("800"));

        assertThat(result.getKrw()).isEqualByComparingTo("800");
    }

    @Test
    void 건당_캡과_한도_잔여가_같이_걸리면_더_빡빡한_쪽이_이긴다() {
        // 산출액 3,500 · 건당 캡 1,000 · 잔여 2,000 → 1,000
        BenefitCandidateResponse candidate = BenefitCandidateResponse.builder()
                .serviceId(1L)
                .benefitType(BenefitType.CASHBACK)
                .valueType(ValueType.RATE)
                .valueNumber(new BigDecimal("10"))
                .perTxLimitAmount(new BigDecimal("1000"))
                .build();

        BenefitAmount result = calculator.calculate(new BigDecimal("35000"), candidate, new BigDecimal("2000"));

        assertThat(result.getKrw()).isEqualByComparingTo("1000");
    }

    // ---------- 네이티브 축 ----------

    @Test
    void 캐시백은_원화와_네이티브가_같은_값이다() {
        BenefitCandidateResponse candidate = cashbackRate(new BigDecimal("10"));

        BenefitAmount result = calculator.calculate(new BigDecimal("35000"), candidate, NO_LIMIT);

        assertThat(result.getKrw()).isEqualByComparingTo("3500");
        assertThat(result.getNativeAmount()).isEqualByComparingTo("3500");
    }

    @Test
    void 적립은_네이티브가_포인트_개수고_원화는_환산값이다() {
        // 35,000 × 5% = 1,750 포인트, 1포인트 0.8원 → 1,400원
        BenefitCandidateResponse candidate = accumulateRate(new BigDecimal("5"), new BigDecimal("0.8"));

        BenefitAmount result = calculator.calculate(new BigDecimal("35000"), candidate, NO_LIMIT);

        assertThat(result.getNativeAmount()).isEqualByComparingTo("1750");
        assertThat(result.getKrw()).isEqualByComparingTo("1400");
    }

    @Test
    void 포인트는_원화에서_역산할_수_없어_따로_들고_있어야_한다() {
        // 1,751P × 0.8원 = 1,400.8원 → 절사하면 1,400원.
        // 1,400 ÷ 0.8 = 1,750P라 되돌리면 1포인트가 사라진다.
        BenefitCandidateResponse candidate = BenefitCandidateResponse.builder()
                .serviceId(1L)
                .benefitType(BenefitType.ACCUMULATE)
                .valueType(ValueType.FIXED)
                .valueNumber(new BigDecimal("1751"))
                .krwPerPoint(new BigDecimal("0.8"))
                .build();

        BenefitAmount result = calculator.calculate(new BigDecimal("35000"), candidate, NO_LIMIT);

        assertThat(result.getKrw()).isEqualByComparingTo("1400");
        assertThat(result.getNativeAmount()).isEqualByComparingTo("1751");
    }

    @Test
    void 원화로_들어온_한도_잔여는_포인트도_함께_자른다() {
        // 3,000P 산출 → 잔여 1,500원 ÷ 2원 = 750P → 750P(1,500원).
        // 포인트를 안 자르면 discount_amount에 3,000P가 적재돼 한도를 넘어선 사용량이 쌓인다.
        BenefitCandidateResponse candidate = accumulateRate(new BigDecimal("10"), new BigDecimal("2"));

        BenefitAmount result = calculator.calculate(new BigDecimal("30000"), candidate, new BigDecimal("1500"));

        assertThat(result.getNativeAmount()).isEqualByComparingTo("750");
        assertThat(result.getKrw()).isEqualByComparingTo("1500");
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

    /** {@code perTxLimitAmount}는 ACCUMULATE 행에서 원이 아니라 <b>포인트 개수</b>다. */
    private BenefitCandidateResponse accumulateRateCapped(BigDecimal valueNumber, BigDecimal krwPerPoint,
                                                          BigDecimal perTxLimitPoints) {
        return BenefitCandidateResponse.builder()
                .serviceId(72L)
                .benefitType(BenefitType.ACCUMULATE)
                .valueType(ValueType.RATE)
                .valueNumber(valueNumber)
                .krwPerPoint(krwPerPoint)
                .perTxLimitAmount(perTxLimitPoints)
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
