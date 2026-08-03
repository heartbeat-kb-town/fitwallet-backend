package com.fitwallet.domain.card.controller;

import com.fitwallet.domain.card.dto.CardSuccessCode;
import com.fitwallet.domain.card.dto.request.CardRegisterRequest;
import com.fitwallet.domain.card.dto.request.CardTransactionSearchRequest;
import com.fitwallet.domain.card.dto.response.CardListResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionDetailResponse;
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

    @GetMapping("/user-cards")
    public ResponseEntity<ApiResponse<List<CardListResponse>>> findMyCards(@LoginUserId Long userId) {
        return ApiResponse.of(CardSuccessCode.USER_CARDS_FOUND, cardService.findMyCards(userId));
    }

    /**
     * 명세의 "내 카드 요약 조회". 명세가 경로 변수를 {@code cardId}로 적었지만
     * 실제로 가리키는 값은 {@code user_card_id}다 (다른 API는 {@code userCardId}로 적혀 있어
     * 명세 쪽 표기 통일이 필요하다).
     */
    @GetMapping("/card/{cardId}/summary")
    public ResponseEntity<ApiResponse<CardListResponse>> findMyCard(
            @LoginUserId Long userId,
            @PathVariable("cardId") Long userCardId) {

        return ApiResponse.of(CardSuccessCode.CARD_SUMMARY_FOUND,
                cardService.findMyCard(userId, userCardId));
    }

    @ApiOperation(value = "카드별 세부 결제 내역 조회", notes = """
            로그인 사용자가 보유한 카드의 월별 결제 내역을 커서 방식으로 조회한다.

            - `yearMonth`를 생략하면 현재 월을 조회하며, 현재 월을 포함한 최근 3개월만 조회할 수 있다.
            - 정렬 기준은 `paidAt DESC`, `transactionId DESC`이며 `nextCursor`로 다음 내역을 조회한다.
            - 현재 월 신용카드는 전날까지 반영된 저장 결제 이용금액을 반환한다.
            - 현재 월 체크카드는 오늘까지, 과거 월 카드는 해당 월 전체 거래의 `amount` 합계를 반환한다.
            - 실적 미인정 거래도 목록과 합계에 포함하며 `performanceIncluded=false`로 구분한다.
            - 가맹점 정보가 없으면 `storeName`, `categoryName`, `categoryImageUrl`은 null이다.

            | HTTP | code | message |
            |---|---|---|
            | 400 | INVALID_YEAR_MONTH | 조회 연월 형식이 올바르지 않습니다. |
            | 400 | YEAR_MONTH_OUT_OF_RANGE | 최근 3개월의 결제 내역만 조회할 수 있습니다. |
            | 400 | INVALID_TRANSACTION_PAGE_SIZE | 조회 개수는 1개 이상 100개 이하여야 합니다. |
            | 400 | INVALID_TRANSACTION_CURSOR | 유효하지 않은 결제 내역 커서입니다. |
            | 404 | CARD_NOT_FOUND | 요청한 카드를 찾을 수 없습니다. |
            | 500 | INVALID_CARD_PAYMENT_DATA | 카드 결제 이용금액 데이터가 올바르지 않습니다. |
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
}
