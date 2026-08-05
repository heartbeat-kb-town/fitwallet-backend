package com.fitwallet.domain.benefit.controller;

import com.fitwallet.domain.benefit.dto.BenefitSuccessCode;
import com.fitwallet.domain.benefit.dto.response.ExpectedBenefitResponse;
import com.fitwallet.domain.benefit.service.BenefitService;
import com.fitwallet.global.common.annotation.LoginUserId;
import com.fitwallet.global.common.dto.ApiResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
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
@Api(tags = "혜택")
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
    @ApiOperation(value = "예상 혜택 조회(카드별)", notes = """
            가맹점 하나를 기준으로 로그인 사용자의 **보유 카드 전부**에 대해 예상 혜택을 판정한다.
            홈 화면의 "내 카드별 혜택" 목록이 이 응답 하나로 그려진다.

            - `cards`는 `status` 기준으로 정렬돼 내려간다 — `AVAILABLE` → `CONDITION_NOT_MET` → `NO_BENEFIT`.
            - **보유 카드가 없으면 `hasCard=false`이고 `cards`는 빈 배열이다.** 빈 상태 화면은 이 값으로 분기한다.
              (카드가 없어도 200이고, `store`는 정상적으로 채워진다.)
            - 카드 한 장의 `reason`·`benefit`은 `status`에 따라 아래처럼 채워지거나 `null`이 된다.

            | status | reason.code | reason | benefit |
            |---|---|---|---|
            | `AVAILABLE` | — | `null` | 적용될 혜택 |
            | `CONDITION_NOT_MET` | `PREV_SPEND_NOT_MET` | 있음 | `null` |
            | `CONDITION_NOT_MET` | `LIMIT_EXHAUSTED` | 있음 | 한도가 소진된 그 혜택 |
            | `NO_BENEFIT` | `NO_BENEFIT_FOR_STORE` | 있음 | `null` |

            `storeId`는 숫자지만 **문자열로 받는다.** 값이 없을 때와 숫자가 아닐 때를 하나의 코드
            (`STORE_ID_REQUIRED`)로 응답해야 하는데, `Long`으로 받으면 Spring이 두 경우를 서로 다른
            예외로 갈라 던져 그 코드를 낼 수 없기 때문이다. 프론트는 그냥 숫자를 문자열로 보내면 된다.

            | HTTP | code | message |
            |---|---|---|
            | 400 | STORE_ID_REQUIRED | storeId를 전달해 주세요. |
            | 404 | STORE_NOT_FOUND | 존재하지 않는 가맹점입니다. |
            """)
    @GetMapping("/benefit/expected")
    public ResponseEntity<ApiResponse<ExpectedBenefitResponse>> findExpectedBenefits(
            @LoginUserId Long userId,
            @ApiParam(value = "가맹점 ID(숫자 문자열). 누락되거나 숫자가 아니면 400 STORE_ID_REQUIRED",
                    example = "1", required = true)
            @RequestParam(required = false) String storeId) {

        return ApiResponse.of(BenefitSuccessCode.EXPECTED_BENEFIT_FOUND,
                benefitService.findExpectedBenefits(userId, storeId));
    }
}
