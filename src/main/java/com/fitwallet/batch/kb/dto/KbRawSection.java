package com.fitwallet.batch.kb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카드 한 장에서 뽑아낸 원문 영역 하나.
 *
 * <p>파서의 출력이자 staging 적재의 입력이다. 해석된 값은 하나도 없다 —
 * {@code rawText}는 태그를 걷고 공백을 정규화한 <b>원문 문자열 그대로</b>이며,
 * 이걸 혜택 수치로 바꾸는 일은 다음 단계(LLM)의 몫이다.
 *
 * <p>AGENTS.md §4에 따라 record가 아닌 class + Lombok이고 {@code @Setter}가 없다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KbRawSection {

    /** 카드사가 부여한 카드 식별자. KB는 제휴코드(cooperationcode, 5자리). */
    private String cardCode;

    /** 수집 시점의 카드명. 페이지에서 못 뽑으면 null. */
    private String cardName;

    private SectionType section;

    /** 수집 출처 URL. */
    private String sourceUrl;

    /** 태그 제거 + 공백 정규화만 거친 순수 텍스트. */
    private String rawText;

    /**
     * {@code SHA-256(rawText)}를 소문자 16진수로 표현한 값(64자).
     *
     * <p>재수집 시 이 값이 같으면 원문이 안 바뀐 것이므로 다음 단계의 LLM 호출을 건너뛴다.
     * 비용 절감이자, 어떤 카드가 바뀌었는지 가려내는 근거다.
     */
    private String contentHash;
}
