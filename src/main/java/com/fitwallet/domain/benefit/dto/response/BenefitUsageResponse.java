package com.fitwallet.domain.benefit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 한 tier·기간의 한도 소진량. {@code limitBasis}에 따라 서비스가 둘 중 하나만 골라 쓴다 —
 * {@code AMOUNT}/{@code POINT}는 {@code usedAmount}, {@code COUNT}는 {@code usedCount}.
 * 매칭되는 결제가 없어도 집계 함수가 항상 0을 반환하므로 이 DTO는 절대 null이 아니다.
 * <p>
 * MyBatis가 리플렉션으로 채우므로 {@code @Setter}는 붙이지 않는다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenefitUsageResponse {

    private BigDecimal usedAmount;
    private Long usedCount;
}
