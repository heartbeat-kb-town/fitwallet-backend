package com.fitwallet.domain.report.mapper;

import com.fitwallet.domain.report.dto.response.CardRecommendationRawResponse;
import com.fitwallet.domain.report.dto.response.CategorySpendResponse;
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

    List<CategorySpendResponse> getTopSpendingCategories(
            @Param("userId") Long userId,
            @Param("yearMonth") String yearMonth,
            @Param("limit") int limit
    );

    List<CardRecommendationRawResponse> getRecommendedCards(
            @Param("userId") Long userId,
            @Param("categoryIds") List<Long> categoryIds
    );
}
