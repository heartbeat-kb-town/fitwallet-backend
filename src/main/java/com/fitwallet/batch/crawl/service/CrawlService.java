package com.fitwallet.batch.crawl.service;

import com.fitwallet.batch.crawl.spi.IssuerCrawler;

/**
 * 카드사 혜택 원문 수집.
 *
 * <p>어댑터를 파라미터로 받는다. 서비스가 카드사를 알지 못하므로 카드사가 늘어도
 * 이 인터페이스는 그대로다.
 *
 * <p>AGENTS.md §9에 따라 {@code @Transactional}은 인터페이스가 아니라 구현체 메서드에
 * 붙인다. 이 서비스 자체는 트랜잭션을 열지 않는다 — 쓰기는 {@link RawCardWriter}가
 * 카드 한 장 단위로 묶는다.
 */
public interface CrawlService {

    /**
     * 카드를 열거해 상세 페이지를 받아 혜택 원문을 적재한다.
     *
     * @param crawler 대상 카드사 어댑터
     * @param limit   처리할 카드 수 상한. 0 이하면 제한 없음(스모크 테스트용)
     * @return 수집 결과 요약
     */
    CrawlResult crawl(IssuerCrawler crawler, int limit);
}
