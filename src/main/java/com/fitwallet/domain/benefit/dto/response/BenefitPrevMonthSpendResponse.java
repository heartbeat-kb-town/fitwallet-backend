package com.fitwallet.domain.benefit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 카드별 전월실적 집계 결과. 지난달 거래가 없는 카드는 이 DTO 자체가 결과에 없다 —
 * 서비스가 조회 대상 카드 전체를 순회하며 없는 카드를 0으로 채운다.
 * <p>
 * MyBatis가 리플렉션으로 채우므로 {@code @Setter}는 붙이지 않는다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenefitPrevMonthSpendResponse {

    private Long userCardId;
    private BigDecimal prevMonthSpend;
}
