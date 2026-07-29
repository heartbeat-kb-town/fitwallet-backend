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
 * {@code @Transactional}은 인터페이스가 아니라 여기, 구현체 메서드에 붙인다.
 * 지금은 JDK 동적 프록시라 인터페이스에 붙여도 동작하지만, 나중에 CGLIB
 * (proxy-target-class="true")로 바뀌면 인터페이스의 애너테이션은 조용히 무시된다.
 * <p>
 * 컨트롤러에 붙이면 애초에 안 걸린다 — {@code <tx:annotation-driven>}이 root-context에
 * 있고 컨트롤러는 servlet-context에서 스캔되기 때문이다.
 */
@Service
@RequiredArgsConstructor
public class DefaultCardService implements CardService {

    private final CardMapper cardMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CardListResponse> findMyCards(Long userId) {
        return cardMapper.findByUserId(userId);
    }

    @Override
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
    @Override
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
