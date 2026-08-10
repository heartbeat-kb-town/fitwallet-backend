package com.fitwallet.batch.crawl.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fitwallet.batch.crawl.client.CrawlHttpClient;
import com.fitwallet.batch.crawl.dto.RawSection;
import com.fitwallet.batch.crawl.exception.CrawlException;
import com.fitwallet.batch.crawl.exception.StubResponseException;
import com.fitwallet.batch.crawl.mapper.CrawlRawCardMapper;
import com.fitwallet.batch.crawl.spi.IssuerCrawler;

/**
 * 열거 → 수집 → 추출 → 적재를 엮는다. <b>카드사를 알지 못한다.</b>
 *
 * <p>카드사별로 다른 부분은 전부 {@link IssuerCrawler}에 위임한다. 그래서 신한·현대를
 * 붙일 때 이 클래스는 한 줄도 바뀌지 않는다.
 *
 * <p>이 클래스엔 {@code @Transactional}이 없다. 카드 한 장의 쓰기 트랜잭션은
 * {@link RawCardWriter}가 맡는다 — 네트워크 요청을 트랜잭션 밖에 두기 위해서다.
 *
 * <p>카드 하나가 실패해도 전체를 멈추지 않는다. 상대 서버가 있는 작업이라 산발적 실패는
 * 정상 범주고, 60장을 받아둔 상태에서 61번째 때문에 전부 버릴 이유가 없다. 다만 몇 개가
 * 왜 실패했는지는 {@link CrawlResult}에 담아 끝까지 들고 나온다 — 특히
 * {@code stubCount}가 0이 아니면 카드사가 페이지 구조를 바꿨다는 신호라 반드시 눈에 띄어야 한다.
 */
@Service
public class DefaultCrawlService implements CrawlService {

    private static final Logger log = LoggerFactory.getLogger(DefaultCrawlService.class);

    private final CrawlHttpClient httpClient;
    private final RawCardWriter rawCardWriter;
    private final CrawlRawCardMapper crawlRawCardMapper;

    public DefaultCrawlService(CrawlHttpClient httpClient,
                               RawCardWriter rawCardWriter,
                               CrawlRawCardMapper crawlRawCardMapper) {
        this.httpClient = httpClient;
        this.rawCardWriter = rawCardWriter;
        this.crawlRawCardMapper = crawlRawCardMapper;
    }

    @Override
    public CrawlResult crawl(IssuerCrawler crawler, int limit) {
        String issuerName = crawler.issuerName();
        Long issuerId = crawlRawCardMapper.findIssuerIdByName(issuerName);
        if (issuerId == null) {
            throw new CrawlException(
                    "issuer 테이블에 '" + issuerName + "'가 없습니다. 시드를 먼저 적재하세요.");
        }

        Set<String> cardCodes = crawler.collectCardCodes();
        List<String> targets = applyLimit(cardCodes, limit);
        log.info("{} 혜택 원문 수집 시작 — 열거 {}개, 이번 대상 {}개",
                issuerName, cardCodes.size(), targets.size());

        int succeeded = 0;
        int stubs = 0;
        int sections = 0;
        List<String> failedCardCodes = new ArrayList<>();

        for (String cardCode : targets) {
            try {
                sections += crawlOneCard(crawler, issuerId, cardCode);
                succeeded++;
            } catch (StubResponseException e) {
                stubs++;
                failedCardCodes.add(cardCode);
                log.warn("카드 {} — 껍데기 응답: {}", cardCode, e.getMessage());
            } catch (RuntimeException e) {
                failedCardCodes.add(cardCode);
                log.warn("카드 {} — 수집 실패: {}", cardCode, e.toString());
            }
        }

        CrawlResult result = CrawlResult.builder()
                .issuerName(issuerName)
                .enumeratedCount(cardCodes.size())
                .succeededCount(succeeded)
                .stubCount(stubs)
                .failedCount(failedCardCodes.size())
                .sectionCount(sections)
                .failedCardCodes(failedCardCodes)
                .build();

        log.info("{} 혜택 원문 수집 완료 — 열거 {}개 / 성공 {}장 / 섹션 {}행 / 껍데기 {}장 / 실패 {}장",
                issuerName, result.getEnumeratedCount(), result.getSucceededCount(),
                result.getSectionCount(), result.getStubCount(), result.getFailedCount());
        return result;
    }

    /** 카드 한 장: 수집(트랜잭션 밖) → 추출(트랜잭션 밖) → 적재(트랜잭션 안). */
    private int crawlOneCard(IssuerCrawler crawler, Long issuerId, String cardCode) {
        String url = crawler.cardDetailUrl(cardCode);
        String html = httpClient.get(url);
        List<RawSection> sections = crawler.parse(cardCode, html);

        if (sections.isEmpty()) {
            // 껍데기는 어댑터가 예외로 던지기로 돼 있다. 빈 목록이 오면 그 계약이 깨진 것이라
            // 성공으로 세지 않는다 — 조용히 0행을 쌓는 게 이 배치에서 제일 위험하다.
            throw new CrawlException("추출된 섹션이 없습니다: " + url);
        }

        int saved = rawCardWriter.save(issuerId, sections);
        log.debug("카드 {}({}) — 섹션 {}개 적재", cardCode, sections.get(0).getCardName(), saved);
        return saved;
    }

    private List<String> applyLimit(Set<String> cardCodes, int limit) {
        List<String> all = new ArrayList<>(cardCodes);
        if (limit <= 0 || limit >= all.size()) {
            return all;
        }
        return all.subList(0, limit);
    }
}
