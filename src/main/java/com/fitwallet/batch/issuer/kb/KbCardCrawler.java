package com.fitwallet.batch.issuer.kb;

import java.util.LinkedHashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitwallet.batch.crawl.client.CrawlHttpClient;
import com.fitwallet.batch.crawl.dto.RawCardBenefit;
import com.fitwallet.batch.crawl.exception.CrawlException;
import com.fitwallet.batch.crawl.spi.IssuerCrawler;

/**
 * KB국민카드 어댑터.
 *
 * <h2>수집 대상 (이 배치가 KB에 보내는 요청의 전부)</h2>
 *
 * <ol>
 *   <li>{@link #POPULAR_CARD_API} — 인기 카드 목록. 카드당 1회가 아니라 <b>실행당 1회</b></li>
 *   <li>{@link #CARD_DETAIL_URL_FORMAT} — 카드 상세. 열거된 카드 수만큼</li>
 * </ol>
 *
 * <p>둘 다 {@code robots.txt}가 {@code User-agent: *}에 대해 {@code Allow: /CRD/}로 허용한
 * 경로다(2026-08-10 실측). 로그인 없이 열리며 개인정보를 다루지 않는다.
 *
 * <h2>왜 인기 카드 API인가</h2>
 *
 * <p>KB는 카드 목록 페이지를 JS로 그려서 초기 HTML에 카드 코드가 하나도 없다. 코드를 얻을
 * 수 있는 공개 경로는 사이트맵과 이 API 둘뿐인데, <b>둘은 서로 다른 카드 집합</b>이다
 * (사이트맵 41장 / API 40장 / 교집합 3장). 사이트맵은 신규·기획 카드 위주고 API는 실사용
 * 인기 카드다.
 *
 * <p>지금 목표가 "대중적으로 쓰는 신용·체크 카드"이므로 <b>인기 순위 자체가 곧 선별 기준</b>이라
 * API만 쓴다. 카드명까지 함께 주기 때문에 열거 결과를 눈으로 검증하기도 쉽다.
 *
 * <p><b>카드 코드를 무차별 대입으로 만들지 않는다.</b> KB가 스스로 공개한 이 API의 응답에
 * 들어 있는 코드만 친다.
 */
@Component
public class KbCardCrawler implements IssuerCrawler {

    private static final Logger log = LoggerFactory.getLogger(KbCardCrawler.class);

    /** {@code issuer.card_company_name}과 정확히 같아야 한다. */
    private static final String ISSUER_NAME = "KB국민카드";

    private static final String BASE_URL = "https://card.kbcard.com";

    /**
     * 인기 카드 목록 API. GET을 받지 않아 POST로 친다(바디는 비어 있어도 된다).
     *
     * <p>응답은 {@code {"인기신용카드":[{"제휴코드":"01507","카드명":"..."}], "인기체크카드":[...]}}
     * 형태다. 키가 한글이라 DTO로 받지 않고 {@link JsonNode}로 읽는다 — 한글 필드명을
     * {@code @JsonProperty}로 매핑한 DTO는 이 한 곳에서만 쓰이는데 읽기만 더 어렵다.
     */
    private static final String POPULAR_CARD_API =
            BASE_URL + "/CRD/API/MCAA0004?responseContentType=json";

    /** 카드 상세. {@code cooperationcode}는 KB가 부여한 제휴코드(5자리 숫자)다. */
    private static final String CARD_DETAIL_URL_FORMAT =
            BASE_URL + "/CRD/DVIEW/HCAMCXPRICAC0076?mainCC=a&cooperationcode=%s";

    /** 목록 페이지를 Referer로 요구한다. 없으면 응답이 달라진다. */
    private static final String LIST_PAGE_REFERER =
            BASE_URL + "/CRD/DVIEW/HCAMCXPRICAC0047";

    private static final String[] CARD_LIST_KEYS = {"인기신용카드", "인기체크카드"};
    private static final String CARD_CODE_FIELD = "제휴코드";
    private static final String CARD_NAME_FIELD = "카드명";

    private final CrawlHttpClient httpClient;
    private final KbCardPageParser pageParser;
    private final ObjectMapper objectMapper;

    public KbCardCrawler(CrawlHttpClient httpClient, KbCardPageParser pageParser) {
        this.httpClient = httpClient;
        this.pageParser = pageParser;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String issuerName() {
        return ISSUER_NAME;
    }

    @Override
    public Set<String> collectCardCodes() {
        String body = httpClient.postForm(POPULAR_CARD_API, "", LIST_PAGE_REFERER);

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            throw new CrawlException("인기 카드 API 응답을 JSON으로 읽지 못했습니다.", e);
        }

        // 신용 → 체크 순서를 유지해 limit을 걸었을 때 늘 같은 카드가 뽑히게 한다(스모크 재현성).
        Set<String> codes = new LinkedHashSet<>();
        for (String listKey : CARD_LIST_KEYS) {
            JsonNode cards = root.path(listKey);
            if (!cards.isArray()) {
                throw new CrawlException(
                        "인기 카드 API 응답에 '" + listKey + "' 배열이 없습니다. 응답 형식이 바뀌었습니다.");
            }
            for (JsonNode card : cards) {
                String code = card.path(CARD_CODE_FIELD).asText(null);
                if (code == null || code.isBlank()) {
                    continue;
                }
                if (codes.add(code)) {
                    log.debug("열거 — {} {}", code, card.path(CARD_NAME_FIELD).asText(""));
                }
            }
        }

        if (codes.isEmpty()) {
            // 조용히 0장으로 끝나면 "수집할 카드가 없었다"와 "열거가 깨졌다"를 구분할 수 없다.
            throw new CrawlException("인기 카드 API가 카드 코드를 하나도 주지 않았습니다.");
        }
        return codes;
    }

    @Override
    public String cardDetailUrl(String cardCode) {
        return String.format(CARD_DETAIL_URL_FORMAT, cardCode);
    }

    @Override
    public RawCardBenefit parse(String cardCode, String html) {
        return pageParser.parse(cardCode, cardDetailUrl(cardCode), html);
    }
}
