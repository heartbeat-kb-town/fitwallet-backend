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

    // ---------- 픽스처 헬퍼 ----------

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
