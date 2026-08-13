package com.fitwallet.domain.report.dto;

/**
 * 놓친 혜택 리포트의 손실 종류(디자인의 탭 구분).
 * <ul>
 *   <li>{@code APP_UNUSED} — 앱 미사용 손실. 앱으로 결제하지 않아(={@code is_used_app=0}) 놓친 혜택.</li>
 *   <li>{@code CARD_MISMATCH} — 카드 선택 손실. 앱은 썼지만(={@code is_used_app=1})
 *       더 나은 카드를 고르지 않아 놓친 혜택.</li>
 * </ul>
 * 두 경우 모두 "더 좋은 카드가 있었던 건"({@code better_user_card_id IS NOT NULL})만 대상이다.
 */
public enum LossType {
    APP_UNUSED(0),
    CARD_MISMATCH(1);

    private final int isUsedApp;

    LossType(int isUsedApp) {
        this.isUsedApp = isUsedApp;
    }

    /** payment_transaction.is_used_app 필터 값. */
    public int getIsUsedApp() {
        return isUsedApp;
    }
}
