package com.fitwallet.domain.card.controller;

import com.fitwallet.domain.card.dto.CardSuccessCode;
import com.fitwallet.domain.card.dto.request.CardRegisterRequest;
import com.fitwallet.domain.card.dto.response.CardListResponse;
import com.fitwallet.domain.card.service.CardService;
import com.fitwallet.global.common.annotation.LoginUserId;
import com.fitwallet.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;

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

    @PostMapping("/card")
    public ResponseEntity<ApiResponse<CardListResponse>> register(
            @LoginUserId Long userId,
            @Valid @RequestBody CardRegisterRequest request) {

        return ApiResponse.of(CardSuccessCode.CARD_REGISTERED,
                cardService.register(userId, request));
    }
}
