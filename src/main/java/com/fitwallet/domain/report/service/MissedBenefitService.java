package com.fitwallet.domain.report.service;

import com.fitwallet.domain.report.dto.LossType;
import com.fitwallet.domain.report.dto.response.MissedCategoryDetailResponse;

public interface MissedBenefitService {
    MissedCategoryDetailResponse getMissedBenefitDetail(Long userId, String yearMonth, LossType lossType);
}
