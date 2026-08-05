package com.fitwallet.domain.card.dto;

/** 보유 카드 목록 정렬 기준. */
public enum CardListSortType {

    /** 결제 탭에서 사용하는 사용자의 표시 순서. */
    DISPLAY_ORDER,

    /** 내 카드 탭에서 사용하는 최근 결제 순서. */
    RECENTLY_USED
}
