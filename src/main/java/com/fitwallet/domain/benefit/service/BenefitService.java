package com.fitwallet.domain.benefit.service;

import com.fitwallet.domain.benefit.dto.response.ExpectedBenefitResponse;

/**
 * 컨트롤러는 이 인터페이스에만 의존한다. 구현체는 {@link DefaultBenefitService}.
 * 구현체 이름은 접미사 {@code Impl}이 아니라 접두사 {@code Default}를 쓴다.
 */
public interface BenefitService {

    /**
     * <b>금액을 모르는 조회.</b> 결제 예정 금액을 알 수 없는 호출부가 쓴다 —
     * {@code expectedAmount}는 채워지지 않고, 카드가 내놓을 혜택은 고정 우선순위로 고른다.
     * <p>
     * {@code findExpectedBenefits(userId, storeId, null)}과 결과가 같다. 금액을 아는 호출부만
     * 3-인자 오버로드를 쓰면 되므로, 이 메서드를 쓰던 코드는 고치지 않아도 된다.
     */
    ExpectedBenefitResponse findExpectedBenefits(Long userId, String storeId);

    /**
     * {@code storeId}·{@code amount}는 컨트롤러가 {@code String}으로 그대로 넘긴다 — 파싱·검증은 여기서 한다.
     * {@code storeId}는 누락과 숫자 아님을 구분하지 않고 둘 다 {@code STORE_ID_REQUIRED}로 통일한다.
     *
     * @param amount 결제 예정 금액. {@code null}·빈 문자열이면 금액을 모르는 조회로 보고
     *               기대혜택액을 계산하지 않는다. 값이 있는데 숫자가 아니거나 0 이하면
     *               {@code AMOUNT_INVALID}로 막는다.
     */
    ExpectedBenefitResponse findExpectedBenefits(Long userId, String storeId, String amount);
}
