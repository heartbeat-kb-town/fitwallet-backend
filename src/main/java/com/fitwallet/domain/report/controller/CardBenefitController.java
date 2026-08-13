package com.fitwallet.domain.report.controller;

import com.fitwallet.domain.report.dto.CardBenefitSuccessCode;
import com.fitwallet.domain.report.dto.response.CardBenefitDetailResponse;
import com.fitwallet.domain.report.service.CardBenefitService;
import com.fitwallet.global.common.annotation.LoginUserId;
import com.fitwallet.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/report/benefit/received/cards")
@RequiredArgsConstructor
public class CardBenefitController {

    private final CardBenefitService cardBenefitService;

    @GetMapping("/{userCardId}")
    public ResponseEntity<ApiResponse<CardBenefitDetailResponse>> getCardBenefitDetail(
            @LoginUserId Long userId,
            @PathVariable Long userCardId,
            @RequestParam String yearMonth
    ) {
        CardBenefitDetailResponse data = cardBenefitService.getCardBenefitDetail(userId, userCardId, yearMonth);
        return ApiResponse.of(CardBenefitSuccessCode.CARD_BENEFIT_DETAIL_FOUND, data);
    }
}