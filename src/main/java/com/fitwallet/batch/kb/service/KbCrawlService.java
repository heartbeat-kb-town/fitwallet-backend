package com.fitwallet.batch.kb.service;

/**
 * KB국민카드 혜택 원문 수집.
 *
 * <p>AGENTS.md §9에 따라 {@code @Transactional}은 인터페이스가 아니라
 * {@link DefaultKbCrawlService} 구현체 메서드에 붙인다.
 */
public interface KbCrawlService {

    /**
     * 카드를 열거해 상세 페이지를 받아 혜택 원문을 적재한다.
     *
     * @param limit 처리할 카드 수 상한. 0 이하면 제한 없음(스모크 테스트용)
     * @return 수집 결과 요약
     */
    KbCrawlResult crawl(int limit);
}
