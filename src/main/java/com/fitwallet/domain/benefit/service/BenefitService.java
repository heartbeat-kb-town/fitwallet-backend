package com.fitwallet.domain.benefit.service;

import com.fitwallet.domain.benefit.dto.response.ExpectedBenefitResponse;

/**
 * 컨트롤러는 이 인터페이스에만 의존한다. 구현체는 {@link DefaultBenefitService}.
 * 구현체 이름은 접미사 {@code Impl}이 아니라 접두사 {@code Default}를 쓴다.
 */
public interface BenefitService {

    /**
     * {@code storeId}는 컨트롤러가 {@code String}으로 그대로 넘긴다 — 파싱·검증은 여기서 한다.
     * 누락과 숫자 아님을 구분하지 않고 둘 다 {@code STORE_ID_REQUIRED}로 통일한다.
     */
    ExpectedBenefitResponse findExpectedBenefits(Long userId, String storeId);
}
