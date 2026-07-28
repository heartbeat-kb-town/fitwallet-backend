package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.CardType;
import com.fitwallet.domain.card.dto.request.CardRegisterRequest;
import com.fitwallet.domain.card.dto.response.CardListResponse;
import com.fitwallet.domain.card.exception.CardErrorCode;
import com.fitwallet.domain.card.mapper.CardMapper;
import com.fitwallet.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Service 단위 테스트. Mapper를 목킹하므로 DB가 필요 없다.
 */
@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private CardMapper cardMapper;

    @InjectMocks
    private CardService cardService;

    @Test
    void 내_카드_목록을_매퍼가_준_순서_그대로_반환한다() {
        given(cardMapper.findByUserId(1L)).willReturn(List.of(
                CardListResponse.builder().userCardId(10L).cardType(CardType.CREDIT).build(),
                CardListResponse.builder().userCardId(11L).cardType(CardType.DEBIT).build()));

        List<CardListResponse> cards = cardService.findMyCards(1L);

        assertThat(cards).extracting(CardListResponse::getUserCardId)
                .containsExactly(10L, 11L);
    }

    @Test
    void 카드가_없으면_빈_목록을_반환한다() {
        given(cardMapper.findByUserId(1L)).willReturn(List.of());

        assertThat(cardService.findMyCards(1L)).isEmpty();
    }

    @Test
    void 카드_단건_조회시_없는_카드면_CARD_NOT_FOUND_예외를_던진다() {
        given(cardMapper.findByUserIdAndUserCardId(1L, 999L)).willReturn(null);

        assertThatThrownBy(() -> cardService.findMyCard(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CardErrorCode.CARD_NOT_FOUND);
    }

    @Test
    void 다른_사용자의_카드는_조회되지_않아_예외를_던진다() {
        // 매퍼가 userId 조건을 함께 걸기 때문에 남의 카드는 null로 돌아온다
        given(cardMapper.findByUserIdAndUserCardId(2L, 10L)).willReturn(null);

        assertThatThrownBy(() -> cardService.findMyCard(2L, 10L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 처음_등록하는_카드는_INSERT하고_표시순서는_마지막_다음이_된다() {
        CardRegisterRequest request = registerRequest();
        given(cardMapper.findDeletedFlag(1L, 47L)).willReturn(null);
        given(cardMapper.findMaxDisplayOrder(1L)).willReturn(5);

        cardService.register(1L, request);

        then(cardMapper).should().insertUserCard(1L, request, 6);
        then(cardMapper).should(never()).reactivateUserCard(any(), any(), anyInt());
    }

    @Test
    void 이미_사용중인_카드를_또_등록하면_CARD_ALREADY_REGISTERED_예외를_던진다() {
        given(cardMapper.findDeletedFlag(1L, 47L)).willReturn(false);

        assertThatThrownBy(() -> cardService.register(1L, registerRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CardErrorCode.CARD_ALREADY_REGISTERED);

        then(cardMapper).should(never()).insertUserCard(any(), any(), anyInt());
    }

    @Test
    void 삭제했던_카드를_다시_등록하면_INSERT가_아니라_재활성화한다() {
        CardRegisterRequest request = registerRequest();
        given(cardMapper.findDeletedFlag(1L, 47L)).willReturn(true);
        given(cardMapper.findMaxDisplayOrder(1L)).willReturn(2);

        cardService.register(1L, request);

        then(cardMapper).should().reactivateUserCard(1L, request, 3);
        then(cardMapper).should(never()).insertUserCard(any(), any(), anyInt());
    }

    private CardRegisterRequest registerRequest() {
        CardRegisterRequest request = new CardRegisterRequest();
        ReflectionTestUtils.setField(request, "cardProductId", 47L);
        ReflectionTestUtils.setField(request, "first4", "5327");
        ReflectionTestUtils.setField(request, "last4", "8014");
        ReflectionTestUtils.setField(request, "expiryDate", LocalDate.of(2030, 1, 31));
        return request;
    }
}
