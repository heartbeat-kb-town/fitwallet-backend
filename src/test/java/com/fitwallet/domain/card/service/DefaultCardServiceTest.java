package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.CardTransactionCardInfo;
import com.fitwallet.domain.card.dto.CardEventTargetType;
import com.fitwallet.domain.card.dto.CardListSortType;
import com.fitwallet.domain.card.dto.CardSummaryCardInfo;
import com.fitwallet.domain.card.dto.CardTransactionSummaryType;
import com.fitwallet.domain.card.dto.CardType;
import com.fitwallet.domain.card.dto.CardUsageAmountSummary;
import com.fitwallet.domain.card.dto.CardUsageBenefitRule;
import com.fitwallet.domain.card.dto.CardUsageCardInfo;
import com.fitwallet.domain.card.dto.CardUsagePerformanceStatus;
import com.fitwallet.domain.card.dto.CardUsageTierType;
import com.fitwallet.domain.card.dto.MyDataCard;
import com.fitwallet.domain.card.dto.MyDataTransaction;
import com.fitwallet.domain.card.dto.request.CardRegisterRequest;
import com.fitwallet.domain.card.dto.request.CardListSearchRequest;
import com.fitwallet.domain.card.dto.request.CardRecentTransactionSearchCondition;
import com.fitwallet.domain.card.dto.request.CardTransactionSearchCondition;
import com.fitwallet.domain.card.dto.request.CardTransactionSearchRequest;
import com.fitwallet.domain.card.dto.request.CardUsagePeriodCondition;
import com.fitwallet.domain.card.dto.request.CardUsageSearchRequest;
import com.fitwallet.domain.card.dto.response.CardListResponse;
import com.fitwallet.domain.card.dto.response.CardEventItemResponse;
import com.fitwallet.domain.card.dto.response.CardEventResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitResponse;
import com.fitwallet.domain.card.dto.response.CardSummaryResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionDetailResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionItemResponse;
import com.fitwallet.domain.card.dto.response.CardUsageDetailResponse;
import com.fitwallet.domain.benefit.dto.BenefitType;
import com.fitwallet.domain.benefit.dto.ValueType;
import com.fitwallet.domain.card.exception.CardErrorCode;
import com.fitwallet.domain.card.mapper.CardMapper;
import com.fitwallet.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

/**
 * Service 단위 테스트. Mapper를 목킹하므로 DB가 필요 없다.
 * <p>
 * {@code @InjectMocks}는 구체 클래스가 있어야 인스턴스를 만들 수 있어
 * 필드 타입을 인터페이스({@code CardService})가 아니라 구현체로 둔다.
 */
@ExtendWith(MockitoExtension.class)
class DefaultCardServiceTest {

    @Mock
    private CardMapper cardMapper;

    @Mock
    private MyDataProvider myDataProvider;

    private DefaultCardService cardService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-23T15:00:00Z"),
                ZoneId.of("Asia/Seoul"));
        CardBenefitValueLabelFormatter benefitValueLabelFormatter =
                new CardBenefitValueLabelFormatter();
        cardService = new DefaultCardService(
                cardMapper,
                new CardMonthlyPeriodResolver(clock),
                new CardMonthlyBenefitPeriodResolver(clock),
                new CardMonthlyBenefitCalculator(benefitValueLabelFormatter),
                new CardUsageRuleNormalizer(),
                new CardUsageTierIntegrator(),
                new CardUsageBenefitAllocator(benefitValueLabelFormatter),
                new CardUsageTierStateCalculator(),
                myDataProvider,
                clock);
    }

    @Test
    void 내_카드_목록을_매퍼가_준_순서_그대로_반환한다() {
        given(cardMapper.findByUserId(1L, CardListSortType.DISPLAY_ORDER)).willReturn(List.of(
                CardListResponse.builder().userCardId(10L).cardType(CardType.CREDIT).build(),
                CardListResponse.builder().userCardId(11L).cardType(CardType.DEBIT).build()));

        List<CardListResponse> cards = cardService.findMyCards(1L, null);

        assertThat(cards).extracting(CardListResponse::getUserCardId)
                .containsExactly(10L, 11L);
    }

    @Test
    void 카드가_없으면_빈_목록을_반환한다() {
        given(cardMapper.findByUserId(1L, CardListSortType.DISPLAY_ORDER)).willReturn(List.of());

        assertThat(cardService.findMyCards(1L, null)).isEmpty();
    }

    @Test
    void 카드별_이벤트는_today를_매퍼에_전달하고_응답을_조립한다() {
        given(cardMapper.findSummaryCardInfo(1L, 1L)).willReturn(CardSummaryCardInfo.builder()
                .cardId(1L)
                .cardProductId(47L)
                .cardName("KB국민 청춘대로 톡톡카드")
                .issuerName("KB국민카드")
                .build());
        CardEventItemResponse event = CardEventItemResponse.builder()
                .eventId(3L)
                .targetType(CardEventTargetType.CARD_PRODUCT)
                .summary("CGV 모바일 예매 시 할인")
                .startsAt(LocalDate.of(2026, 7, 1))
                .endsAt(LocalDate.of(2026, 7, 31))
                .daysRemaining(7L)
                .detailUrl("https://card.kbcard.com/")
                .detailAvailable(true)
                .build();
        given(cardMapper.findCardEventItems(eq(1L), eq(1L), any(LocalDate.class)))
                .willReturn(List.of(event));

        CardEventResponse response = cardService.findCardEvents(1L, 1L);

        assertThat(response.getCard().getCardId()).isEqualTo(1L);
        assertThat(response.getCard().getCardProductId()).isEqualTo(47L);
        assertThat(response.getCard().getCardName()).isEqualTo("KB국민 청춘대로 톡톡카드");
        assertThat(response.getCard().getIssuerName()).isEqualTo("KB국민카드");
        assertThat(response.getEventCount()).isEqualTo(1);
        assertThat(response.getEvents()).containsExactly(event);

        ArgumentCaptor<LocalDate> todayCaptor = ArgumentCaptor.forClass(LocalDate.class);
        then(cardMapper).should().findCardEventItems(eq(1L), eq(1L), todayCaptor.capture());
        assertThat(todayCaptor.getValue()).isEqualTo(LocalDate.of(2026, 7, 24));
    }

    @Test
    void 카드별_이벤트는_카드가_없으면_CARD_NOT_FOUND를_던지고_이벤트를_조회하지_않는다() {
        given(cardMapper.findSummaryCardInfo(1L, 999L)).willReturn(null);

        assertErrorCode(
                () -> cardService.findCardEvents(1L, 999L),
                CardErrorCode.CARD_NOT_FOUND);
        then(cardMapper).should(never()).findCardEventItems(any(), any(), any());
    }

    @Test
    void 카드별_이벤트는_summary가_null이면_정합성_예외를_던진다() {
        given(cardMapper.findSummaryCardInfo(1L, 1L)).willReturn(CardSummaryCardInfo.builder()
                .cardId(1L)
                .cardProductId(47L)
                .build());
        given(cardMapper.findCardEventItems(eq(1L), eq(1L), any(LocalDate.class)))
                .willReturn(List.of(CardEventItemResponse.builder().summary(null).build()));

        assertErrorCode(
                () -> cardService.findCardEvents(1L, 1L),
                CardErrorCode.INVALID_CARD_EVENT_DATA);
    }

    @Test
    void 카드별_이벤트는_summary가_blank이면_정합성_예외를_던진다() {
        given(cardMapper.findSummaryCardInfo(1L, 1L)).willReturn(CardSummaryCardInfo.builder()
                .cardId(1L)
                .cardProductId(47L)
                .build());
        given(cardMapper.findCardEventItems(eq(1L), eq(1L), any(LocalDate.class)))
                .willReturn(List.of(CardEventItemResponse.builder().summary(" ").build()));

        assertErrorCode(
                () -> cardService.findCardEvents(1L, 1L),
                CardErrorCode.INVALID_CARD_EVENT_DATA);
    }

    @Test
    void 카드별_이벤트가_없으면_빈_배열과_0건을_반환한다() {
        given(cardMapper.findSummaryCardInfo(1L, 1L)).willReturn(CardSummaryCardInfo.builder()
                .cardId(1L)
                .cardProductId(47L)
                .cardName("테스트 카드")
                .issuerName("테스트 카드사")
                .build());
        given(cardMapper.findCardEventItems(eq(1L), eq(1L), any(LocalDate.class)))
                .willReturn(null);

        CardEventResponse response = cardService.findCardEvents(1L, 1L);

        assertThat(response.getEventCount()).isZero();
        assertThat(response.getEvents()).isEmpty();
    }

    @Test
    void 카드_월간혜택은_기존_카드정보와_전월실적_조회를_재사용한다() {
        given(cardMapper.findSummaryCardInfo(1L, 2L)).willReturn(CardSummaryCardInfo.builder()
                .cardId(2L)
                .cardProductId(15L)
                .cardName("신한카드 All Pass")
                .issuerName("신한카드")
                .cardType(CardType.CREDIT)
                .build());
        given(cardMapper.findUsageAmounts(eq(1L), eq(2L), any()))
                .willReturn(CardUsageAmountSummary.builder()
                        .recognizedAmount(new BigDecimal("317300"))
                        .excludedAmount(BigDecimal.ZERO)
                        .build());
        given(cardMapper.findUsageBenefitRules(15L)).willReturn(List.of(
                CardUsageBenefitRule.builder()
                        .benefitId(53L)
                        .benefitName("일반 할인")
                        .benefitType(BenefitType.CASHBACK)
                        .valueType(ValueType.RATE)
                        .valueNumber(new BigDecimal("10"))
                        .benefitMinimumAmount(new BigDecimal("300000"))
                        .build()));
        given(cardMapper.findMonthlyBenefitRules(15L)).willReturn(List.of());
        given(cardMapper.findMonthlyBenefitCategoryTargets(15L)).willReturn(List.of());
        given(cardMapper.findMonthlyBenefitBrandTargets(15L)).willReturn(List.of());
        given(cardMapper.findMonthlyBenefitTargetUsages(eq(1L), eq(2L), any()))
                .willReturn(List.of());

        CardMonthlyBenefitResponse response = cardService.getCardMonthlyBenefit(1L, 2L);

        assertThat(response.getYearMonth()).isEqualTo("2026-07");
        assertThat(response.getAsOfDate()).isEqualTo(LocalDate.of(2026, 7, 23));
        assertThat(response.getPerformance().getStatus())
                .isEqualTo(CardUsagePerformanceStatus.ACHIEVED);
        assertThat(response.getPerformance().getCurrentTier().getTierName())
                .isEqualTo("1구간");
        assertThat(response.getCategoryBenefits()).isEmpty();
        assertThat(response.getBrandBenefits()).isEmpty();

        ArgumentCaptor<CardUsagePeriodCondition> conditionCaptor =
                ArgumentCaptor.forClass(CardUsagePeriodCondition.class);
        then(cardMapper).should().findUsageAmounts(eq(1L), eq(2L), conditionCaptor.capture());
        assertThat(conditionCaptor.getValue().getStartAt())
                .isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0));
        assertThat(conditionCaptor.getValue().getEndAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
    }

    @Test
    void 카드_월간혜택은_소유한_카드가_아니면_찾을수없음_예외를_던진다() {
        given(cardMapper.findSummaryCardInfo(1L, 999L)).willReturn(null);

        assertThatThrownBy(() -> cardService.getCardMonthlyBenefit(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(CardErrorCode.CARD_NOT_FOUND));

        then(cardMapper).should(never()).findUsageAmounts(any(), any(), any());
    }

    @Test
    void 최근사용순_요청을_매퍼에_전달한다() {
        CardListSearchRequest request = new CardListSearchRequest();
        ReflectionTestUtils.setField(request, "sort", CardListSortType.RECENTLY_USED);
        given(cardMapper.findByUserId(1L, CardListSortType.RECENTLY_USED))
                .willReturn(List.of());

        cardService.findMyCards(1L, request);

        then(cardMapper).should().findByUserId(1L, CardListSortType.RECENTLY_USED);
    }

    @Test
    void 신용카드_요약은_전날까지_금액과_오늘_어제_최근내역을_조회한다() {
        given(cardMapper.findSummaryCardInfo(1L, 10L)).willReturn(
                CardSummaryCardInfo.builder()
                        .cardId(10L)
                        .cardProductId(20L)
                        .cardName("테스트 신용카드")
                        .issuerName("테스트 카드사")
                        .cardType(CardType.CREDIT)
                        .build());
        given(cardMapper.sumTransactionAmount(eq(1L), eq(10L), any()))
                .willReturn(new BigDecimal("1240000.00"));
        given(cardMapper.findRecentTransactions(eq(1L), eq(10L), any()))
                .willReturn(List.of());
        given(cardMapper.findUsageAmounts(eq(1L), eq(10L), any()))
                .willReturn(CardUsageAmountSummary.builder()
                        .recognizedAmount(BigDecimal.ZERO)
                        .excludedAmount(BigDecimal.ZERO)
                        .build());
        given(cardMapper.findUsageBenefitRules(20L)).willReturn(List.of());

        CardSummaryResponse response = cardService.findCardSummary(1L, 10L);

        assertThat(response.getCard().getCardId()).isEqualTo(10L);
        assertThat(response.getAmountSummary().getCreditUsageAmount())
                .isEqualByComparingTo("1240000.00");
        assertThat(response.getAmountSummary().getAsOfDate())
                .isEqualTo(LocalDate.of(2026, 7, 23));
        assertThat(response.getUsageSummary().getTierType())
                .isEqualTo(CardUsageTierType.NO_REQUIREMENT);
        assertThat(response.getUsageSummary().getRecognizedAmount()).isNull();

        ArgumentCaptor<CardTransactionSearchCondition> amountCaptor =
                ArgumentCaptor.forClass(CardTransactionSearchCondition.class);
        then(cardMapper).should().sumTransactionAmount(eq(1L), eq(10L), amountCaptor.capture());
        assertThat(amountCaptor.getValue().getStartAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
        assertThat(amountCaptor.getValue().getEndAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 24, 0, 0));

        ArgumentCaptor<CardRecentTransactionSearchCondition> recentCaptor =
                ArgumentCaptor.forClass(CardRecentTransactionSearchCondition.class);
        then(cardMapper).should().findRecentTransactions(
                eq(1L), eq(10L), recentCaptor.capture());
        assertThat(recentCaptor.getValue().getStartAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 23, 0, 0));
        assertThat(recentCaptor.getValue().getEndAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 25, 0, 0));
    }

    @Test
    void 체크카드_요약은_잔액이_0이어도_정상이다() {
        given(cardMapper.findSummaryCardInfo(1L, 10L)).willReturn(
                CardSummaryCardInfo.builder()
                        .cardId(10L)
                        .cardProductId(20L)
                        .cardName("테스트 체크카드")
                        .issuerName("테스트 카드사")
                        .cardType(CardType.DEBIT)
                        .bankName("KB국민은행")
                        .balance(BigDecimal.ZERO)
                        .build());
        given(cardMapper.findRecentTransactions(eq(1L), eq(10L), any()))
                .willReturn(List.of());
        given(cardMapper.findUsageAmounts(eq(1L), eq(10L), any()))
                .willReturn(CardUsageAmountSummary.builder()
                        .recognizedAmount(BigDecimal.ZERO)
                        .excludedAmount(BigDecimal.ZERO)
                        .build());
        given(cardMapper.findUsageBenefitRules(20L)).willReturn(List.of());

        CardSummaryResponse response = cardService.findCardSummary(1L, 10L);

        assertThat(response.getAmountSummary().getBalance()).isZero();
        assertThat(response.getAmountSummary().getBankName()).isEqualTo("KB국민은행");
        assertThat(response.getAmountSummary().getAsOfDate())
                .isEqualTo(LocalDate.of(2026, 7, 24));
        assertThat(response.getAmountSummary().getCreditUsageAmount()).isNull();
    }

    @Test
    void 체크카드_은행명이나_잔액이_없으면_요약_정합성_예외를_던진다() {
        given(cardMapper.findSummaryCardInfo(1L, 10L)).willReturn(
                CardSummaryCardInfo.builder()
                        .cardId(10L)
                        .cardProductId(20L)
                        .cardType(CardType.DEBIT)
                        .bankName(" ")
                        .balance(null)
                        .build());

        assertErrorCode(
                () -> cardService.findCardSummary(1L, 10L),
                CardErrorCode.INVALID_CARD_SUMMARY_DATA);
    }

    @Test
    void 카드_요약_조회시_없는_카드면_CARD_NOT_FOUND_예외를_던진다() {
        given(cardMapper.findSummaryCardInfo(1L, 999L)).willReturn(null);

        assertThatThrownBy(() -> cardService.findCardSummary(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CardErrorCode.CARD_NOT_FOUND);
    }

    @Test
    void 다른_사용자의_카드는_요약_조회되지_않아_예외를_던진다() {
        // 매퍼가 userId 조건을 함께 걸기 때문에 남의 카드는 null로 돌아온다
        given(cardMapper.findSummaryCardInfo(2L, 10L)).willReturn(null);

        assertThatThrownBy(() -> cardService.findCardSummary(2L, 10L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 현재월_신용카드는_전날까지_조회하고_거래금액을_합산한다() {
        given(cardMapper.findTransactionCardInfo(1L, 10L))
                .willReturn(transactionCard(CardType.CREDIT, new BigDecimal("89800.00")));
        given(cardMapper.sumTransactionAmount(eq(1L), eq(10L), any()))
                .willReturn(new BigDecimal("89800.00"));
        given(cardMapper.findTransactions(eq(1L), eq(10L), any()))
                .willReturn(List.of());

        CardTransactionDetailResponse response =
                cardService.getCardTransactions(1L, 10L, searchRequest(null, null, null));

        assertThat(response.getYearMonth()).isEqualTo("2026-07");
        assertThat(response.getAvailableYearMonths())
                .containsExactly("2026-07", "2026-06", "2026-05");
        assertThat(response.getPaymentSummary().getSummaryType())
                .isEqualTo(CardTransactionSummaryType.MONTHLY_PAYMENT_AMOUNT);
        assertThat(response.getPaymentSummary().getAmount()).isEqualByComparingTo("89800.00");
        assertThat(response.getTransactions().getSize()).isZero();
        assertThat(response.getTransactions().getHasNext()).isFalse();
        assertThat(response.getTransactions().getNextCursor()).isNull();

        ArgumentCaptor<CardTransactionSearchCondition> captor =
                ArgumentCaptor.forClass(CardTransactionSearchCondition.class);
        then(cardMapper).should().findTransactions(eq(1L), eq(10L), captor.capture());
        assertThat(captor.getValue().getStartAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
        assertThat(captor.getValue().getEndAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 24, 0, 0));
        assertThat(captor.getValue().getLimit()).isEqualTo(21);
        then(cardMapper).should().sumTransactionAmount(eq(1L), eq(10L), any());
    }

    @Test
    void 현재월_체크카드는_오늘까지_조회하고_거래금액을_합산한다() {
        given(cardMapper.findTransactionCardInfo(1L, 10L))
                .willReturn(transactionCard(CardType.DEBIT, null));
        given(cardMapper.sumTransactionAmount(eq(1L), eq(10L), any()))
                .willReturn(new BigDecimal("178400.00"));
        given(cardMapper.findTransactions(eq(1L), eq(10L), any()))
                .willReturn(List.of());

        CardTransactionDetailResponse response =
                cardService.getCardTransactions(1L, 10L, searchRequest("2026-07", 10, null));

        assertThat(response.getPaymentSummary().getSummaryType())
                .isEqualTo(CardTransactionSummaryType.MONTHLY_PAYMENT_AMOUNT);
        assertThat(response.getPaymentSummary().getAmount()).isEqualByComparingTo("178400.00");

        ArgumentCaptor<CardTransactionSearchCondition> captor =
                ArgumentCaptor.forClass(CardTransactionSearchCondition.class);
        then(cardMapper).should().sumTransactionAmount(eq(1L), eq(10L), captor.capture());
        assertThat(captor.getValue().getEndAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 25, 0, 0));
        assertThat(captor.getValue().getLimit()).isEqualTo(11);
    }

    @Test
    void 과거월_신용카드는_다음달_시작전까지_조회하고_거래금액을_합산한다() {
        given(cardMapper.findTransactionCardInfo(1L, 10L))
                .willReturn(transactionCard(CardType.CREDIT, new BigDecimal("89800.00")));
        given(cardMapper.sumTransactionAmount(eq(1L), eq(10L), any()))
                .willReturn(new BigDecimal("306800.00"));
        given(cardMapper.findTransactions(eq(1L), eq(10L), any()))
                .willReturn(List.of());

        CardTransactionDetailResponse response =
                cardService.getCardTransactions(1L, 10L, searchRequest("2026-06", null, null));

        assertThat(response.getPaymentSummary().getSummaryType())
                .isEqualTo(CardTransactionSummaryType.MONTHLY_PAYMENT_AMOUNT);
        assertThat(response.getPaymentSummary().getAmount()).isEqualByComparingTo("306800.00");

        ArgumentCaptor<CardTransactionSearchCondition> captor =
                ArgumentCaptor.forClass(CardTransactionSearchCondition.class);
        then(cardMapper).should().sumTransactionAmount(eq(1L), eq(10L), captor.capture());
        assertThat(captor.getValue().getStartAt())
                .isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0));
        assertThat(captor.getValue().getEndAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
    }

    @Test
    void 결제내역_조회시_카드를_찾을수_없으면_CARD_NOT_FOUND_예외를_던진다() {
        given(cardMapper.findTransactionCardInfo(1L, 999L)).willReturn(null);

        assertErrorCode(
                () -> cardService.getCardTransactions(1L, 999L, searchRequest(null, null, null)),
                CardErrorCode.CARD_NOT_FOUND);
    }

    @Test
    void 현재월_신용카드는_저장금액이_null이어도_거래금액을_합산한다() {
        given(cardMapper.findTransactionCardInfo(1L, 10L))
                .willReturn(transactionCard(CardType.CREDIT, null));
        given(cardMapper.sumTransactionAmount(eq(1L), eq(10L), any()))
                .willReturn(BigDecimal.ZERO);
        given(cardMapper.findTransactions(eq(1L), eq(10L), any()))
                .willReturn(List.of());

        CardTransactionDetailResponse response =
                cardService.getCardTransactions(1L, 10L, searchRequest(null, null, null));

        assertThat(response.getPaymentSummary().getAmount()).isZero();
    }

    @Test
    void 조회연월_형식과_최근3개월_범위를_검증한다() {
        given(cardMapper.findTransactionCardInfo(1L, 10L))
                .willReturn(transactionCard(CardType.CREDIT, BigDecimal.ZERO));

        assertErrorCode(
                () -> cardService.getCardTransactions(
                        1L, 10L, searchRequest("2026-7", null, null)),
                CardErrorCode.INVALID_YEAR_MONTH);
        assertErrorCode(
                () -> cardService.getCardTransactions(
                        1L, 10L, searchRequest("2026-04", null, null)),
                CardErrorCode.YEAR_MONTH_OUT_OF_RANGE);
        assertErrorCode(
                () -> cardService.getCardTransactions(
                        1L, 10L, searchRequest("2026-08", null, null)),
                CardErrorCode.YEAR_MONTH_OUT_OF_RANGE);
    }

    @Test
    void 조회개수는_1부터_100까지만_허용한다() {
        given(cardMapper.findTransactionCardInfo(1L, 10L))
                .willReturn(transactionCard(CardType.CREDIT, BigDecimal.ZERO));

        assertErrorCode(
                () -> cardService.getCardTransactions(
                        1L, 10L, searchRequest(null, 0, null)),
                CardErrorCode.INVALID_TRANSACTION_PAGE_SIZE);
        assertErrorCode(
                () -> cardService.getCardTransactions(
                        1L, 10L, searchRequest(null, 101, null)),
                CardErrorCode.INVALID_TRANSACTION_PAGE_SIZE);
    }

    @Test
    void size보다_한건_더_조회되면_마지막_응답거래로_다음커서를_만든다() {
        given(cardMapper.findTransactionCardInfo(1L, 10L))
                .willReturn(transactionCard(CardType.CREDIT, new BigDecimal("89800.00")));
        List<CardTransactionItemResponse> fetched = List.of(
                transaction(103L, LocalDateTime.of(2026, 7, 23, 12, 0)),
                transaction(102L, LocalDateTime.of(2026, 7, 22, 11, 0)),
                transaction(101L, LocalDateTime.of(2026, 7, 21, 10, 0)));
        given(cardMapper.findTransactions(eq(1L), eq(10L), any()))
                .willReturn(fetched, List.of());

        CardTransactionDetailResponse firstResponse =
                cardService.getCardTransactions(1L, 10L, searchRequest(null, 2, null));

        assertThat(firstResponse.getTransactions().getContent())
                .extracting(CardTransactionItemResponse::getTransactionId)
                .containsExactly(103L, 102L);
        assertThat(firstResponse.getTransactions().getSize()).isEqualTo(2);
        assertThat(firstResponse.getTransactions().getHasNext()).isTrue();
        assertThat(firstResponse.getTransactions().getNextCursor()).isNotBlank();

        cardService.getCardTransactions(
                1L, 10L,
                searchRequest(null, 2, firstResponse.getTransactions().getNextCursor()));

        ArgumentCaptor<CardTransactionSearchCondition> captor =
                ArgumentCaptor.forClass(CardTransactionSearchCondition.class);
        then(cardMapper).should(org.mockito.Mockito.times(2))
                .findTransactions(eq(1L), eq(10L), captor.capture());
        CardTransactionSearchCondition secondCondition = captor.getAllValues().get(1);
        assertThat(secondCondition.getCursorPaidAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 22, 11, 0));
        assertThat(secondCondition.getCursorTransactionId()).isEqualTo(102L);
    }

    @Test
    void 다른_카드나_조회월의_커서는_허용하지_않는다() {
        given(cardMapper.findTransactionCardInfo(1L, 10L))
                .willReturn(transactionCard(CardType.CREDIT, BigDecimal.ZERO));
        String otherCardCursor = encodeCursor("11|2026-07|2026-07-22T11:00:00|102");
        String otherMonthCursor = encodeCursor("10|2026-06|2026-06-22T11:00:00|102");

        assertErrorCode(
                () -> cardService.getCardTransactions(
                        1L, 10L, searchRequest(null, 20, otherCardCursor)),
                CardErrorCode.INVALID_TRANSACTION_CURSOR);
        assertErrorCode(
                () -> cardService.getCardTransactions(
                        1L, 10L, searchRequest(null, 20, otherMonthCursor)),
                CardErrorCode.INVALID_TRANSACTION_CURSOR);
    }

    @Test
    void 처음_등록하는_카드는_INSERT하고_표시순서는_마지막_다음이_된다() {
        CardRegisterRequest request = registerRequest();
        given(cardMapper.findDeletedFlag(1L, 47L)).willReturn(null);
        given(cardMapper.findMaxDisplayOrder(1L)).willReturn(5);

        cardService.register(1L, request);

        then(cardMapper).should().insertUserCard(1L, request, 6);
        then(cardMapper).should(never()).reactivateUserCard(any(), any(), anyInt());
    }

    @Test
    void 이미_사용중인_카드를_또_등록하면_CARD_ALREADY_REGISTERED_예외를_던진다() {
        given(cardMapper.findDeletedFlag(1L, 47L)).willReturn(false);

        assertThatThrownBy(() -> cardService.register(1L, registerRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CardErrorCode.CARD_ALREADY_REGISTERED);

        then(cardMapper).should(never()).insertUserCard(any(), any(), anyInt());
    }

    @Test
    void 삭제했던_카드를_다시_등록하면_INSERT가_아니라_재활성화한다() {
        CardRegisterRequest request = registerRequest();
        given(cardMapper.findDeletedFlag(1L, 47L)).willReturn(true);
        given(cardMapper.findMaxDisplayOrder(1L)).willReturn(2);

        cardService.register(1L, request);

        then(cardMapper).should().reactivateUserCard(1L, request, 3);
        then(cardMapper).should(never()).insertUserCard(any(), any(), anyInt());
    }

    @Test
    void 이용실적조회시_카드와_금액과_혜택규칙을_조합해_응답한다() {
        CardUsageSearchRequest request = new CardUsageSearchRequest();
        given(cardMapper.findUsageCardInfo(1L, 5L)).willReturn(CardUsageCardInfo.builder()
                .cardProductId(43L).cardName("KB국민 노리 체크카드")
                .issuerName("KB국민카드").cardType(CardType.DEBIT).build());
        given(cardMapper.findUsageAmounts(eq(1L), eq(5L), any(CardUsagePeriodCondition.class)))
                .willReturn(CardUsageAmountSummary.builder()
                        .recognizedAmount(new BigDecimal("100000"))
                        .excludedAmount(new BigDecimal("20000")).build());
        given(cardMapper.findUsageBenefitRules(43L)).willReturn(List.of(
                CardUsageBenefitRule.builder()
                        .benefitId(124L).benefitName("전 가맹점 할인")
                        .benefitType(BenefitType.CASHBACK).valueType(ValueType.RATE)
                        .valueNumber(new BigDecimal("0.50"))
                        .benefitMinimumAmount(BigDecimal.ZERO).build()));

        CardUsageDetailResponse response = cardService.getCardUsage(1L, 5L, request);

        assertThat(response.getCard().getCardName()).isEqualTo("KB국민 노리 체크카드");
        assertThat(response.getCard().getIssuerName()).isEqualTo("KB국민카드");
        assertThat(response.getYearMonth()).isEqualTo("2026-07");
        assertThat(response.getUsageSummary().getRecognizedAmount()).isEqualByComparingTo("100000");
        assertThat(response.getUsageSummary().getExcludedAmount()).isEqualByComparingTo("20000");
        assertThat(response.getTiers()).isEmpty();
        assertThat(response.getDefaultBenefits()).hasSize(1);
        assertThat(response.getDefaultBenefits().get(0).getValueLabel()).isEqualTo("0.5%");
    }

    @Test
    void 이용실적조회는_카드유형에_맞춘_공통월범위를_금액집계에_전달한다() {
        given(cardMapper.findUsageCardInfo(1L, 5L)).willReturn(CardUsageCardInfo.builder()
                .cardProductId(43L).cardType(CardType.DEBIT).build());
        given(cardMapper.findUsageAmounts(eq(1L), eq(5L), any(CardUsagePeriodCondition.class)))
                .willReturn(CardUsageAmountSummary.builder()
                        .recognizedAmount(BigDecimal.ZERO).excludedAmount(BigDecimal.ZERO).build());
        given(cardMapper.findUsageBenefitRules(43L)).willReturn(List.of());

        cardService.getCardUsage(1L, 5L, new CardUsageSearchRequest());

        ArgumentCaptor<CardUsagePeriodCondition> captor =
                ArgumentCaptor.forClass(CardUsagePeriodCondition.class);
        then(cardMapper).should().findUsageAmounts(eq(1L), eq(5L), captor.capture());
        assertThat(captor.getValue().getStartAt()).isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
        assertThat(captor.getValue().getEndAt()).isEqualTo(LocalDateTime.of(2026, 7, 25, 0, 0));
    }

    @Test
    void 이용실적조회시_사용자카드가_없으면_예외를_던진다() {
        given(cardMapper.findUsageCardInfo(1L, 999L)).willReturn(null);

        assertThatThrownBy(() -> cardService.getCardUsage(1L, 999L, new CardUsageSearchRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CardErrorCode.CARD_NOT_FOUND);
        then(cardMapper).should(never()).findUsageAmounts(any(), any(), any());
        then(cardMapper).should(never()).findUsageBenefitRules(any());
    }

    private CardRegisterRequest registerRequest() {
        CardRegisterRequest request = new CardRegisterRequest();
        ReflectionTestUtils.setField(request, "cardProductId", 47L);
        ReflectionTestUtils.setField(request, "first4", "5327");
        ReflectionTestUtils.setField(request, "last4", "8014");
        ReflectionTestUtils.setField(request, "expiryDate", LocalDate.of(2030, 1, 31));
        return request;
    }

    private CardTransactionSearchRequest searchRequest(String yearMonth, Integer size, String cursor) {
        CardTransactionSearchRequest request = new CardTransactionSearchRequest();
        ReflectionTestUtils.setField(request, "yearMonth", yearMonth);
        ReflectionTestUtils.setField(request, "size", size);
        ReflectionTestUtils.setField(request, "cursor", cursor);
        return request;
    }

    private CardTransactionCardInfo transactionCard(CardType cardType, BigDecimal scheduledAmount) {
        return CardTransactionCardInfo.builder()
                .cardId(10L)
                .cardProductId(20L)
                .cardName("테스트 카드")
                .issuerName("테스트 카드사")
                .cardImageUrl("https://example.com/card.png")
                .cardType(cardType)
                .maskedRearNumber("1234")
                .scheduledPaymentAmount(scheduledAmount)
                .build();
    }

    private CardTransactionItemResponse transaction(Long transactionId, LocalDateTime paidAt) {
        return CardTransactionItemResponse.builder()
                .transactionId(transactionId)
                .paymentAmount(BigDecimal.TEN)
                .paidAt(paidAt)
                .performanceIncluded(true)
                .build();
    }

    private String encodeCursor(String rawCursor) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(rawCursor.getBytes(StandardCharsets.UTF_8));
    }

    private void assertErrorCode(Runnable invocation, CardErrorCode errorCode) {
        assertThatThrownBy(invocation::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }

    @Test
    void 보유_카드가_전혀_없으면_Provider_카드와_거래를_모두_등록한다() {
        MyDataCard card1 = myDataCard(47L, List.of(transaction(15_000, 1L)));
        MyDataCard card2 = myDataCard(15L, List.of(transaction(23_000, 2L)));
        given(myDataProvider.fetchCards(1L)).willReturn(List.of(card1, card2));
        given(cardMapper.findMaxDisplayOrder(1L)).willReturn(0);
        given(cardMapper.findDeletedFlag(1L, 47L)).willReturn(null);
        given(cardMapper.findDeletedFlag(1L, 15L)).willReturn(null);
        willAnswer(fillGeneratedKey()).given(cardMapper)
                .insertMyDataCard(eq(1L), any(MyDataCard.class), anyInt(), anyMap());

        cardService.connectMyData(1L);

        then(cardMapper).should().insertMyDataCard(eq(1L), eq(card1), eq(1), anyMap());
        then(cardMapper).should().insertMyDataCard(eq(1L), eq(card2), eq(2), anyMap());
        then(cardMapper).should().insertMyDataTransactions(eq(47_000L), eq(card1.getTransactions()));
        then(cardMapper).should().insertMyDataTransactions(eq(15_000L), eq(card2.getTransactions()));
    }

    @Test
    void 소프트_삭제된_카드는_건너뛴다() {
        MyDataCard card = myDataCard(47L, List.of());
        given(myDataProvider.fetchCards(1L)).willReturn(List.of(card));
        given(cardMapper.findMaxDisplayOrder(1L)).willReturn(5);
        given(cardMapper.findDeletedFlag(1L, 47L)).willReturn(true);

        cardService.connectMyData(1L);

        then(cardMapper).should(never()).insertMyDataCard(any(), any(), anyInt(), anyMap());
        then(cardMapper).should(never()).reactivateUserCard(any(), any(), anyInt());
        then(cardMapper).should(never()).insertMyDataTransactions(any(), any());
    }

    @Test
    void 신규_카드만_카드와_거래를_함께_등록한다() {
        MyDataCard existing = myDataCard(47L, List.of(transaction(1_000, 1L)));
        MyDataCard fresh = myDataCard(15L, List.of(transaction(2_000, 2L)));
        given(myDataProvider.fetchCards(1L)).willReturn(List.of(existing, fresh));
        given(cardMapper.findMaxDisplayOrder(1L)).willReturn(5);
        given(cardMapper.findDeletedFlag(1L, 47L)).willReturn(false);
        given(cardMapper.findDeletedFlag(1L, 15L)).willReturn(null);
        willAnswer(fillGeneratedKey()).given(cardMapper)
                .insertMyDataCard(eq(1L), any(MyDataCard.class), anyInt(), anyMap());

        cardService.connectMyData(1L);

        then(cardMapper).should(never()).insertMyDataCard(eq(1L), eq(existing), anyInt(), anyMap());
        then(cardMapper).should().insertMyDataCard(eq(1L), eq(fresh), eq(6), anyMap());
        then(cardMapper).should().insertMyDataTransactions(eq(15_000L), eq(fresh.getTransactions()));
        then(cardMapper).should(never()).insertMyDataTransactions(eq(47_000L), any());
    }

    /** MyBatis useGeneratedKeys가 INSERT 후 keyHolder에 PK를 채우는 것을 흉내낸다. */
    private Answer<Void> fillGeneratedKey() {
        return invocation -> {
            MyDataCard card = invocation.getArgument(1);
            Map<String, Object> keyHolder = invocation.getArgument(3);
            keyHolder.put("userCardId", card.getCardProductId() * 1000);
            return null;
        };
    }

    private MyDataCard myDataCard(Long cardProductId, List<MyDataTransaction> transactions) {
        return MyDataCard.builder()
                .cardProductId(cardProductId)
                .first4("1234")
                .last4("5678")
                .expiryDate(LocalDate.of(2030, 1, 31))
                .creditLimit(BigDecimal.valueOf(1_000_000))
                .scheduledPaymentAmount(BigDecimal.valueOf(50_000))
                .transactions(transactions)
                .build();
    }

    private MyDataTransaction transaction(long amount, Long storeId) {
        return MyDataTransaction.builder()
                .amount(BigDecimal.valueOf(amount))
                .storeId(storeId)
                .paidAt(LocalDateTime.of(2026, 7, 1, 12, 0))
                .build();
    }
}
