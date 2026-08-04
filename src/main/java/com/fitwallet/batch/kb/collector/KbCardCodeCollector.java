package com.fitwallet.batch.kb.collector;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitwallet.batch.kb.client.KbCrawlEndpoints;
import com.fitwallet.batch.kb.client.KbHttpClient;
import com.fitwallet.batch.kb.dto.KbPopularCardResponse;

/**
 * 어떤 카드들이 있는지 알아내는 단계(파이프라인 ①).
 *
 * <p>KB엔 "전체 카드 목록"을 주는 공개 API가 없다. 목록 페이지는 스크롤할 때 XHR로 채우는
 * 구조라 정적 HTML만 받아서는 카드가 10개쯤밖에 안 보인다. 그래서 <b>서로 다른 세 곳에서
 * 긁어 합집합</b>을 만든다.
 *
 * <table>
 *   <caption>열거 소스별 실측 수확량</caption>
 *   <tr><th>소스</th><th>방법</th><th>수확</th></tr>
 *   <tr><td>사이트맵</td><td>{@code cooperationcode=} 쿼리 파라미터 추출</td><td>41개</td></tr>
 *   <tr><td>목록 페이지</td><td>카드 이미지 경로의 파일명이 곧 코드다</td><td>+20개</td></tr>
 *   <tr><td>인기카드 API</td><td>한글 키 JSON의 {@code 제휴코드}</td><td>보강</td></tr>
 *   <tr><td><b>합집합</b></td><td></td><td><b>61개</b></td></tr>
 * </table>
 *
 * <p><b>코드 대역을 훑는 무차별 대입은 하지 않는다.</b> 00001~99999를 다 찔러보면 카드를
 * 더 찾을 수야 있겠지만, 그건 KB 서버에 대한 부하이자 "공개된 것만 본다"는 이 배치의 전제를
 * 깨는 일이다. 사이트맵은 KB가 robots.txt에 스스로 공개한 파일이고, 나머지 둘도 일반 방문자가
 * 받는 그 페이지다.
 *
 * <p>그 대가로 <b>열거 완전성은 보장되지 않는다</b> — 61개가 KB 전체 카드라는 확인은 못 했다.
 * 사이트맵에 안 실린 카드는 안 잡힌다. 이건 알고 받아들인 한계다.
 */
@Component
public class KbCardCodeCollector {

    private static final Logger log = LoggerFactory.getLogger(KbCardCodeCollector.class);

    /** 사이트맵/링크의 {@code ...&cooperationcode=04485...}에서 코드만 뽑는다. */
    private static final Pattern COOPERATION_CODE_PATTERN =
            Pattern.compile("cooperationcode=(\\d{4,6})");

    /**
     * 카드 이미지 경로가 코드를 품고 있다:
     * {@code https://img1.kbcard.com/ST/img/cxc/kbcard/upload/img/product/09570_img.png}
     */
    private static final Pattern PRODUCT_IMAGE_PATTERN =
            Pattern.compile("upload/img/product/(\\d{4,6})_img");

    private final KbHttpClient httpClient;

    /**
     * 루트 컨텍스트엔 {@code ObjectMapper} 빈이 없다(웹 계층의 것은 별도 컨텍스트다).
     * 인기카드 API 응답 하나 읽자고 XML에 빈을 추가할 이유가 없어 여기서 직접 만든다.
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KbCardCodeCollector(KbHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * 세 소스의 합집합을 돌려준다.
     *
     * <p>한 소스가 실패해도 전체를 멈추지 않는다. 사이트맵이 잠깐 안 열린다고 수집 자체를
     * 포기할 이유는 없고, 어차피 소스끼리 겹치기 때문이다. 다만 <b>전부 실패해서 한 개도
     * 못 모으면</b> 그건 KB가 구조를 바꿨다는 신호이므로 예외로 알린다.
     *
     * @return 삽입 순서를 지키는 제휴코드 집합(로그를 읽기 쉽게 하려고 LinkedHashSet)
     */
    public Set<String> collectCardCodes() {
        Set<String> codes = new LinkedHashSet<>();

        collectQuietly("사이트맵", codes, this::collectFromSitemap);
        collectQuietly("목록 페이지", codes, this::collectFromListPages);
        collectQuietly("인기카드 API", codes, this::collectFromPopularCardApi);

        if (codes.isEmpty()) {
            throw new com.fitwallet.batch.kb.exception.KbCrawlException(
                    "카드 코드를 한 개도 수집하지 못했습니다. KB 페이지 구조가 바뀌었을 수 있습니다.");
        }
        log.info("카드 코드 {}개 수집 완료", codes.size());
        return codes;
    }

    private void collectQuietly(String sourceName, Set<String> target, SourceCollector collector) {
        int before = target.size();
        try {
            target.addAll(collector.collect());
            log.info("[{}] 신규 {}개 (누적 {}개)", sourceName, target.size() - before, target.size());
        } catch (RuntimeException e) {
            log.warn("[{}] 수집 실패 — 나머지 소스로 계속합니다: {}", sourceName, e.toString());
        }
    }

    /** 사이트맵 XML의 URL 중 {@code cooperationcode}가 붙은 것들. */
    private Set<String> collectFromSitemap() {
        return extractAll(httpClient.get(KbCrawlEndpoints.SITEMAP), COOPERATION_CODE_PATTERN);
    }

    /** 목록 페이지 3종의 카드 이미지 경로 + 혹시 박혀 있는 상세 링크. */
    private Set<String> collectFromListPages() {
        Set<String> codes = new LinkedHashSet<>();
        List<String> pages = List.of(
                KbCrawlEndpoints.CARD_LIST_PAGE,
                KbCrawlEndpoints.CREDIT_CARD_LIST_PAGE,
                KbCrawlEndpoints.CHECK_CARD_LIST_PAGE);

        for (String page : pages) {
            String html = httpClient.get(page);
            codes.addAll(extractAll(html, PRODUCT_IMAGE_PATTERN));
            codes.addAll(extractAll(html, COOPERATION_CODE_PATTERN));
        }
        return codes;
    }

    /** 인기카드 API. 부분집합이라 보강용이다. */
    private Set<String> collectFromPopularCardApi() {
        String json = httpClient.postForm(KbCrawlEndpoints.POPULAR_CARD_API, "");
        try {
            KbPopularCardResponse response = objectMapper.readValue(json, KbPopularCardResponse.class);
            Set<String> codes = new LinkedHashSet<>();
            addCardCodes(codes, response.getCreditCards());
            addCardCodes(codes, response.getCheckCards());
            return codes;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new com.fitwallet.batch.kb.exception.KbCrawlException(
                    "인기카드 API 응답을 파싱하지 못했습니다.", e);
        }
    }

    private void addCardCodes(Set<String> target, List<KbPopularCardResponse.Card> cards) {
        if (cards == null) {
            return;
        }
        for (KbPopularCardResponse.Card card : cards) {
            if (card.getCardCode() != null && !card.getCardCode().isBlank()) {
                target.add(card.getCardCode());
            }
        }
    }

    private Set<String> extractAll(String text, Pattern pattern) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    @FunctionalInterface
    private interface SourceCollector {
        Set<String> collect();
    }
}
