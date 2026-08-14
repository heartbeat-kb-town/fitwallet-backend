package com.fitwallet.domain.card.dto.response;

import com.fitwallet.domain.card.dto.CardMonthlyBenefitLimitStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/** 같은 월 한도를 소비하는 선택 혜택 서비스 묶음. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardMonthlyBenefitSharedLimitGroupResponse {

    private Long limitGroupId;
    private List<CardMonthlyBenefitGroupCategoryResponse> categories;
    private CardMonthlyBenefitLimitResponse sharedMonthlyLimit;
    private CardMonthlyBenefitLimitStatus groupLimitStatus;
    private List<CardMonthlyBenefitSharedLimitUsageResponse> usageBreakdown;
    private List<CardMonthlyBenefitServiceResponse> benefitServices;
}
