package com.fitwallet.batch.kb.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fitwallet.batch.kb.client.KbCrawlEndpoints;
import com.fitwallet.batch.kb.client.KbHttpClient;
import com.fitwallet.batch.kb.collector.KbCardCodeCollector;
import com.fitwallet.batch.kb.dto.KbRawSection;
import com.fitwallet.batch.kb.exception.KbCrawlException;
import com.fitwallet.batch.kb.exception.KbStubResponseException;
import com.fitwallet.batch.kb.mapper.CrawlRawCardMapper;
import com.fitwallet.batch.kb.parser.KbCardPageParser;

/**
 * 열거 → 수집 → 추출 → 적재를 엮는다.
 *
 * <p>이 클래스엔 {@code @Transactional}이 없다. 카드 한 장의 쓰기 트랜잭션은
 * {@link KbRawCardWriter}가 맡는다 — 네트워크 요청을 트랜잭션 밖에 두기 위해서다.
 *
 * <p>카드 하나가 실패해도 전체를 멈추지 않는다. 상대 서버가 있는 작업이라 산발적 실패는
 * 정상 범주고, 60장을 받아둔 상태에서 61번째 때문에 전부 버릴 이유가 없다. 다만 몇 개가
 * 왜 실패했는지는 {@link KbCrawlResult}에 담아 끝까지 들고 나온다 — 특히
 * {@code stubCount}가 0이 아니면 KB가 페이지 구조를 바꿨다는 신호라 반드시 눈에 띄어야 한다.
 */
@Service
public class DefaultKbCrawlService implements KbCrawlService {

    private static final Logger log = LoggerFactory.getLogger(DefaultKbCrawlService.class);

    /** {@code issuer.card_company_name}과 정확히 같아야 한다. */
    private static final String ISSUER_NAME = "KB국민카드";

    private final KbCardCodeCollector cardCodeCollector;
    private final KbHttpClient httpClient;
    private final KbCardPageParser pageParser;
    private final KbRawCardWriter rawCardWriter;
    private final CrawlRawCardMapper crawlRawCardMapper;

    public DefaultKbCrawlService(KbCardCodeCollector cardCodeCollector,
                                 KbHttpClient httpClient,
                                 KbCardPageParser pageParser,
                                 KbRawCardWriter rawCardWriter,
                                 CrawlRawCardMapper crawlRawCardMapper) {
        this.cardCodeCollector = cardCodeCollector;
        this.httpClient = httpClient;
        this.pageParser = pageParser;
        this.rawCardWriter = rawCardWriter;
        this.crawlRawCardMapper = crawlRawCardMapper;
    }

    @Override
    public KbCrawlResult crawl(int limit) {
        Long issuerId = crawlRawCardMapper.findIssuerIdByName(ISSUER_NAME);
        if (issuerId == null) {
            throw new KbCrawlException(
                    "issuer 테이블에 '" + ISSUER_NAME + "'가 없습니다. 시드를 먼저 적재하세요.");
        }

        Set<String> cardCodes = cardCodeCollector.collectCardCodes();
        List<String> targets = applyLimit(cardCodes, limit);
        log.info("KB 혜택 원문 수집 시작 — 열거 {}개, 이번 대상 {}개", cardCodes.size(), targets.size());

        int succeeded = 0;
        int stubs = 0;
        int sections = 0;
        List<String> failedCardCodes = new ArrayList<>();

        for (String cardCode : targets) {
            try {
                sections += crawlOneCard(issuerId, cardCode);
                succeeded++;
            } catch (KbStubResponseException e) {
                stubs++;
                failedCardCodes.add(cardCode);
                log.warn("카드 {} — 껍데기 응답: {}", cardCode, e.getMessage());
            } catch (RuntimeException e) {
                failedCardCodes.add(cardCode);
                log.warn("카드 {} — 수집 실패: {}", cardCode, e.toString());
            }
        }

        KbCrawlResult result = KbCrawlResult.builder()
                .enumeratedCount(cardCodes.size())
                .succeededCount(succeeded)
                .stubCount(stubs)
                .failedCount(failedCardCodes.size())
                .sectionCount(sections)
                .failedCardCodes(failedCardCodes)
                .build();

        log.info("KB 혜택 원문 수집 완료 — 성공 {}장 / 섹션 {}행 / 껍데기 {}장 / 실패 {}장",
                result.getSucceededCount(), result.getSectionCount(),
                result.getStubCount(), result.getFailedCount());
        return result;
    }

    /** 카드 한 장: 수집(트랜잭션 밖) → 추출(트랜잭션 밖) → 적재(트랜잭션 안). */
    private int crawlOneCard(Long issuerId, String cardCode) {
        String html = httpClient.get(KbCrawlEndpoints.cardDetailUrl(cardCode));
        List<KbRawSection> sections = pageParser.parse(cardCode, html);

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
