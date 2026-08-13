package com.fitwallet.domain.report.mapper;

import com.fitwallet.domain.report.dto.response.CardSummaryResponse;
import com.fitwallet.domain.report.dto.response.CategoryTransactionRawResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CardBenefitMapper {

    CardSummaryResponse getCardSummary(
            @Param("userId") Long userId,
            @Param("userCardId") Long userCardId,
            @Param("yearMonth") String yearMonth
    );

    List<CategoryTransactionRawResponse> getCategoryTransactions(
            @Param("userId") Long userId,
            @Param("userCardId") Long userCardId,
            @Param("yearMonth") String yearMonth
    );
}