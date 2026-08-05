package com.fitwallet.domain.card.service;

import com.fitwallet.domain.card.dto.MyDataCard;

import java.util.List;

/**
 * 사용자의 카드와 거래내역을 가져오는 역할을 한다.
 * 현재는 목데이터를 사용하며, 추후 실제 마이데이터 연동 구현체로 교체할 수 있다.
 */
public interface MyDataProvider {

    /** {@code userId}는 지금 mock 구현에서는 안 쓰지만, 실제 연동에서는 반드시 필요한 값이라 미리 넣는다. */
    List<MyDataCard> fetchCards(Long userId);
}
