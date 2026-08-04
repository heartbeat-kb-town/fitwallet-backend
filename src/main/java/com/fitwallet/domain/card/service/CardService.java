package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.request.CardRegisterRequest;
import com.fitwallet.domain.card.dto.request.CardTransactionSearchRequest;
import com.fitwallet.domain.card.dto.response.CardListResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionDetailResponse;

import java.util.List;

/**
 * 컨트롤러는 이 인터페이스에만 의존한다. 구현체는 {@link DefaultCardService}.
 * 구현체 이름은 접미사 {@code Impl}이 아니라 접두사 {@code Default}를 쓴다.
 */
public interface CardService {

    List<CardListResponse> findMyCards(Long userId);

    CardListResponse findMyCard(Long userId, Long userCardId);

    CardTransactionDetailResponse getCardTransactions(
            Long userId,
            Long cardId,
            CardTransactionSearchRequest request);

    CardListResponse register(Long userId, CardRegisterRequest request);

    void connectMyData(Long userId);
}
