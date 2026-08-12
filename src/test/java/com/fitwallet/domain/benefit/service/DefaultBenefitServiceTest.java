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
import com.fitwallet.domain.benefit.dto.response.CardBenefitResponse;
import com.fitwallet.domain.benefit.dto.response.ExpectedBenefitResponse;
import com.fitwallet.domain.benefit.exception.BenefitErrorCode;
import com.fitwallet.domain.benefit.mapper.BenefitMapper;
import com.fitwallet.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * Service 단위 테스트. Mapper를 목킹하므로 DB가 필요 없다.
 * <p>
 * {@code @InjectMocks}는 구체 클래스가 있어야 인스턴스를 만들 수 있어
 * 필드 타입을 인터페이스({@code BenefitService})가 아니라 구현체로 둔다.
 */
@ExtendWith(MockitoExtension.class)
class DefaultBenefitServiceTest {

    private static final Long USER_ID = 1L;
    private static final String STORE_ID = "1";
    private static final Long STORE_ID_LONG = 1L;
    private static final Long CATEGORY_ID = 4L;
    private static final Long BRAND_ID = 11L;
    private static final Long USER_CARD_ID = 10L;
    private static final Long CARD_PRODUCT_ID = 47L;
    private static final BigDecimal PREV_MONTH_SPEND = new BigDecimal("350000");

    @Mock
    private BenefitMapper benefitMapper;

    /**
     * 목이 아니라 실물이다. 순수 계산이라 스텁할 게 없고, 목으로 두면 산출액 비교가
     * 실제 계산이 아니라 스텁 값을 검증하게 된다.
     */
    @Spy
    private BenefitAmountCalculator benefitAmountCalculator = new BenefitAmountCalculator();

    @InjectMocks
    private DefaultBenefitService benefitService;

    // ---------- storeId 파싱 ----------

    @Test
    void storeId가_null이면_STORE_ID_REQUIRED_예외를_던진다() {
        assertThatThrownBy(() -> benefitService.findExpectedBenefits(USER_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(BenefitErrorCode.STORE_ID_REQUIRED);
    }

    @Test
    void storeId가_빈_문자열이면_STORE_ID_REQUIRED_예외를_던진다() {
        assertThatThrownBy(() -> benefitService.findExpectedBenefits(USER_ID, "", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(BenefitErrorCode.STORE_ID_REQUIRED);
    }

    @Test
    void storeId가_공백이면_STORE_ID_REQUIRED_예외를_던진다() {
        assertThatThrownBy(() -> benefitService.findExpectedBenefits(USER_ID, "   ", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(BenefitErrorCode.STORE_ID_REQUIRED);
    }

    @Test
    void storeId가_숫자가_아니면_STORE_ID_REQUIRED_예외를_던진다() {
        assertThatThrownBy(() -> benefitService.findExpectedBenefits(USER_ID, "abc", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(BenefitErrorCode.STORE_ID_REQUIRED);
    }

    // ---------- 가맹점 / 보유 카드 ----------

    @Test
    void 존재하지_않는_가맹점이면_STORE_NOT_FOUND_예외를_던진다() {
        given(benefitMapper.findStore(STORE_ID_LONG)).willReturn(null);

        assertThatThrownBy(() -> benefitService.findExpectedBenefits(USER_ID, STORE_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(BenefitErrorCode.STORE_NOT_FOUND);
    }

    @Test
    void 보유_카드가_없으면_hasCard_false와_빈_목록을_반환한다() {
        givenStore(CATEGORY_ID, BRAND_ID);
        given(benefitMapper.findUserCards(USER_ID)).willReturn(List.of());

        ExpectedBenefitResponse response = benefitService.findExpectedBenefits(USER_ID, STORE_ID, null);

        assertThat(response.getHasCard()).isFalse();
        assertThat(response.getCards()).isEmpty();
        then(benefitMapper).should(never()).findPrevMonthSpends(any());
    }

    // ---------- 카드별 판정 ----------

    @Test
    void 스코프에_맞는_혜택이_없으면_NO_BENEFIT으로_판정한다() {
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of());

        CardBenefitResponse result = singleCardResult();

        assertThat(result.getStatus()).isEqualTo(CardBenefitStatus.NO_BENEFIT);
        assertThat(result.getReason().getCode()).isEqualTo(BenefitReasonCode.NO_BENEFIT_FOR_STORE);
        assertThat(result.getBenefit()).isNull();
    }

    @Test
    void 모든_후보가_tierOk_false면_PREV_SPEND_NOT_MET으로_판정하고_한도조회는_호출하지_않는다() {
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        BenefitCandidateResponse candidate = candidate(133L, null, BenefitType.CASHBACK, ValueType.FIXED,
                new BigDecimal("2000"), null, null, false);
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(candidate));

        CardBenefitResponse result = singleCardResult();

        assertThat(result.getStatus()).isEqualTo(CardBenefitStatus.CONDITION_NOT_MET);
        assertThat(result.getReason().getCode()).isEqualTo(BenefitReasonCode.PREV_SPEND_NOT_MET);
        assertThat(result.getBenefit()).isNull();
        then(benefitMapper).should(never()).findLimits(any(), any(), any());
    }

    @Test
    void 한도에_여유가_있으면_AVAILABLE로_판정한다() {
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        BenefitCandidateResponse candidate = candidate(133L, null, BenefitType.CASHBACK, ValueType.FIXED,
                new BigDecimal("2000"), null, null, true);
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(candidate));
        given(benefitMapper.findLimits(null, 133L, PREV_MONTH_SPEND))
                .willReturn(List.of(limit(21L, LimitBasis.AMOUNT, LimitPeriod.MONTH, new BigDecimal("5000.00"))));
        given(benefitMapper.findUsage(eq(USER_CARD_ID), eq(21L), any()))
                .willReturn(usage(new BigDecimal("1000.00"), 0L));

        CardBenefitResponse result = singleCardResult();

        assertThat(result.getStatus()).isEqualTo(CardBenefitStatus.AVAILABLE);
        assertThat(result.getReason()).isNull();
        assertThat(result.getBenefit().getBenefitServiceId()).isEqualTo(133L);
    }

    @Test
    void 한도가_소진되면_CONDITION_NOT_MET과_LIMIT_EXHAUSTED로_판정한다() {
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        BenefitCandidateResponse candidate = candidate(133L, null, BenefitType.CASHBACK, ValueType.FIXED,
                new BigDecimal("2000"), null, null, true);
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(candidate));
        given(benefitMapper.findLimits(null, 133L, PREV_MONTH_SPEND))
                .willReturn(List.of(limit(21L, LimitBasis.AMOUNT, LimitPeriod.MONTH, new BigDecimal("5000.00"))));
        given(benefitMapper.findUsage(eq(USER_CARD_ID), eq(21L), any()))
                .willReturn(usage(new BigDecimal("5000.00"), 0L));

        CardBenefitResponse result = singleCardResult();

        assertThat(result.getStatus()).isEqualTo(CardBenefitStatus.CONDITION_NOT_MET);
        assertThat(result.getReason().getCode()).isEqualTo(BenefitReasonCode.LIMIT_EXHAUSTED);
        assertThat(result.getReason().getMessage()).isEqualTo("이번 달 받을 수 있는 할인 한도를 모두 사용했어요.");
        assertThat(result.getBenefit().getBenefitServiceId()).isEqualTo(133L);
    }

    @Test
    void PER_TRANSACTION_한도는_소진_판정에서_제외되고_사용량_조회도_호출되지_않는다() {
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        BenefitCandidateResponse candidate = candidate(133L, null, BenefitType.CASHBACK, ValueType.FIXED,
                new BigDecimal("2000"), null, null, true);
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(candidate));
        given(benefitMapper.findLimits(null, 133L, PREV_MONTH_SPEND))
                .willReturn(List.of(limit(30L, LimitBasis.AMOUNT, LimitPeriod.PER_TRANSACTION, BigDecimal.ZERO)));

        CardBenefitResponse result = singleCardResult();

        assertThat(result.getStatus()).isEqualTo(CardBenefitStatus.AVAILABLE);
        then(benefitMapper).should(never()).findUsage(any(), any(), any());
    }

    @Test
    void 소진된_한도가_여러_개면_DAY_MONTH_YEAR_순으로_하나만_메시지에_반영한다() {
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        BenefitCandidateResponse candidate = candidate(133L, null, BenefitType.CASHBACK, ValueType.FIXED,
                new BigDecimal("2000"), null, null, true);
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(candidate));
        given(benefitMapper.findLimits(null, 133L, PREV_MONTH_SPEND)).willReturn(List.of(
                limit(21L, LimitBasis.AMOUNT, LimitPeriod.MONTH, new BigDecimal("1000.00")),
                limit(22L, LimitBasis.AMOUNT, LimitPeriod.DAY, new BigDecimal("500.00"))));
        given(benefitMapper.findUsage(eq(USER_CARD_ID), eq(21L), any()))
                .willReturn(usage(new BigDecimal("1000.00"), 0L));
        given(benefitMapper.findUsage(eq(USER_CARD_ID), eq(22L), any()))
                .willReturn(usage(new BigDecimal("500.00"), 0L));

        CardBenefitResponse result = singleCardResult();

        assertThat(result.getReason().getMessage()).isEqualTo("오늘 받을 수 있는 할인 한도를 모두 사용했어요.");
    }

    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource(delimiterString = "|", textBlock = """
            DAY   | AMOUNT | 오늘 받을 수 있는 할인 한도를 모두 사용했어요.
            DAY   | POINT  | 오늘 적립 가능한 포인트를 모두 적립했어요.
            DAY   | COUNT  | 오늘 받을 수 있는 혜택 횟수를 모두 사용했어요.
            MONTH | AMOUNT | 이번 달 받을 수 있는 할인 한도를 모두 사용했어요.
            MONTH | POINT  | 이번 달 적립 가능한 포인트를 모두 적립했어요.
            MONTH | COUNT  | 이번 달 받을 수 있는 혜택 횟수를 모두 사용했어요.
            YEAR  | AMOUNT | 올해 받을 수 있는 할인 한도를 모두 사용했어요.
            YEAR  | POINT  | 올해 적립 가능한 포인트를 모두 적립했어요.
            YEAR  | COUNT  | 올해 받을 수 있는 혜택 횟수를 모두 사용했어요.
            """)
    void 소진된_한도의_기간과_기준_조합에_따라_메시지가_다르다(LimitPeriod period, LimitBasis basis, String expectedMessage) {
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        BenefitCandidateResponse candidate = candidate(133L, null, BenefitType.ACCUMULATE, ValueType.FIXED,
                new BigDecimal("80"), "마이신한포인트", BigDecimal.ONE, true);
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(candidate));
        given(benefitMapper.findLimits(null, 133L, PREV_MONTH_SPEND))
                .willReturn(List.of(limit(21L, basis, period, BigDecimal.TEN)));
        given(benefitMapper.findUsage(eq(USER_CARD_ID), eq(21L), any()))
                .willReturn(usage(BigDecimal.TEN, 10L));

        CardBenefitResponse result = singleCardResult();

        assertThat(result.getReason().getMessage()).isEqualTo(expectedMessage);
    }

    @Test
    void POINT_한도는_원화_사용액을_krwPerPoint로_나눠_환산하고_여유가_있으면_AVAILABLE이다() {
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        BenefitCandidateResponse candidate = candidate(72L, null, BenefitType.ACCUMULATE, ValueType.FIXED,
                new BigDecimal("80"), "마이신한포인트", new BigDecimal("500"), true);
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(candidate));
        given(benefitMapper.findLimits(null, 72L, PREV_MONTH_SPEND))
                .willReturn(List.of(limit(69L, LimitBasis.POINT, LimitPeriod.MONTH, BigDecimal.TEN)));
        given(benefitMapper.findUsage(eq(USER_CARD_ID), eq(69L), any()))
                .willReturn(usage(new BigDecimal("4500"), 0L)); // 4500/500=9 < 10

        CardBenefitResponse result = singleCardResult();

        assertThat(result.getStatus()).isEqualTo(CardBenefitStatus.AVAILABLE);
    }

    @Test
    void POINT_한도가_정확히_소진되면_LIMIT_EXHAUSTED로_판정한다() {
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        BenefitCandidateResponse candidate = candidate(72L, null, BenefitType.ACCUMULATE, ValueType.FIXED,
                new BigDecimal("80"), "마이신한포인트", new BigDecimal("500"), true);
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(candidate));
        given(benefitMapper.findLimits(null, 72L, PREV_MONTH_SPEND))
                .willReturn(List.of(limit(69L, LimitBasis.POINT, LimitPeriod.MONTH, BigDecimal.TEN)));
        given(benefitMapper.findUsage(eq(USER_CARD_ID), eq(69L), any()))
                .willReturn(usage(new BigDecimal("5000"), 0L)); // 5000/500=10 == 10

        CardBenefitResponse result = singleCardResult();

        assertThat(result.getStatus()).isEqualTo(CardBenefitStatus.CONDITION_NOT_MET);
        assertThat(result.getReason().getCode()).isEqualTo(BenefitReasonCode.LIMIT_EXHAUSTED);
    }

    @Test
    void tierOk가_true인_후보가_하나라도_있으면_PREV_SPEND_NOT_MET이_나오지_않는다() {
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        BenefitCandidateResponse notMet = candidate(133L, null, BenefitType.CASHBACK, ValueType.FIXED,
                new BigDecimal("2000"), null, null, false);
        BenefitCandidateResponse met = candidate(134L, null, BenefitType.CASHBACK, ValueType.FIXED,
                new BigDecimal("3000"), null, null, true);
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(notMet, met));
        given(benefitMapper.findLimits(null, 134L, PREV_MONTH_SPEND)).willReturn(List.of());

        CardBenefitResponse result = singleCardResult();

        assertThat(result.getStatus()).isEqualTo(CardBenefitStatus.AVAILABLE);
        assertThat(result.getReason()).isNull();
        then(benefitMapper).should(never()).findLimits(any(), eq(133L), any());
    }

    // ---------- tie-break ----------

    @Test
    void tie_break에서_AVAILABLE_후보가_LIMIT_EXHAUSTED_후보보다_우선한다() {
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        // 소진된 쪽이 tie-break 속성상(FIXED/CASHBACK/낮은 id) 원래는 더 유리하다 — 그래도 AVAILABLE이 이겨야 한다.
        BenefitCandidateResponse exhausted = candidate(100L, null, BenefitType.CASHBACK, ValueType.FIXED,
                new BigDecimal("2000"), null, null, true);
        BenefitCandidateResponse available = candidate(200L, null, BenefitType.ACCUMULATE, ValueType.RATE,
                new BigDecimal("5"), "마이신한포인트", BigDecimal.ONE, true);
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(exhausted, available));
        given(benefitMapper.findLimits(null, 100L, PREV_MONTH_SPEND))
                .willReturn(List.of(limit(1L, LimitBasis.AMOUNT, LimitPeriod.MONTH, new BigDecimal("1000.00"))));
        given(benefitMapper.findUsage(eq(USER_CARD_ID), eq(1L), any()))
                .willReturn(usage(new BigDecimal("1000.00"), 0L));
        given(benefitMapper.findLimits(null, 200L, PREV_MONTH_SPEND))
                .willReturn(List.of(limit(2L, LimitBasis.AMOUNT, LimitPeriod.MONTH, new BigDecimal("1000.00"))));
        given(benefitMapper.findUsage(eq(USER_CARD_ID), eq(2L), any()))
                .willReturn(usage(BigDecimal.ZERO, 0L));

        CardBenefitResponse result = singleCardResult();

        assertThat(result.getStatus()).isEqualTo(CardBenefitStatus.AVAILABLE);
        assertThat(result.getBenefit().getBenefitServiceId()).isEqualTo(200L);
    }

    @Test
    void tie_break에서_valueType_FIXED가_RATE보다_우선한다() {
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        BenefitCandidateResponse fixed = candidate(200L, null, BenefitType.CASHBACK, ValueType.FIXED,
                new BigDecimal("2000"), null, null, true);
        BenefitCandidateResponse rate = candidate(100L, null, BenefitType.CASHBACK, ValueType.RATE,
                new BigDecimal("5"), null, null, true);
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(fixed, rate));
        given(benefitMapper.findLimits(null, 200L, PREV_MONTH_SPEND)).willReturn(List.of());
        given(benefitMapper.findLimits(null, 100L, PREV_MONTH_SPEND)).willReturn(List.of());

        CardBenefitResponse result = singleCardResult();

        assertThat(result.getBenefit().getBenefitServiceId()).isEqualTo(200L);
    }

    @Test
    void tie_break에서_benefitType_CASHBACK이_ACCUMULATE보다_우선한다() {
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        BenefitCandidateResponse cashback = candidate(200L, null, BenefitType.CASHBACK, ValueType.FIXED,
                new BigDecimal("2000"), null, null, true);
        BenefitCandidateResponse accumulate = candidate(100L, null, BenefitType.ACCUMULATE, ValueType.FIXED,
                new BigDecimal("80"), "마이신한포인트", BigDecimal.ONE, true);
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(cashback, accumulate));
        given(benefitMapper.findLimits(null, 200L, PREV_MONTH_SPEND)).willReturn(List.of());
        given(benefitMapper.findLimits(null, 100L, PREV_MONTH_SPEND)).willReturn(List.of());

        CardBenefitResponse result = singleCardResult();

        assertThat(result.getBenefit().getBenefitServiceId()).isEqualTo(200L);
    }

    @Test
    void tie_break에서_valueType과_benefitType이_같으면_serviceId_오름차순으로_고른다() {
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        BenefitCandidateResponse higherId = candidate(200L, null, BenefitType.CASHBACK, ValueType.FIXED,
                new BigDecimal("2000"), null, null, true);
        BenefitCandidateResponse lowerId = candidate(100L, null, BenefitType.CASHBACK, ValueType.FIXED,
                new BigDecimal("3000"), null, null, true);
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(higherId, lowerId));
        given(benefitMapper.findLimits(null, 200L, PREV_MONTH_SPEND)).willReturn(List.of());
        given(benefitMapper.findLimits(null, 100L, PREV_MONTH_SPEND)).willReturn(List.of());

        CardBenefitResponse result = singleCardResult();

        assertThat(result.getBenefit().getBenefitServiceId()).isEqualTo(100L);
    }

    // ---------- displayText ----------

    @Test
    void displayText는_CASHBACK_FIXED_조합에서_천단위_할인_문구를_만든다() {
        CardBenefitResponse result = singleAvailableResultWith(BenefitType.CASHBACK, ValueType.FIXED,
                new BigDecimal("2000"), null, null);

        assertThat(result.getBenefit().getDisplayText()).isEqualTo("2,000원 할인");
    }

    @Test
    void displayText는_CASHBACK_RATE_조합에서_퍼센트_할인_문구를_만들고_소수점_끝자리_0을_뗀다() {
        CardBenefitResponse result = singleAvailableResultWith(BenefitType.CASHBACK, ValueType.RATE,
                new BigDecimal("5.00"), null, null);

        assertThat(result.getBenefit().getDisplayText()).isEqualTo("5% 할인");
    }

    @Test
    void displayText는_ACCUMULATE_FIXED_조합에서_포인트_적립_문구를_만든다() {
        CardBenefitResponse result = singleAvailableResultWith(BenefitType.ACCUMULATE, ValueType.FIXED,
                new BigDecimal("80"), "마이신한포인트", BigDecimal.ONE);

        assertThat(result.getBenefit().getDisplayText()).isEqualTo("80 마이신한포인트 적립");
    }

    @Test
    void displayText는_ACCUMULATE_RATE_조합에서_퍼센트_적립_문구를_만들고_소수점_끝자리_0을_뗀다() {
        CardBenefitResponse result = singleAvailableResultWith(BenefitType.ACCUMULATE, ValueType.RATE,
                new BigDecimal("2.10"), "마이신한포인트", BigDecimal.ONE);

        assertThat(result.getBenefit().getDisplayText()).isEqualTo("2.1% 마이신한포인트 적립");
    }

    // ---------- 카드 목록 정렬 / 호출 횟수 ----------

    @Test
    void 카드_목록은_상태그룹_순으로_정렬되고_그룹_안에서는_원래_순서를_유지한다() {
        givenStore(CATEGORY_ID, BRAND_ID);
        BenefitUserCardResponse card1 = card(1L, 10L, 1); // NO_BENEFIT
        BenefitUserCardResponse card2 = card(2L, 20L, 2); // AVAILABLE
        BenefitUserCardResponse card3 = card(3L, 30L, 3); // CONDITION_NOT_MET
        BenefitUserCardResponse card4 = card(4L, 40L, 4); // NO_BENEFIT
        given(benefitMapper.findUserCards(USER_ID)).willReturn(List.of(card1, card2, card3, card4));
        given(benefitMapper.findPrevMonthSpends(List.of(1L, 2L, 3L, 4L))).willReturn(List.of());

        given(benefitMapper.findCandidates(10L, BigDecimal.ZERO, BRAND_ID, CATEGORY_ID)).willReturn(List.of());
        BenefitCandidateResponse availableCandidate = candidate(200L, null, BenefitType.CASHBACK, ValueType.FIXED,
                new BigDecimal("1000"), null, null, true);
        given(benefitMapper.findCandidates(20L, BigDecimal.ZERO, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(availableCandidate));
        given(benefitMapper.findLimits(null, 200L, BigDecimal.ZERO)).willReturn(List.of());
        BenefitCandidateResponse notMetCandidate = candidate(300L, null, BenefitType.CASHBACK, ValueType.FIXED,
                new BigDecimal("1000"), null, null, false);
        given(benefitMapper.findCandidates(30L, BigDecimal.ZERO, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(notMetCandidate));
        given(benefitMapper.findCandidates(40L, BigDecimal.ZERO, BRAND_ID, CATEGORY_ID)).willReturn(List.of());

        ExpectedBenefitResponse response = benefitService.findExpectedBenefits(USER_ID, STORE_ID, null);

        assertThat(response.getCards()).extracting(CardBenefitResponse::getUserCardId)
                .containsExactly(2L, 3L, 1L, 4L);
    }

    @Test
    void findPrevMonthSpends는_카드_수와_무관하게_한_번만_호출된다() {
        givenStore(CATEGORY_ID, BRAND_ID);
        BenefitUserCardResponse card1 = card(1L, 10L, 1);
        BenefitUserCardResponse card2 = card(2L, 20L, 2);
        given(benefitMapper.findUserCards(USER_ID)).willReturn(List.of(card1, card2));
        given(benefitMapper.findPrevMonthSpends(List.of(1L, 2L))).willReturn(List.of());
        given(benefitMapper.findCandidates(any(), any(), any(), any())).willReturn(List.of());

        benefitService.findExpectedBenefits(USER_ID, STORE_ID, null);

        then(benefitMapper).should(times(1)).findPrevMonthSpends(any());
    }

    // ---------- 결제 예정 금액 ----------

    @ParameterizedTest(name = "amount={0} → AMOUNT_INVALID")
    @CsvSource({"abc", "'1,000'", "0", "-1", "35000.5.5"})
    void amount가_숫자가_아니거나_0이하면_AMOUNT_INVALID_예외를_던진다(String amount) {
        assertThatThrownBy(() -> benefitService.findExpectedBenefits(USER_ID, STORE_ID, amount))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(BenefitErrorCode.AMOUNT_INVALID);
    }

    @Test
    void amount가_공백이면_에러가_아니라_금액을_모르는_조회로_본다() {
        givenStore(CATEGORY_ID, BRAND_ID);
        given(benefitMapper.findUserCards(USER_ID)).willReturn(List.of());

        ExpectedBenefitResponse response = benefitService.findExpectedBenefits(USER_ID, STORE_ID, "   ");

        assertThat(response.getHasCard()).isFalse();
    }

    @Test
    void 결제금액이_주어지면_정액이_아니라_산출액이_큰_후보를_고른다() {
        // 시드 card_product 9(주유)에 실재하는 조합: 정액 100원 적립 vs 정률 2%
        // 100,000원 결제면 정률이 2,000원으로 20배 유리한데, 기존 tie-break는 정액을 먼저 집었다.
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        BenefitCandidateResponse fixed = candidate(35L, null, BenefitType.CASHBACK, ValueType.FIXED,
                new BigDecimal("100"), null, null, true);
        BenefitCandidateResponse rate = candidate(43L, null, BenefitType.CASHBACK, ValueType.RATE,
                new BigDecimal("2"), null, null, true);
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(fixed, rate));
        given(benefitMapper.findLimits(null, 35L, PREV_MONTH_SPEND)).willReturn(List.of());
        given(benefitMapper.findLimits(null, 43L, PREV_MONTH_SPEND)).willReturn(List.of());

        CardBenefitResponse result = singleCardResultFor("100000");

        assertThat(result.getBenefit().getBenefitServiceId()).isEqualTo(43L);
    }

    @Test
    void 적립_혜택의_expectedAmount는_포인트가_아니라_원화로_환산된_값이다() {
        // 35,000 × 5% = 1,750 포인트, 1포인트 = 0.8원 → 1,400원
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        BenefitCandidateResponse candidate = candidate(133L, null, BenefitType.ACCUMULATE, ValueType.RATE,
                new BigDecimal("5"), "마이신한포인트", new BigDecimal("0.8"), true);
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(candidate));
        given(benefitMapper.findLimits(null, 133L, PREV_MONTH_SPEND)).willReturn(List.of());

        CardBenefitResponse result = singleCardResultFor("35000");

        assertThat(result.getBenefit().getExpectedAmount()).isEqualByComparingTo("1400");
    }

    @Test
    void amount를_안_보내면_expectedAmount는_null이다() {
        CardBenefitResponse result = singleAvailableResultWith(BenefitType.CASHBACK, ValueType.RATE,
                new BigDecimal("10"), null, null);

        assertThat(result.getBenefit().getExpectedAmount()).isNull();
    }

    @Test
    void 금액을_모르는_2인자_오버로드는_3인자에_null을_넘긴_것과_같다() {
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        BenefitCandidateResponse candidate = candidate(133L, null, BenefitType.CASHBACK, ValueType.RATE,
                new BigDecimal("10"), null, null, true);
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(candidate));
        given(benefitMapper.findLimits(null, 133L, PREV_MONTH_SPEND)).willReturn(List.of());

        CardBenefitResponse result = benefitService.findExpectedBenefits(USER_ID, STORE_ID).getCards().get(0);

        assertThat(result.getStatus()).isEqualTo(CardBenefitStatus.AVAILABLE);
        assertThat(result.getBenefit().getExpectedAmount()).isNull();
    }

    // ---------- 한도 잔여 클리핑 ----------

    @ParameterizedTest(name = "월 한도 20,000 중 {0} 사용 → expectedAmount {1}")
    @CsvSource({
            "16500, 3500",   // 잔여 3,500 — 산출액과 딱 맞음
            "16501, 3499",   // 잔여 3,499 — 잔여만큼만
            "19999, 1"       // 잔여 1원
    })
    void 한도가_일부만_남으면_잔여까지만_안내한다(String usedAmount, String expected) {
        givenClippingFixture(new BigDecimal("20000.00"), new BigDecimal(usedAmount));

        CardBenefitResponse result = singleCardResultFor("35000");

        assertThat(result.getStatus()).isEqualTo(CardBenefitStatus.AVAILABLE);
        assertThat(result.getBenefit().getExpectedAmount()).isEqualByComparingTo(expected);
    }

    @Test
    void 한도가_소진되면_expectedAmount는_0이다() {
        // 못 받는 혜택에 금액이 실리면 안 된다 — status는 예전대로 CONDITION_NOT_MET이다
        givenClippingFixture(new BigDecimal("20000.00"), new BigDecimal("20000"));

        CardBenefitResponse result = singleCardResultFor("35000");

        assertThat(result.getStatus()).isEqualTo(CardBenefitStatus.CONDITION_NOT_MET);
        assertThat(result.getReason().getCode()).isEqualTo(BenefitReasonCode.LIMIT_EXHAUSTED);
        assertThat(result.getBenefit().getExpectedAmount()).isEqualByComparingTo("0");
    }

    @Test
    void 잔여가_산출액보다_넉넉하면_산출액_그대로_안내한다() {
        givenClippingFixture(new BigDecimal("20000.00"), new BigDecimal("1000"));

        CardBenefitResponse result = singleCardResultFor("35000");

        assertThat(result.getBenefit().getExpectedAmount()).isEqualByComparingTo("3500");
    }

    @Test
    void POINT_한도의_잔여는_원화로_환산해_자른다() {
        // 35,000 × 5% = 1,750 포인트 = 1,400원. 잔여 1,000포인트 × 0.8 = 800원이라 800원까지만.
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        BenefitCandidateResponse candidate = candidate(133L, null, BenefitType.ACCUMULATE, ValueType.RATE,
                new BigDecimal("5"), "마이신한포인트", new BigDecimal("0.8"), true);
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(candidate));
        given(benefitMapper.findLimits(null, 133L, PREV_MONTH_SPEND))
                .willReturn(List.of(limit(21L, LimitBasis.POINT, LimitPeriod.MONTH, new BigDecimal("1000"))));
        given(benefitMapper.findUsage(eq(USER_CARD_ID), eq(21L), any()))
                .willReturn(usage(BigDecimal.ZERO, 0L));

        CardBenefitResponse result = singleCardResultFor("35000");

        assertThat(result.getBenefit().getExpectedAmount()).isEqualByComparingTo("800");
    }

    @Test
    void COUNT_한도는_잔여가_있으면_금액을_자르지_않는다() {
        // 횟수는 금액 축이 아니다 — 1회라도 남았으면 그 결제는 만액을 받는다
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        BenefitCandidateResponse candidate = candidate(133L, null, BenefitType.CASHBACK, ValueType.RATE,
                new BigDecimal("10"), null, null, true);
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(candidate));
        given(benefitMapper.findLimits(null, 133L, PREV_MONTH_SPEND))
                .willReturn(List.of(limit(21L, LimitBasis.COUNT, LimitPeriod.MONTH, new BigDecimal("3"))));
        given(benefitMapper.findUsage(eq(USER_CARD_ID), eq(21L), any()))
                .willReturn(usage(BigDecimal.ZERO, 1L));

        CardBenefitResponse result = singleCardResultFor("35000");

        assertThat(result.getBenefit().getExpectedAmount()).isEqualByComparingTo("3500");
    }

    @Test
    void 한도가_여러_개면_가장_빡빡한_잔여가_이긴다() {
        // 월 잔여 4,000 · 일 잔여 2,000 → 2,000
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        BenefitCandidateResponse candidate = candidate(133L, null, BenefitType.CASHBACK, ValueType.RATE,
                new BigDecimal("10"), null, null, true);
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(candidate));
        given(benefitMapper.findLimits(null, 133L, PREV_MONTH_SPEND)).willReturn(List.of(
                limit(21L, LimitBasis.AMOUNT, LimitPeriod.MONTH, new BigDecimal("20000.00")),
                limit(22L, LimitBasis.AMOUNT, LimitPeriod.DAY, new BigDecimal("5000.00"))));
        given(benefitMapper.findUsage(eq(USER_CARD_ID), eq(21L), any()))
                .willReturn(usage(new BigDecimal("16000"), 0L));
        given(benefitMapper.findUsage(eq(USER_CARD_ID), eq(22L), any()))
                .willReturn(usage(new BigDecimal("3000"), 0L));

        CardBenefitResponse result = singleCardResultFor("35000");

        assertThat(result.getBenefit().getExpectedAmount()).isEqualByComparingTo("2000");
    }

    @Test
    void 잔여가_적은_후보보다_실제로_더_주는_후보가_이긴다() {
        // 산출액만 보면 정률(3,500)이 이기지만 잔여가 500뿐이라 실제로는 정액(1,000)이 낫다
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        BenefitCandidateResponse rate = candidate(35L, null, BenefitType.CASHBACK, ValueType.RATE,
                new BigDecimal("10"), null, null, true);
        BenefitCandidateResponse fixed = candidate(43L, null, BenefitType.CASHBACK, ValueType.FIXED,
                new BigDecimal("1000"), null, null, true);
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(rate, fixed));
        given(benefitMapper.findLimits(null, 35L, PREV_MONTH_SPEND))
                .willReturn(List.of(limit(21L, LimitBasis.AMOUNT, LimitPeriod.MONTH, new BigDecimal("20000.00"))));
        given(benefitMapper.findUsage(eq(USER_CARD_ID), eq(21L), any()))
                .willReturn(usage(new BigDecimal("19500"), 0L));
        given(benefitMapper.findLimits(null, 43L, PREV_MONTH_SPEND)).willReturn(List.of());

        CardBenefitResponse result = singleCardResultFor("35000");

        assertThat(result.getBenefit().getBenefitServiceId()).isEqualTo(43L);
        assertThat(result.getBenefit().getExpectedAmount()).isEqualByComparingTo("1000");
    }

    @Test
    void amount가_없으면_잔여가_있어도_expectedAmount는_null이다() {
        givenClippingFixture(new BigDecimal("20000.00"), new BigDecimal("19999"));

        CardBenefitResponse result = singleCardResult();

        assertThat(result.getStatus()).isEqualTo(CardBenefitStatus.AVAILABLE);
        assertThat(result.getBenefit().getExpectedAmount()).isNull();
    }

    // ---------- 건당 최소 이용금액 ----------

    @Test
    void 결제금액이_건당_최소_이용금액에_못_미치면_MIN_TX_AMOUNT_NOT_MET이다() {
        givenMinTxFixture(new BigDecimal("10000"));

        CardBenefitResponse result = singleCardResultFor("5000");

        assertThat(result.getStatus()).isEqualTo(CardBenefitStatus.CONDITION_NOT_MET);
        assertThat(result.getReason().getCode()).isEqualTo(BenefitReasonCode.MIN_TX_AMOUNT_NOT_MET);
        assertThat(result.getReason().getMessage()).isEqualTo("10,000원 이상 결제해야 받을 수 있는 혜택이에요.");
    }

    @Test
    void 조건_미달이면_benefit은_내려가지_않는다() {
        // 혜택이 "덜" 발생하는 게 아니라 아예 발생하지 않는다 — PREV_SPEND_NOT_MET과 같은 모양이다
        givenMinTxFixture(new BigDecimal("10000"));

        CardBenefitResponse result = singleCardResultFor("5000");

        assertThat(result.getBenefit()).isNull();
    }

    @ParameterizedTest(name = "최소 10,000원 · 결제 {0}원 → {1}")
    @CsvSource({
            "9999,  CONDITION_NOT_MET",
            "10000, AVAILABLE",          // 이상(>=)이라 딱 맞으면 통과다
            "10001, AVAILABLE"
    })
    void 건당_최소_이용금액은_이상_비교다(String amount, CardBenefitStatus expected) {
        givenMinTxFixture(new BigDecimal("10000"));
        // 9,999원 케이스는 게이트에 막혀 한도를 조회하지 않는다 — 그래서 lenient다
        lenient().when(benefitMapper.findLimits(null, 133L, PREV_MONTH_SPEND)).thenReturn(List.of());

        CardBenefitResponse result = singleCardResultFor(amount);

        assertThat(result.getStatus()).isEqualTo(expected);
    }

    @Test
    void 문턱을_넘는_후보가_하나라도_있으면_그_후보로_판정한다() {
        // 5,000원 결제: 문턱 10,000인 정률은 탈락하고 문턱 없는 정액이 남는다
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        BenefitCandidateResponse blocked = candidateWithMinTx(35L, ValueType.RATE,
                new BigDecimal("10"), new BigDecimal("10000"));
        BenefitCandidateResponse passing = candidateWithMinTx(43L, ValueType.FIXED,
                new BigDecimal("500"), BigDecimal.ZERO);
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(blocked, passing));
        given(benefitMapper.findLimits(null, 43L, PREV_MONTH_SPEND)).willReturn(List.of());

        CardBenefitResponse result = singleCardResultFor("5000");

        assertThat(result.getStatus()).isEqualTo(CardBenefitStatus.AVAILABLE);
        assertThat(result.getBenefit().getBenefitServiceId()).isEqualTo(43L);
        then(benefitMapper).should(never()).findLimits(any(), eq(35L), any());
    }

    @Test
    void 전부_미달이면_문턱이_가장_낮은_후보를_안내한다() {
        // 조금만 더 쓰면 되는 쪽을 알려준다 — 30,000이 아니라 10,000
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(
                        candidateWithMinTx(35L, ValueType.RATE, new BigDecimal("10"), new BigDecimal("30000")),
                        candidateWithMinTx(43L, ValueType.FIXED, new BigDecimal("500"), new BigDecimal("10000"))));

        CardBenefitResponse result = singleCardResultFor("5000");

        assertThat(result.getReason().getMessage()).isEqualTo("10,000원 이상 결제해야 받을 수 있는 혜택이에요.");
    }

    @Test
    void 전월실적이_미달이면_건당_최소금액보다_먼저_안내한다() {
        // 실적이 아예 안 되면 금액을 올려도 소용없다 — PREV_SPEND_NOT_MET이 이긴다
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        BenefitCandidateResponse candidate = BenefitCandidateResponse.builder()
                .serviceId(133L).benefitType(BenefitType.CASHBACK).valueType(ValueType.RATE)
                .valueNumber(new BigDecimal("10")).minTxAmount(new BigDecimal("10000")).tierOk(false)
                .build();
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(candidate));

        CardBenefitResponse result = singleCardResultFor("5000");

        assertThat(result.getReason().getCode()).isEqualTo(BenefitReasonCode.PREV_SPEND_NOT_MET);
    }

    @Test
    void amount가_없으면_건당_최소금액_게이트를_건너뛴다() {
        // 금액을 모르는 조회는 판정할 근거가 없다 — 현행 동작을 유지한다
        givenMinTxFixture(new BigDecimal("10000"));
        given(benefitMapper.findLimits(null, 133L, PREV_MONTH_SPEND)).willReturn(List.of());

        CardBenefitResponse result = singleCardResult();

        assertThat(result.getStatus()).isEqualTo(CardBenefitStatus.AVAILABLE);
    }

    @Test
    void 최소금액이_0이면_조건이_없는_것이다() {
        // DDL이 NOT NULL DEFAULT 0이라 "조건 없음"은 null이 아니라 0이다
        givenMinTxFixture(BigDecimal.ZERO);
        given(benefitMapper.findLimits(null, 133L, PREV_MONTH_SPEND)).willReturn(List.of());

        CardBenefitResponse result = singleCardResultFor("1");

        assertThat(result.getStatus()).isEqualTo(CardBenefitStatus.AVAILABLE);
    }

    // ---------- 픽스처 헬퍼 ----------

    /** 정률 10% 후보 하나에 건당 최소 이용금액을 걸어 둔다. 한도는 걸지 않는다. */
    private void givenMinTxFixture(BigDecimal minTxAmount) {
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(candidateWithMinTx(133L, ValueType.RATE, new BigDecimal("10"), minTxAmount)));
    }

    private BenefitCandidateResponse candidateWithMinTx(Long serviceId, ValueType valueType,
                                                          BigDecimal valueNumber, BigDecimal minTxAmount) {
        return BenefitCandidateResponse.builder()
                .serviceId(serviceId)
                .benefitType(BenefitType.CASHBACK)
                .valueType(valueType)
                .valueNumber(valueNumber)
                .minTxAmount(minTxAmount)
                .tierOk(true)
                .build();
    }

    /** 정률 10% 후보 하나에 월 AMOUNT 한도를 걸어 둔다. 35,000원 결제면 산출액은 3,500원이다. */
    private void givenClippingFixture(BigDecimal limitValue, BigDecimal usedAmount) {
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        BenefitCandidateResponse candidate = candidate(133L, null, BenefitType.CASHBACK, ValueType.RATE,
                new BigDecimal("10"), null, null, true);
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(candidate));
        given(benefitMapper.findLimits(null, 133L, PREV_MONTH_SPEND))
                .willReturn(List.of(limit(21L, LimitBasis.AMOUNT, LimitPeriod.MONTH, limitValue)));
        given(benefitMapper.findUsage(eq(USER_CARD_ID), eq(21L), any()))
                .willReturn(usage(usedAmount, 0L));
    }

    private void givenStore(Long categoryId, Long brandId) {
        given(benefitMapper.findStore(STORE_ID_LONG)).willReturn(BenefitStoreResponse.builder()
                .storeId(STORE_ID_LONG).storeName("테스트가맹점")
                .categoryId(categoryId).brandId(brandId).build());
    }

    private void givenOneCard() {
        given(benefitMapper.findUserCards(USER_ID)).willReturn(List.of(card(USER_CARD_ID, CARD_PRODUCT_ID, 1)));
    }

    private void givenPrevMonthSpend(BigDecimal spend) {
        given(benefitMapper.findPrevMonthSpends(List.of(USER_CARD_ID))).willReturn(List.of(
                BenefitPrevMonthSpendResponse.builder().userCardId(USER_CARD_ID).prevMonthSpend(spend).build()));
    }

    private CardBenefitResponse singleCardResult() {
        return benefitService.findExpectedBenefits(USER_ID, STORE_ID, null).getCards().get(0);
    }

    private CardBenefitResponse singleCardResultFor(String amount) {
        return benefitService.findExpectedBenefits(USER_ID, STORE_ID, amount).getCards().get(0);
    }

    /** displayText 검증용 — 후보 하나를 AVAILABLE(한도 미발견)로 만들어 그 결과를 돌려준다. */
    private CardBenefitResponse singleAvailableResultWith(BenefitType benefitType, ValueType valueType,
                                                            BigDecimal valueNumber, String currencyName,
                                                            BigDecimal krwPerPoint) {
        givenStore(CATEGORY_ID, BRAND_ID);
        givenOneCard();
        givenPrevMonthSpend(PREV_MONTH_SPEND);
        BenefitCandidateResponse candidate = candidate(133L, null, benefitType, valueType, valueNumber,
                currencyName, krwPerPoint, true);
        given(benefitMapper.findCandidates(CARD_PRODUCT_ID, PREV_MONTH_SPEND, BRAND_ID, CATEGORY_ID))
                .willReturn(List.of(candidate));
        given(benefitMapper.findLimits(null, 133L, PREV_MONTH_SPEND)).willReturn(List.of());

        return singleCardResult();
    }

    private BenefitUserCardResponse card(long userCardId, long cardProductId, int displayOrder) {
        return BenefitUserCardResponse.builder()
                .userCardId(userCardId).cardProductId(cardProductId).displayOrder(displayOrder)
                .cardName("카드").cardImageUrl("img.png").cardCompanyName("카드사").build();
    }

    private BenefitCandidateResponse candidate(long serviceId, Long planGroupId, BenefitType benefitType,
                                                ValueType valueType, BigDecimal valueNumber, String currencyName,
                                                BigDecimal krwPerPoint, boolean tierOk) {
        return BenefitCandidateResponse.builder()
                .serviceId(serviceId).planGroupId(planGroupId).benefitName("혜택" + serviceId)
                .benefitType(benefitType).valueType(valueType).valueNumber(valueNumber)
                .currencyName(currencyName).krwPerPoint(krwPerPoint).tierOk(tierOk)
                .build();
    }

    private BenefitLimitResponse limit(long tierId, LimitBasis basis, LimitPeriod period, BigDecimal value) {
        return BenefitLimitResponse.builder()
                .tierId(tierId).limitBasis(basis).limitPeriod(period).limitValue(value).build();
    }

    private BenefitUsageResponse usage(BigDecimal amount, long count) {
        return BenefitUsageResponse.builder().usedAmount(amount).usedCount(count).build();
    }
}
