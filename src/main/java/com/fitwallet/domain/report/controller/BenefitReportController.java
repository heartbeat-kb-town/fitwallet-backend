package com.fitwallet.domain.report.controller;

import com.fitwallet.domain.report.dto.ReportSuccessCode;
import com.fitwallet.domain.report.dto.response.BenefitSummaryResponse;
import com.fitwallet.domain.report.service.BenefitReportService;
import com.fitwallet.global.common.annotation.LoginUserId;
import com.fitwallet.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/report/benefit")
@RequiredArgsConstructor
public class BenefitReportController {

    private final BenefitReportService benefitReportService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<BenefitSummaryResponse>> getSummary(
            @LoginUserId Long userId,
            @RequestParam String yearMonth
    ) {
        BenefitSummaryResponse data = benefitReportService.getBenefitSummary(userId, yearMonth);
        return ApiResponse.of(ReportSuccessCode.BENEFIT_SUMMARY_FOUND, data);
    }
}