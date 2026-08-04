package com.fitwallet.domain.report.service;

import com.fitwallet.domain.report.dto.response.BenefitSummaryResponse;

public interface BenefitReportService {
     BenefitSummaryResponse getBenefitSummary(Long userId, String yearMonth);
}
