package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.request.CardDisplayOrderUpdateRequest;
import com.fitwallet.domain.card.dto.request.CardRegisterRequest;
import com.fitwallet.domain.card.dto.request.CardListSearchRequest;
import com.fitwallet.domain.card.dto.request.CardTransactionSearchRequest;
import com.fitwallet.domain.card.dto.request.CardUsageSearchRequest;
import com.fitwallet.domain.card.dto.response.CardListResponse;
import com.fitwallet.domain.card.dto.response.CardMonthlyBenefitResponse;
import com.fitwallet.domain.card.dto.response.CardEventResponse;
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

    CardEventResponse findCardEvents(Long userId, Long cardId);

    /** 로그인 사용자가 보유한 카드의 현재 월 혜택 한도 현황을 조회한다. */
    CardMonthlyBenefitResponse getCardMonthlyBenefit(Long userId, Long cardId);

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

    /** 로그인 사용자의 보유 카드 표시 순서를 변경한다. */
    void updateCardsDisplayOrder(Long userId, CardDisplayOrderUpdateRequest request);
}
