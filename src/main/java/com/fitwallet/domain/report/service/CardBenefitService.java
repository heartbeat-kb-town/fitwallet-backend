package com.fitwallet.domain.report.service;

import com.fitwallet.domain.report.dto.response.CardBenefitDetailResponse;

public interface CardBenefitService {
    CardBenefitDetailResponse getCardBenefitDetail(Long userId, Long userCardId, String yearMonth);
}