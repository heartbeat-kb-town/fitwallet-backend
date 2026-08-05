package com.fitwallet.domain.benefit.dto.response;

import com.fitwallet.domain.benefit.dto.LimitBasis;
import com.fitwallet.domain.benefit.dto.LimitPeriod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 혜택 한 건에 걸린 한도 한 행. 한 tier에 기간·종류 조합이 다른 한도가 여러 개
 * 붙을 수 있다({@code uq_benefit_limit_tier_basis_period}가 조합당 하나만 허용).
 * <p>
 * MyBatis가 리플렉션으로 채우므로 {@code @Setter}는 붙이지 않는다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenefitLimitResponse {

    private Long tierId;
    private LimitBasis limitBasis;
    private LimitPeriod limitPeriod;
    private BigDecimal limitValue;
}
