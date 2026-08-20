package com.fitwallet.domain.card.mapper;

import com.fitwallet.domain.card.dto.CardTransactionCardInfo;
import com.fitwallet.domain.card.dto.CardListSortType;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitBrandTarget;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitCategoryTarget;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitRule;
import com.fitwallet.domain.card.dto.CardMonthlyBenefitTargetUsage;
import com.fitwallet.domain.card.dto.CardSummaryCardInfo;
import com.fitwallet.domain.card.dto.CardUsageAmountSummary;
import com.fitwallet.domain.card.dto.CardUsageBenefitRule;
import com.fitwallet.domain.card.dto.CardUsageCardInfo;
import com.fitwallet.domain.card.dto.MyDataCard;
import com.fitwallet.domain.card.dto.MyDataTransaction;
import com.fitwallet.domain.card.dto.request.CardRegisterRequest;
import com.fitwallet.domain.card.dto.request.CardRecentTransactionSearchCondition;
import com.fitwallet.domain.card.dto.request.CardTransactionSearchCondition;
import com.fitwallet.domain.card.dto.request.CardUsagePeriodCondition;
import com.fitwallet.domain.card.dto.response.CardEventItemResponse;
import com.fitwallet.domain.card.dto.response.CardListResponse;
import com.fitwallet.domain.card.dto.response.CardSummaryTransactionResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionItemResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 카드 도메인 조회.
 * <p>
 * 도메인은 테이블을 소유하지 않는다. 이 매퍼는 {@code user_card}, {@code card_product},
 * {@code issuer}를 조인해 화면이 필요한 모양으로 바로 반환한다.
 * SQL은 {@code resources/mapper/card/CardMapper.xml}에 있고,
 * {@code <select>}의 id는 여기 메서드명과 같아야 한다.
 */
@Mapper
public interface CardMapper {

    /** 로그인 사용자가 보유한 카드의 내 카드 탭 요약용 내부 정보를 조회한다. */
    CardSummaryCardInfo findSummaryCardInfo(@Param("userId") Long userId,
                                            @Param("cardId") Long cardId);

    /** 로그인 사용자의 보유 카드에 적용되는 현재 진행 중 이벤트를 정렬된 순서로 조회한다. */
    List<CardEventItemResponse> findCardEventItems(@Param("userId") Long userId,
                                                   @Param("cardId") Long cardId,
                                                   @Param("today") LocalDate today);

    /** KST 기준 오늘과 어제의 결제 내역을 최신순으로 조회한다. */
    List<CardSummaryTransactionResponse> findRecentTransactions(
            @Param("userId") Long userId,
            @Param("cardId") Long cardId,
            @Param("condition") CardRecentTransactionSearchCondition condition);

    /**
     * 로그인 사용자가 보유한 카드의 결제 내역 조회용 내부 정보를 조회한다.
     * 카드가 없거나 삭제됐거나 다른 사용자의 카드이면 null을 반환한다.
     */
    CardTransactionCardInfo findTransactionCardInfo(@Param("userId") Long userId,
                                                    @Param("cardId") Long cardId);

    /** 로그인 사용자가 보유한 카드에 저장된 가장 오래된 승인·승인취소 거래 시각을 조회한다. */
    LocalDateTime findOldestTransactionPaidAt(@Param("userId") Long userId,
                                              @Param("cardId") Long cardId);

    /**
     * 카드 유형별 조회 기간에 발생한 결제 완료 건의 금액을 합산한다.
     * 결제 내역이 없으면 0을 반환한다.
     */
    BigDecimal sumTransactionAmount(@Param("userId") Long userId,
                                    @Param("cardId") Long cardId,
                                    @Param("condition") CardTransactionSearchCondition condition);

    /** 조회 기간의 승인 거래에서 할인만 반영된 실제 청구액을 합산한다. */
    BigDecimal sumScheduledPaymentAmount(
            @Param("userId") Long userId,
            @Param("cardId") Long cardId,
            @Param("condition") CardTransactionSearchCondition condition);

    /** 카드 유형별 조회 기간의 결제 완료 내역을 커서 기준 최신순으로 조회한다. */
    List<CardTransactionItemResponse> findTransactions(
            @Param("userId") Long userId,
            @Param("cardId") Long cardId,
            @Param("condition") CardTransactionSearchCondition condition);

    /** 로그인 사용자가 보유한 카드의 이용 실적 조회용 내부 정보를 조회한다. */
    CardUsageCardInfo findUsageCardInfo(@Param("userId") Long userId,
                                        @Param("cardId") Long cardId);

    /** 조회 기간의 실적 인정 금액과 실적 미인정 금액을 각각 집계한다. */
    CardUsageAmountSummary findUsageAmounts(
            @Param("userId") Long userId,
            @Param("cardId") Long cardId,
            @Param("condition") CardUsagePeriodCondition condition);

    /** 카드상품에 속한 혜택과 조건부로 연결된 원본 실적 구간을 조회한다. */
    List<CardUsageBenefitRule> findUsageBenefitRules(
            @Param("cardProductId") Long cardProductId);

    /** 카드 상품의 월 한도와 해당 한도를 적용받는 혜택 서비스를 평면 행으로 조회한다. */
    List<CardMonthlyBenefitRule> findMonthlyBenefitRules(
            @Param("cardProductId") Long cardProductId);

    /** 카드 상품의 업종 범위 혜택과 대상 카테고리를 일괄 조회한다. */
    List<CardMonthlyBenefitCategoryTarget> findMonthlyBenefitCategoryTargets(
            @Param("cardProductId") Long cardProductId);

    /** 카드 상품의 브랜드 범위 혜택과 대상 브랜드를 일괄 조회한다. */
    List<CardMonthlyBenefitBrandTarget> findMonthlyBenefitBrandTargets(
            @Param("cardProductId") Long cardProductId);

    /** 조회 기간에 실제 적용된 혜택 거래를 서비스·카테고리·브랜드 조합으로 집계한다. */
    List<CardMonthlyBenefitTargetUsage> findMonthlyBenefitTargetUsages(
            @Param("userId") Long userId,
            @Param("cardId") Long cardId,
            @Param("condition") CardUsagePeriodCondition condition);

    /** 사용자의 카드 목록을 요청한 기준으로 조회한다. 삭제된 카드는 제외된다. */
    List<CardListResponse> findByUserId(@Param("userId") Long userId,
                                        @Param("sortType") CardListSortType sortType,
                                        @Param("condition") CardTransactionSearchCondition condition);

    /** 사용자의 카드 한 건. 없거나 삭제됐으면 null. */
    CardListResponse findByUserIdAndUserCardId(@Param("userId") Long userId,
                                               @Param("userCardId") Long userCardId,
                                               @Param("condition") CardTransactionSearchCondition condition);

    /** 등록 직후 응답을 만들기 위한 조회. */
    CardListResponse findByUserIdAndCardProductId(@Param("userId") Long userId,
                                                  @Param("cardProductId") Long cardProductId,
                                                  @Param("condition") CardTransactionSearchCondition condition);

    /**
     * 등록 이력 여부. 행이 없으면 {@code null}, 사용 중이면 {@code false}, 소프트 삭제 상태면 {@code true}.
     * <p>
     * {@code (user_id, card_product_id)}에 UNIQUE가 걸려 있어 재등록은 INSERT가 아니라 재활성화다.
     * 그래서 "없음"과 "삭제됨"을 구분해야 한다.
     */
    Boolean findDeletedFlag(@Param("userId") Long userId,
                            @Param("cardProductId") Long cardProductId);

    /** 살아 있는 카드 중 가장 큰 표시순서. 없으면 0. */
    int findMaxDisplayOrder(@Param("userId") Long userId);

    void insertUserCard(@Param("userId") Long userId,
                        @Param("request") CardRegisterRequest request,
                        @Param("displayOrder") int displayOrder);

    /** 소프트 삭제된 카드를 다시 살린다. */
    void reactivateUserCard(@Param("userId") Long userId,
                            @Param("request") CardRegisterRequest request,
                            @Param("displayOrder") int displayOrder);

    /**
     * 마이데이터로 받아온 카드를 등록한다.
     * <p>
     * 생성된 PK를 그 객체에 채워 넣는 대신, XML의 {@code useGeneratedKeys}가
     * {@code keyHolder} Map에 {@code userCardId}를 채운다.
     */
    void insertMyDataCard(@Param("userId") Long userId,
                          @Param("card") MyDataCard card,
                          @Param("displayOrder") int displayOrder,
                          @Param("keyHolder") Map<String, Object> keyHolder);

    /** 마이데이터로 받아온 거래내역을 한 카드에 일괄 저장한다. */
    void insertMyDataTransactions(@Param("userCardId") Long userCardId,
                                  @Param("transactions") List<MyDataTransaction> transactions);

    /** 살아 있는 보유 카드의 ID만 조회한다. 순서 변경 요청이 보유 카드 전체와 일치하는지 검증할 때 쓴다. */
    List<Long> findUserCardIds(@Param("userId") Long userId);

    /**
     * 표시 순서를 일괄 갱신한다. 리스트의 인덱스(0-based)가 곧 새 {@code display_order}(1-based)다.
     * 반환값은 영향받은 행 수 — 보통 {@code userCardIds} 크기와 같다.
     */
    int updateCardsDisplayOrder(@Param("userId") Long userId,
                                @Param("userCardIds") List<Long> userCardIds);
}
