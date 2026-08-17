package com.fitwallet.domain.card.controller;

import com.fitwallet.domain.card.dto.CardSuccessCode;
import com.fitwallet.domain.card.dto.request.CardDisplayOrderUpdateRequest;
import com.fitwallet.domain.card.dto.request.CardListSearchRequest;
import com.fitwallet.domain.card.dto.request.CardRegisterRequest;
import com.fitwallet.domain.card.dto.request.CardTransactionSearchRequest;
import com.fitwallet.domain.card.dto.request.CardUsageSearchRequest;
import com.fitwallet.domain.card.dto.response.CardListResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitResponse;
import com.fitwallet.domain.card.dto.response.CardEventResponse;
import com.fitwallet.domain.card.dto.response.CardSummaryResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionDetailResponse;
import com.fitwallet.domain.card.dto.response.CardUsageDetailResponse;
import com.fitwallet.domain.card.service.CardService;
import com.fitwallet.global.common.annotation.LoginUserId;
import com.fitwallet.global.common.dto.ApiResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 카드 도메인 참조 구현. 경로와 응답 형태는 API 명세를 따른다.
 * <p>
 * 명세가 카드 도메인 안에서도 {@code /api/card}와 {@code /api/user-cards}를 함께 쓰므로
 * 클래스 레벨은 {@code /api}만 잡고 메서드마다 전체 경로를 적는다.
 * <p>
 * 사용자 식별자는 {@link LoginUserId}로만 받는다.
 * {@code @RequestParam userId}나 헤더 직접 읽기, 세션 접근은 금지한다.
 * 에러 응답은 여기서 만들지 않는다 — {@code GlobalExceptionHandler}가 변환한다.
 */
@Api(tags = "카드")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;

    /** GET 조회 DTO를 setter 없이 필드에 직접 바인딩한다. */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.initDirectFieldAccess();
    }

    @ApiOperation(value = "보유 카드 목록 조회", notes = """
            로그인 사용자의 보유 카드 목록을 조회한다.

            - `sort`를 생략하면 결제 탭에서 사용하는 `displayOrder` 순으로 반환한다.
            - `sort=RECENTLY_USED`이면 최근 승인 거래 시각 내림차순으로 반환한다.
            - 최근 거래가 없는 카드는 뒤로 보내고 동률이면 `displayOrder`, `userCardId` 순으로 정렬한다.
            - 신용카드의 `scheduledPaymentAmount`는 기간 제한 없이 전체 승인 거래의 `finalAmount`를 합산한다.
            - 보유 카드가 없으면 빈 배열을 반환한다.
            """)
    @GetMapping("/user-cards")
    public ResponseEntity<ApiResponse<List<CardListResponse>>> findMyCards(
            @LoginUserId Long userId,
            @ModelAttribute CardListSearchRequest request) {
        return ApiResponse.of(CardSuccessCode.USER_CARDS_FOUND,
                cardService.findMyCards(userId, request));
    }

    @ApiOperation(value = "내 카드 요약 조회", notes = """
            내 카드 탭에서 선택한 보유 카드의 기본 정보, 카드 유형별 금액, 최근 이용 내역과
            현재 월 이용 실적 요약을 조회한다.

            - 신용카드 결제 이용금액과 실적은 현재 월 1일부터 전날까지 반영한다.
            - 체크카드는 현재 잔액을 반환하고 실적은 오늘까지 반영한다.
            - 최근 이용 내역은 카드 유형과 관계없이 KST 기준 오늘과 어제의 승인·취소 거래다.
            - 최근 이용 내역은 `paidAt DESC`, `transactionId DESC` 순이며 접힘·펼침은 클라이언트가 처리한다.
            - 실적 조건이 없으면 `tierType`과 `performanceStatus`가 `NO_REQUIREMENT`이고 표시 값은 null이다.

            | HTTP | code | message |
            |---|---|---|
            | 404 | CARD_NOT_FOUND | 요청한 카드를 찾을 수 없습니다. |
            | 500 | INVALID_CARD_SUMMARY_DATA | 카드 요약 데이터가 올바르지 않습니다. |
            """)
    @GetMapping("/card/{cardId}/summary")
    public ResponseEntity<ApiResponse<CardSummaryResponse>> findCardSummary(
            @LoginUserId Long userId,
            @ApiParam(value = "보유 카드 ID(user_card_id)", example = "1", required = true)
            @PathVariable("cardId") Long userCardId) {

        return ApiResponse.of(CardSuccessCode.CARD_SUMMARY_FOUND,
                cardService.findCardSummary(userId, userCardId));
    }

    @GetMapping("/card/{cardId}/event")
    public ResponseEntity<ApiResponse<CardEventResponse>> findCardEvents(
            @LoginUserId Long userId,
            @PathVariable("cardId") Long cardId) {

        return ApiResponse.of(CardSuccessCode.CARD_EVENTS_FOUND,
                cardService.findCardEvents(userId, cardId));
    }

    @ApiOperation(value = "카드별 월간 혜택 현황 조회", notes = """
            로그인 사용자가 보유한 카드의 이번 달 잠재 혜택과 월 한도 사용 현황을 조회합니다.

            - `cardId`는 카드 상품 ID가 아닌 보유 카드 ID(`user_card_id`)입니다.
            - KST 기준 이번 달 1일 00:00:00부터 오늘 00:00:00 직전까지 집계합니다.
            - `transactionCount`는 카테고리·브랜드 전체 거래가 아니라 현재 혜택 서비스가 실제 적용된 승인 거래 건수입니다.
            - 카테고리·브랜드 혜택은 한 번에 반환하며 소진된 혜택은 각 배열의 하단에 정렬합니다.
            - 적용 가능한 월 한도 혜택이 없으면 빈 배열을 반환합니다.

            | HTTP | code | message |
            |---|---|---|
            | 404 | CARD_NOT_FOUND | 요청한 카드를 찾을 수 없습니다. |
            | 500 | INVALID_CARD_MONTHLY_BENEFIT_DATA | 카드 월간 혜택 데이터가 올바르지 않습니다. |
            """)
    @GetMapping("/card/{cardId}/benefit")
    public ResponseEntity<ApiResponse<CardMonthlyBenefitResponse>> getCardMonthlyBenefit(
            @LoginUserId Long userId,
            @ApiParam(value = "보유 카드 ID(user_card_id)", required = true)
            @PathVariable Long cardId) {
        return ApiResponse.of(CardSuccessCode.CARD_MONTHLY_BENEFIT_FOUND,
                cardService.getCardMonthlyBenefit(userId, cardId));
    }

    @ApiOperation(value = "카드별 세부 결제 내역 조회", notes = """
            로그인 사용자가 보유한 카드의 월별 결제 내역을 커서 방식으로 조회한다.

            - `yearMonth`를 생략하면 현재 월을 조회하며, 미래 월을 제외한 모든 과거 월을 조회할 수 있다.
            - `availableYearMonths`는 현재 월부터 최초 거래 월까지 빈 월을 포함해 최신순으로 반환한다.
            - 정렬 기준은 `paidAt DESC`, `transactionId DESC`이며 `nextCursor`로 다음 내역을 조회한다.
            - 신용카드는 조회 월과 무관하게 전체 승인 거래의 `finalAmount` 합계를 결제예정금액으로 반환한다.
            - 체크카드는 현재 월은 오늘까지, 과거 월은 해당 월 승인 거래의 `amount` 합계를 반환한다.
            - 승인·취소 거래를 모두 목록에 노출하고 `transactionStatus`로 구분한다.
            - 취소 거래는 합계와 실적에서 제외되어 `performanceIncluded=false`이며, 승인된 실적 미인정 거래는 합계에 포함한다.
            - 가맹점 정보가 없으면 `storeName`, `categoryName`, `categoryImageUrl`은 null이다.

            | HTTP | code | message |
            |---|---|---|
            | 400 | INVALID_YEAR_MONTH | 조회 연월 형식이 올바르지 않습니다. |
            | 400 | YEAR_MONTH_OUT_OF_RANGE | 미래 월의 내역은 조회할 수 없습니다. |
            | 400 | INVALID_TRANSACTION_PAGE_SIZE | 조회 개수는 1개 이상 100개 이하여야 합니다. |
            | 400 | INVALID_TRANSACTION_CURSOR | 유효하지 않은 결제 내역 커서입니다. |
            | 404 | CARD_NOT_FOUND | 요청한 카드를 찾을 수 없습니다. |
            """)
    @GetMapping("/card/{cardId}/transactions")
    public ResponseEntity<ApiResponse<CardTransactionDetailResponse>> getCardTransactions(
            @LoginUserId Long userId,
            @ApiParam(value = "보유 카드 ID(user_card_id)", example = "1", required = true)
            @PathVariable Long cardId,
            @ModelAttribute CardTransactionSearchRequest request) {

        return ApiResponse.of(CardSuccessCode.CARD_TRANSACTIONS_FOUND,
                cardService.getCardTransactions(userId, cardId, request));
    }

    @ApiOperation(value = "카드 이용 실적 상세 조회", notes = """
            로그인 사용자가 보유한 카드의 월별 이용 실적과 카드상품 단위 통합 혜택 구간을 조회한다.

            - `yearMonth`를 생략하면 현재 월을 조회하며, 미래 월을 제외한 모든 과거 월을 조회할 수 있다.
            - `availableYearMonths`는 현재 월부터 최초 거래 월까지 빈 월을 포함해 최신순으로 반환한다.
            - 체크카드의 현재 월 실적은 오늘 거래까지, 신용카드는 전날 거래까지 반영한다.
            - `recognizedAmount`는 `is_eligible=true`, `excludedAmount`는 `is_eligible=false` 거래의 `amount` 합계다.
            - 실적 조건이 있는 카드는 최소금액 기준의 통합 구간과 구간별 적용 혜택을 반환한다.
            - 실적 조건이 없는 카드는 `tiers=[]`이고 혜택을 `defaultBenefits`로 반환한다.
            - 실적 금액을 선택하면 같은 `cardId`, `yearMonth`로 카드별 결제 내역 API를 호출할 수 있다.

            | HTTP | code | message |
            |---|---|---|
            | 400 | INVALID_YEAR_MONTH | 조회 연월 형식이 올바르지 않습니다. |
            | 400 | YEAR_MONTH_OUT_OF_RANGE | 미래 월의 내역은 조회할 수 없습니다. |
            | 404 | CARD_NOT_FOUND | 요청한 카드를 찾을 수 없습니다. |
            """)
    @GetMapping("/card/{cardId}/usage")
    public ResponseEntity<ApiResponse<CardUsageDetailResponse>> getCardUsage(
            @LoginUserId Long userId,
            @ApiParam(value = "보유 카드 ID(user_card_id)", example = "5", required = true)
            @PathVariable Long cardId,
            @ModelAttribute CardUsageSearchRequest request) {

        return ApiResponse.of(CardSuccessCode.CARD_USAGE_FOUND,
                cardService.getCardUsage(userId, cardId, request));
    }

    @PostMapping("/card")
    public ResponseEntity<ApiResponse<CardListResponse>> register(
            @LoginUserId Long userId,
            @Valid @RequestBody CardRegisterRequest request) {

        return ApiResponse.of(CardSuccessCode.CARD_REGISTERED,
                cardService.register(userId, request));
    }

    @ApiOperation(value = "마이데이터 연동", notes = """
            마이데이터에서 가져온 보유 카드 중 아직 등록되지 않은 카드와 최근 거래내역을 등록한다.

            - 이미 등록된 카드는 건너뛰고 새로 발견된 카드만 등록한다.
            - 새로 등록할 카드가 하나도 없어도 오류가 아니라 성공으로 응답한다.
            """)
    @PostMapping("/cards/mydata")
    public ResponseEntity<ApiResponse<Void>> connectMyData(@LoginUserId Long userId) {
        cardService.connectMyData(userId);
        return ApiResponse.of(CardSuccessCode.MYDATA_LINKED, null);
    }

    @PatchMapping("/user-cards/display-order")
    public ResponseEntity<ApiResponse<Void>> updateCardsDisplayOrder(
            @LoginUserId Long userId,
            @Valid @RequestBody CardDisplayOrderUpdateRequest request) {

        cardService.updateCardsDisplayOrder(userId, request);

        return ApiResponse.of(CardSuccessCode.CARD_DISPLAY_ORDER_UPDATED, null);
    }
}
