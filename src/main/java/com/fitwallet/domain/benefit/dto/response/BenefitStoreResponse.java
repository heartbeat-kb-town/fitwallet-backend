package com.fitwallet.domain.benefit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 예상 혜택 판정용 가맹점 조회 결과. {@code store} 테이블 조회 결과를 그대로 채운다.
 * <p>
 * {@code categoryId}/{@code brandId}는 최종 API 응답에는 나가지 않고 혜택 스코프 매칭에만
 * 쓰인다 — 최종 응답의 가맹점 표현({@code storeId}, {@code storeName}만 담음)은 서비스
 * 이슈에서 별도 DTO로 조립한다.
 * <p>
 * MyBatis가 리플렉션으로 채우므로 {@code @Setter}는 붙이지 않는다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenefitStoreResponse {

    private Long storeId;
    private String storeName;

    /** 업종 스코프(INDUSTRY) 매칭에만 쓴다. */
    private Long categoryId;

    /** 브랜드 스코프(BRAND) 매칭에만 쓴다. 인식 가능한 체인일 때만 값이 있다. */
    private Long brandId;
}
