package com.fitwallet.domain.card.mapper;

import com.fitwallet.domain.card.dto.request.CardRegisterRequest;
import com.fitwallet.domain.card.dto.response.CardListResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 카드 도메인 조회.
 * <p>
 * 도메인은 테이블을 소유하지 않는다. 이 매퍼는 {@code user_card}, {@code card_product},
 * {@code issuer}를 조인해 화면이 필요한 모양으로 바로 반환한다.
 * SQL은 {@code resources/mapper/card/CardMapper.xml}에 있고,
 * {@code <select>}의 id는 여기 메서드명과 같아야 한다.
 */
@Mapper
public interface CardMapper {

    /** 사용자의 카드 목록을 {@code display_order} 순으로 조회한다. 삭제된 카드는 제외된다. */
    List<CardListResponse> findByUserId(@Param("userId") Long userId);

    /** 사용자의 카드 한 건. 없거나 삭제됐으면 null. */
    CardListResponse findByUserIdAndUserCardId(@Param("userId") Long userId,
                                               @Param("userCardId") Long userCardId);

    /** 등록 직후 응답을 만들기 위한 조회. */
    CardListResponse findByUserIdAndCardProductId(@Param("userId") Long userId,
                                                  @Param("cardProductId") Long cardProductId);

    /**
     * 등록 이력 여부. 행이 없으면 {@code null}, 사용 중이면 {@code false}, 소프트 삭제 상태면 {@code true}.
     * <p>
     * {@code (user_id, card_product_id)}에 UNIQUE가 걸려 있어 재등록은 INSERT가 아니라 재활성화다.
     * 그래서 "없음"과 "삭제됨"을 구분해야 한다.
     */
    Boolean findDeletedFlag(@Param("userId") Long userId,
                            @Param("cardProductId") Long cardProductId);

    /** 살아 있는 카드 중 가장 큰 표시순서. 없으면 0. */
    int findMaxDisplayOrder(@Param("userId") Long userId);

    void insertUserCard(@Param("userId") Long userId,
                        @Param("request") CardRegisterRequest request,
                        @Param("displayOrder") int displayOrder);

    /** 소프트 삭제된 카드를 다시 살린다. */
    void reactivateUserCard(@Param("userId") Long userId,
                            @Param("request") CardRegisterRequest request,
                            @Param("displayOrder") int displayOrder);
}
