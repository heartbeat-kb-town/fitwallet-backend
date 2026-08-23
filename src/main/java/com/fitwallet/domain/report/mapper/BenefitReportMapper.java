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

    /**
     * 추천 후보 <b>전체</b>. 카테고리·사용자 조건이 없다.
     * <p>
     * 조인 대상이 전부 참조 데이터라 결과가 사용자와 무관하고, Flyway로만 바뀐다(= 재기동을
     * 동반한다). 그래서 앱이 한 번만 부르고 메모리에 들고 있는다 — 종속 서브쿼리가 요청당
     * 244회에서 앱 생애 1회가 된다. 경위는 매퍼 XML 주석에 있다.
     */
    List<CardRecommendationRawResponse> getAllRecommendationCandidates();

    /** 추천에서 제외할 보유 카드상품 ID. */
    List<Long> findOwnedCardProductIds(@Param("userId") Long userId);
}
