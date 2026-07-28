package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.request.CardRegisterRequest;
import com.fitwallet.domain.card.dto.response.CardListResponse;
import com.fitwallet.domain.card.exception.CardErrorCode;
import com.fitwallet.domain.card.mapper.CardMapper;
import com.fitwallet.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 인터페이스 없이 클래스 하나로 둔다 ({@code CardServiceImpl}을 만들지 않는다).
 * <p>
 * {@code @Transactional}은 이 계층에만 붙인다. 컨트롤러에 붙이면
 * {@code <tx:annotation-driven>}이 root-context에 있고 컨트롤러는 servlet-context에서
 * 스캔되기 때문에 프록시가 걸리지 않아 조용히 무시된다.
 */
@Service
@RequiredArgsConstructor
public class CardService {

    private final CardMapper cardMapper;

    @Transactional(readOnly = true)
    public List<CardListResponse> findMyCards(Long userId) {
        return cardMapper.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public CardListResponse findMyCard(Long userId, Long userCardId) {
        CardListResponse card = cardMapper.findByUserIdAndUserCardId(userId, userCardId);
        if (card == null) {
            throw new BusinessException(CardErrorCode.CARD_NOT_FOUND);
        }
        return card;
    }

    /**
     * 카드를 등록한다.
     * <p>
     * {@code (user_id, card_product_id)}에 UNIQUE가 걸려 있고 삭제는 소프트 삭제라,
     * 예전에 지운 카드를 다시 등록하면 새 행을 넣지 않고 기존 행을 되살린다.
     */
    @Transactional
    public CardListResponse register(Long userId, CardRegisterRequest request) {
        Boolean deleted = cardMapper.findDeletedFlag(userId, request.getCardProductId());
        if (Boolean.FALSE.equals(deleted)) {
            throw new BusinessException(CardErrorCode.CARD_ALREADY_REGISTERED);
        }

        int displayOrder = cardMapper.findMaxDisplayOrder(userId) + 1;
        if (deleted == null) {
            cardMapper.insertUserCard(userId, request, displayOrder);
        } else {
            cardMapper.reactivateUserCard(userId, request, displayOrder);
        }

        return cardMapper.findByUserIdAndCardProductId(userId, request.getCardProductId());
    }
}
