package com.fitwallet.domain.report.controller;

import com.fitwallet.domain.report.dto.LossType;
import com.fitwallet.domain.report.dto.MissedBenefitSuccessCode;
import com.fitwallet.domain.report.dto.response.MissedCategoryDetailResponse;
import com.fitwallet.domain.report.service.MissedBenefitService;
import com.fitwallet.global.common.annotation.LoginUserId;
import com.fitwallet.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/report/benefit/missed")
@RequiredArgsConstructor
public class MissedBenefitController {

    private final MissedBenefitService missedBenefitService;

    @GetMapping
    public ResponseEntity<ApiResponse<MissedCategoryDetailResponse>> getMissedBenefitDetail(
            @LoginUserId Long userId,
            @RequestParam String yearMonth,
            @RequestParam LossType lossType
    ) {
        MissedCategoryDetailResponse data =
                missedBenefitService.getMissedBenefitDetail(userId, yearMonth, lossType);
        return ApiResponse.of(MissedBenefitSuccessCode.MISSED_BENEFIT_DETAIL_FOUND, data);
    }
}
