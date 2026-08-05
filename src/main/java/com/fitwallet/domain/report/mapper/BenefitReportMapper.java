package com.fitwallet.domain.report.mapper;

import com.fitwallet.domain.report.dto.response.CardRecommendationRawResponse;
import com.fitwallet.domain.report.dto.response.CategoryBenefitResponse;
import com.fitwallet.domain.report.dto.response.CategorySpendResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface BenefitReportMapper {
    BigDecimal getTotalReceivedBenefit(@Param("userId") Long userId, @Param("yearMonth") String yearMonth);
    BigDecimal getTotalMissedBenefit(@Param("userId") Long userId, @Param("yearMonth") String yearMonth);
    List<CategoryBenefitResponse> getCategoryBenefits(@Param("userId") Long userId, @Param("yearMonth") String yearMonth);

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
