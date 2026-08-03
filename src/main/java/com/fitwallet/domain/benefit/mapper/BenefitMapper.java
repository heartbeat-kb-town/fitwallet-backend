package com.fitwallet.domain.benefit.mapper;

import com.fitwallet.domain.benefit.dto.response.BenefitCandidateResponse;
import com.fitwallet.domain.benefit.dto.response.BenefitLimitResponse;
import com.fitwallet.domain.benefit.dto.response.BenefitPrevMonthSpendResponse;
import com.fitwallet.domain.benefit.dto.response.BenefitStoreResponse;
import com.fitwallet.domain.benefit.dto.response.BenefitUsageResponse;
import com.fitwallet.domain.benefit.dto.response.BenefitUserCardResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 예상 혜택 판정용 원시 조회. 도메인은 테이블을 소유하지 않는다.
 * <p>
 * 이 매퍼가 반환하는 DTO는 최종 API 응답 모양이 아니다 — 카드 하나가 최종적으로
 * {@code AVAILABLE}/{@code CONDITION_NOT_MET}/{@code NO_BENEFIT} 중 무엇인지는 이 쿼리들의
 * 결과를 서비스가 조합해야 알 수 있다. 그래서 모든 반환 DTO에 {@code Benefit} 접두사를 붙여
 * 최종 응답 DTO({@code ExpectedBenefitResponse})와 구분한다.
 * <p>
 * SQL은 {@code resources/mapper/benefit/BenefitMapper.xml}에 있고,
 * {@code <select>}의 id는 여기 메서드명과 같아야 한다.
 */
@Mapper
public interface BenefitMapper {

    /** 없으면 {@code null} — 서비스가 404({@code STORE_NOT_FOUND})로 변환한다. */
    BenefitStoreResponse findStore(@Param("storeId") Long storeId);

    /** 사용자의 살아 있는 카드 전체를 {@code display_order}, {@code user_card_id} 순으로 조회한다. */
    List<BenefitUserCardResponse> findUserCards(@Param("userId") Long userId);

    /**
     * 카드별 전월실적. IN 절 한 번으로 카드 수만큼의 쿼리 왕복을 피한다.
     * 지난달 거래가 없는 카드는 결과에 아예 없다 — 서비스가 0으로 채운다.
     */
    List<BenefitPrevMonthSpendResponse> findPrevMonthSpends(@Param("userCardIds") List<Long> userCardIds);

    /**
     * 가맹점 스코프(브랜드·업종)에 매칭되는 후보 혜택. 스코프 매칭은 하드 필터(WHERE)지만
     * 전월실적 구간 통과 여부는 {@code tierOk} 플래그로 남긴다 — 실적이 모자란 혜택도
     * 결과에서 빼지 않아야 {@code PREV_SPEND_NOT_MET}을 판정할 수 있다.
     */
    List<BenefitCandidateResponse> findCandidates(@Param("cardProductId") Long cardProductId,
                                                   @Param("prevMonthSpend") BigDecimal prevMonthSpend,
                                                   @Param("storeBrandId") Long storeBrandId,
                                                   @Param("storeCategoryId") Long storeCategoryId);

    /**
     * {@code planGroupId}가 있으면 그것으로, 없으면 {@code serviceId} 직결로 tier를 찾아
     * 그 tier에 걸린 한도 행 전부를 반환한다({@code ck_benefit_tier_xor}).
     */
    List<BenefitLimitResponse> findLimits(@Param("planGroupId") Long planGroupId,
                                          @Param("serviceId") Long serviceId,
                                          @Param("prevMonthSpend") BigDecimal prevMonthSpend);

    /**
     * {@code periodStart} 이후 소진량. 매칭되는 결제가 없어도 {@code usedAmount=0},
     * {@code usedCount=0}인 행을 돌려준다({@code COALESCE}/{@code COUNT}라 절대 null이 아니다).
     */
    BenefitUsageResponse findUsage(@Param("userCardId") Long userCardId,
                                   @Param("tierId") Long tierId,
                                   @Param("periodStart") LocalDateTime periodStart);
}
