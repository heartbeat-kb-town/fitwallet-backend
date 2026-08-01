package com.fitwallet.domain.benefit.controller;

import com.fitwallet.domain.benefit.dto.BenefitSuccessCode;
import com.fitwallet.domain.benefit.dto.response.ExpectedBenefitResponse;
import com.fitwallet.domain.benefit.service.BenefitService;
import com.fitwallet.global.common.annotation.LoginUserId;
import com.fitwallet.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 혜택 도메인 컨트롤러. 사용자 식별자는 {@link LoginUserId}로만 받는다.
 * 에러 응답은 여기서 만들지 않는다 — {@code GlobalExceptionHandler}가 변환한다.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BenefitController {

    private final BenefitService benefitService;

    /**
     * {@code storeId}를 {@code Long}이 아니라 {@code String}으로 받는 건 의도된 설계다.
     * 명세가 "누락"과 "숫자가 아님"을 하나의 코드({@code STORE_ID_REQUIRED})로 응답하도록
     * 정했는데, {@code Long}으로 받으면 Spring이 두 경우를 서로 다른 예외
     * ({@code MissingServletRequestParameterException} /
     * {@code MethodArgumentTypeMismatchException})로 갈라 던져 도메인 메시지를 낼 수 없다.
     * {@code String}이면 두 경우 모두 평범한 값({@code null} 또는 {@code "abc"})으로 들어오고,
     * 파싱·검증은 전부 {@code DefaultBenefitService.resolveStoreId()}가 한다.
     * <p>
     * 즉 {@code Long}으로 '정리'하면 에러 응답 스펙이 깨진다.
     */
    @GetMapping("/benefit/expected")
    public ResponseEntity<ApiResponse<ExpectedBenefitResponse>> findExpectedBenefits(
            @LoginUserId Long userId,
            @RequestParam(required = false) String storeId) {

        return ApiResponse.of(BenefitSuccessCode.EXPECTED_BENEFIT_FOUND,
                benefitService.findExpectedBenefits(userId, storeId));
    }
}
