package com.fitwallet.domain.card.controller;

import com.fitwallet.domain.card.dto.request.CardRegisterRequest;
import com.fitwallet.domain.card.dto.response.CardListResponse;
import com.fitwallet.domain.card.service.CardService;
import com.fitwallet.global.common.annotation.LoginUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 카드 도메인 참조 구현.
 * <p>
 * 사용자 식별자는 {@link LoginUserId}로만 받는다.
 * {@code @RequestParam userId}나 헤더 직접 읽기, 세션 접근은 금지한다.
 * <p>
 * 에러 응답은 여기서 만들지 않는다. 서비스가 던진 {@code BusinessException}을
 * {@code GlobalExceptionHandler}가 상태코드와 바디로 변환한다.
 */
@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;

    @GetMapping
    public List<CardListResponse> findMyCards(@LoginUserId Long userId) {
        return cardService.findMyCards(userId);
    }

    @GetMapping("/{userCardId}")
    public CardListResponse findMyCard(@LoginUserId Long userId,
                                       @PathVariable Long userCardId) {
        return cardService.findMyCard(userId, userCardId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CardListResponse register(@LoginUserId Long userId,
                                     @Valid @RequestBody CardRegisterRequest request) {
        return cardService.register(userId, request);
    }
}
