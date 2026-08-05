package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.request.CardRegisterRequest;
import com.fitwallet.domain.card.dto.request.CardListSearchRequest;
import com.fitwallet.domain.card.dto.request.CardTransactionSearchRequest;
import com.fitwallet.domain.card.dto.request.CardUsageSearchRequest;
import com.fitwallet.domain.card.dto.response.CardListResponse;
import com.fitwallet.domain.card.dto.response.CardSummaryResponse;
import com.fitwallet.domain.card.dto.response.CardTransactionDetailResponse;
import com.fitwallet.domain.card.dto.response.CardUsageDetailResponse;

import java.util.List;

/**
 * 컨트롤러는 이 인터페이스에만 의존한다. 구현체는 {@link DefaultCardService}.
 * 구현체 이름은 접미사 {@code Impl}이 아니라 접두사 {@code Default}를 쓴다.
 */
public interface CardService {

    List<CardListResponse> findMyCards(Long userId, CardListSearchRequest request);

    CardSummaryResponse findCardSummary(Long userId, Long cardId);

    CardTransactionDetailResponse getCardTransactions(
            Long userId,
            Long cardId,
            CardTransactionSearchRequest request);

    CardUsageDetailResponse getCardUsage(
            Long userId,
            Long cardId,
            CardUsageSearchRequest request);

    CardListResponse register(Long userId, CardRegisterRequest request);

    void connectMyData(Long userId);
}
