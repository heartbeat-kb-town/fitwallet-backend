package com.fitwallet.domain.store.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가맹점 조회({@code GET /api/store/search})의 쿼리 파라미터 묶음.
 * <p>
 * 이름은 {@code AGENTS.md} §3의 "조회 조건 → {@code {대상}SearchCondition}" 규칙을 따른다.
 * POST 본문을 담는 {@code {동작}{대상}Request}(예: {@code CardRegisterRequest})와 규칙이 다르다 —
 * 이쪽은 명령이 아니라 필터이고, 필드가 전부 선택적으로 조합된다.
 * <p>
 * <b>컨트롤러가 받은 원본과 매퍼가 받는 값이 같지 않다.</b> 서비스가 그 사이에서 조건을 확정한다.
 * <ul>
 *   <li>{@code keyword} — trim한 뒤 공백만 남으면 {@code null}로 취급한다(= 필터 없음)</li>
 *   <li>{@code radiusMeters} — 반경 정책을 적용한 <b>최종값</b>이다.
 *       키워드 검색 모드에서 미전달이면 {@code null}(전국, 거리 필터 없음),
 *       {@code keyword} 없이 {@code categoryId}만 보낸 주변 조회에서 미전달이면 {@code 3000}(3km)</li>
 * </ul>
 * 즉 매퍼는 <b>확정된 조건</b>만 본다. 검증과 정책 적용은 서비스가 끝낸 상태다.
 * <p>
 * 인증 컨텍스트인 {@code userId}는 여기 넣지 않는다(§4). 쿼리로 받으면 남의 데이터를 조회할 수 있다.
 * <p>
 * Bean Validation을 붙이지 않는다. 명세의 400 응답이 {@code errors} 배열 없이 발생 조건별
 * {@code code}/{@code message}만 내려주므로, 검증은 서비스가 {@code BusinessException}으로 처리한다.
 */
@Getter
@NoArgsConstructor
public class StoreSearchCondition {

    /** 상호명 부분 일치 검색어. 미전달이면 {@code null}이고 이름 필터가 걸리지 않는다. */
    private String keyword;

    /** 업종 카테고리. 미전달이면 {@code null}이고 업종 필터가 걸리지 않는다. */
    private Long categoryId;

    /** 사용자 현재 위도. 거리 계산·정렬의 기준이라 필수다(서비스가 검증한다). */
    private Double latitude;

    /** 사용자 현재 경도. {@code latitude}와 항상 쌍으로 온다. */
    private Double longitude;

    /** 반경 정책이 적용된 최종 검색 반경(m). {@code null}이면 거리 필터를 걸지 않는다. */
    private Integer radiusMeters;
}
