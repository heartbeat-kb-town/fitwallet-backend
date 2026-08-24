package com.fitwallet.domain.benefit.service;

import com.fitwallet.domain.benefit.dto.response.ExpectedBenefitResponse;
import com.fitwallet.domain.benefit.dto.response.PaymentBenefitResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * 컨트롤러는 이 인터페이스에만 의존한다. 구현체는 {@link DefaultBenefitService}.
 * 구현체 이름은 접미사 {@code Impl}이 아니라 접두사 {@code Default}를 쓴다.
 */
public interface BenefitService {

    /**
     * <b>금액을 모르는 조회.</b> 결제 예정 금액을 알 수 없는 호출부가 쓴다 —
     * {@code expectedAmount}는 채워지지 않지만, <b>순위({@code rank})는 매겨진다.</b>
     * 카드가 내놓을 혜택과 카드 사이의 순서 모두 혜택 우열(정액→정률, 할인→적립, 값이 큰 쪽)로 정한다.
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
     *               기대혜택액을 계산하지 않는다(순위는 혜택 우열로 매긴다).
     *               값이 있는데 숫자가 아니거나 0 이하면 {@code AMOUNT_INVALID}로 막는다.
     */
    ExpectedBenefitResponse findExpectedBenefits(Long userId, String storeId, String amount);

    /**
     * <b>payment 전용 판정.</b> {@code findExpectedBenefits}와 같은 판정을 돌리되,
     * 결제 확정에 필요한 값({@code benefitType}·네이티브 금액·{@code tierId})까지 실어 준다.
     * 화면 응답과 결제 결과가 서로 다른 금액을 답하지 않도록 계산 경로를 하나로 묶는다.
     * <p>
     * {@code AVAILABLE}인 카드만, <b>원화 기대혜택액 내림차순</b>으로 돌려준다. 동점이면
     * 카드 표시 순서({@code display_order})가 유지된다 — 예상 혜택 목록의 순위와 같은 기준이다.
     *
     * @param storeId 결제 세션이 확정한 가맹점. 없으면 {@code STORE_NOT_FOUND}
     * @param amount  결제 금액. 결제 확정 시점이므로 항상 값이 있다
     */
    List<PaymentBenefitResponse> findPaymentBenefits(Long userId, Long storeId, BigDecimal amount);
}
