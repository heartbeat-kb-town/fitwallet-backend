package com.fitwallet.domain.report.mapper;

import com.fitwallet.domain.report.dto.response.CardRecommendationRawResponse;
import com.fitwallet.domain.report.dto.response.MonthlyCategorySpendRawResponse;
import com.fitwallet.domain.report.dto.response.PopularCardRawResponse;
import com.fitwallet.domain.report.dto.response.ReceivedBenefitSummaryResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BenefitReportMapper {

    /** 받은 혜택 요약(총 받은 혜택·총 할인 금액·총 포인트)을 한 번의 집계로 조회한다. */
    ReceivedBenefitSummaryResponse getReceivedBenefitSummary(
            @Param("userId") Long userId,
            @Param("yearMonth") String yearMonth
    );

    /**
     * 최근 N개월([fromYearMonth, toYearMonth])의 카테고리 × 월별 지출을 조회한다.
     * 추천 엔진이 카테고리별 월 지출들의 중앙값으로 예상 월 지출을 만든다.
     */
    List<MonthlyCategorySpendRawResponse> getMonthlyCategorySpends(
            @Param("userId") Long userId,
            @Param("fromYearMonth") String fromYearMonth,
            @Param("toYearMonth") String toYearMonth
    );

    List<CardRecommendationRawResponse> getRecommendedCards(
            @Param("userId") Long userId,
            @Param("categoryIds") List<Long> categoryIds
    );

    /** 콜드스타트 폴백: 보유 수 상위의 미보유 카드. */
    List<PopularCardRawResponse> getPopularUnownedCards(
            @Param("userId") Long userId,
            @Param("limit") int limit
    );
}
