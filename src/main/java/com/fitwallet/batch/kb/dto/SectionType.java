package com.fitwallet.batch.kb.dto;

import java.util.Optional;

/**
 * 카드 상세 페이지에서 수집하는 원문 영역.
 *
 * <p>KB 상세 페이지는 탭으로 나뉘고 각 탭이 {@code <div id="tabConNN">} 하나에 대응한다.
 * 그런데 <b>NN과 탭의 의미는 카드마다 다르다.</b> 실측:
 *
 * <pre>
 * 굿데이 / 예다함 / The CJ : 00 주요혜택 | 01 상세혜택 | 02 연회비   | 03 확인사항
 * 스카이패스 플래티늄        : 00 주요혜택 | 01 상세혜택 | 02 플래티늄 | 03 연회비   | 04 확인사항
 * 노리 체크               : 00 주요혜택 | 01 상세혜택 | 02 카드이용 | 03 해외이용 | 04 확인사항
 * </pre>
 *
 * <p>카드에 부가 탭이 붙으면 뒤 탭들이 통째로 밀린다. 그래서 <b>번호가 아니라 탭 라벨로</b>
 * 찾는다. 번호로 찾으면 플래티늄 카드에서 "연회비" 자리에 쿠폰서비스 20,000자가 들어온다
 * (실제로 그렇게 겪고 고쳤다).
 *
 * <p>여기 없는 탭은 수집하지 않는다. 특히 <b>확인사항</b>은 카드당 8천 자가 넘어 페이지에서
 * 가장 큰 영역이지만 내용이 할인 제외매출·연체이자율 같은 법적 고지문이다(카드 3종 실측
 * 8,841 / 8,554 / 8,127자, 카드 간 유사도 52%). 혜택 판정에 쓸 값이 없는 데다, 넣으면
 * "상품권 구입은 할인 제외" 같은 문장이 오히려 혜택으로 오추출된다.
 *
 * <p>상수 이름은 DDL의 {@code ck_crawl_raw_card_section} CHECK 값과 같아야 한다
 * (AGENTS.md §6 — 커스텀 TypeHandler 없이 기본 {@code EnumTypeHandler}가 변환한다).
 */
public enum SectionType {

    /** 주요혜택. 카드 대표 혜택 요약. 짧다(실측 62~232자). */
    SUMMARY("주요혜택"),

    /** 상세혜택. 전월실적 구간표와 한도가 들어 있는 핵심 영역(실측 347~4,078자). */
    DETAIL("상세혜택"),

    /** 연회비. 기본/제휴 연회비 표(실측 278~1,761자). */
    ANNUAL_FEE("연회비");

    private final String tabLabel;

    SectionType(String tabLabel) {
        this.tabLabel = tabLabel;
    }

    /** 상세 페이지 탭에 표시되는 이름. 이 문자열로 탭을 찾는다. */
    public String getTabLabel() {
        return tabLabel;
    }

    public static Optional<SectionType> fromTabLabel(String label) {
        if (label == null) {
            return Optional.empty();
        }
        String normalized = label.replaceAll("\\s+", "");
        for (SectionType type : values()) {
            if (type.tabLabel.equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
