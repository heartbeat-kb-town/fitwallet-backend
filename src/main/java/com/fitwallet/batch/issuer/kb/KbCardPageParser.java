package com.fitwallet.batch.issuer.kb;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import com.fitwallet.batch.crawl.dto.HtmlText;
import com.fitwallet.batch.crawl.dto.RawCardBenefit;
import com.fitwallet.batch.crawl.exception.CrawlException;
import com.fitwallet.batch.crawl.exception.StubResponseException;

/**
 * KB국민카드 상세 페이지에서 혜택 원문을 뽑는다. <b>네트워크를 모른다.</b>
 *
 * <p>페이지 구조(2026-08 실측, 대중 신용·체크 카드 13장):
 *
 * <pre>
 * h1.tit                     ← 카드명
 * ul.tabType1
 *   li#topTab0 > a[href=#tabCon00] > span  "주요혜택"
 *   li#topTab1 > a[href=#tabCon01] > span  "상세혜택"   ← 이것만 저장한다
 *   li#topTab2 > a[href=#tabCon02] > span  "연회비"
 *   ...
 * div#tabCon01                ← 실제 본문
 * </pre>
 *
 * <h2>탭을 인덱스가 아니라 라벨로 찾는 이유</h2>
 *
 * <p><b>탭 개수가 카드마다 4~6개로 다르고, 카드 전용 탭이 중간에 끼어든다.</b> 실측에서
 * {@code Brand Story}·{@code BeV IX 전용혜택}·{@code Mastercard 서비스}가 모두
 * {@code tabCon02} 자리를 차지해 그 뒤 탭이 한 칸씩 밀렸다. 체크카드는 연회비 탭이 아예
 * 없다. 인덱스로 잡으면 어느 순간 조용히 엉뚱한 영역을 저장하게 된다 — 예외도 안 나고
 * 행 수도 정상이라 알아채기까지 오래 걸린다.
 *
 * <h2>왜 상세혜택만인가</h2>
 *
 * <p>대중 카드 8장을 대조한 결과 {@code 주요혜택}의 모든 토큰이 이미 {@code 상세혜택}에
 * 있었다(탭 제목 문자열 제외). {@code 카드이용}은 결제계좌·출금 규칙이고 {@code 해외이용}은
 * 국제브랜드 마크와 수수료율이라 혜택이 아니다. {@code 확인사항}은 카드당 8천 자가 넘는
 * 법적 고지문인데, 넣으면 "상품권 구입은 할인 제외" 같은 문장이 오히려 혜택으로 오추출된다.
 *
 * <p>단 <b>프리미엄 카드에서는 이 규칙이 깨진다.</b> {@code BeV Ⅸ 대한항공 카드}는
 * {@code 상세혜택}이 1,585자뿐이고 동반자 항공권·라운지 같은 실제 혜택이 전부 카드 전용
 * 탭(16,885자)에 있었다. 지금은 대중 신용·체크 카드만 대상이라 해당 없다.
 */
@Component
public class KbCardPageParser {

    /** 저장 대상 탭의 라벨. */
    private static final String BENEFIT_TAB_LABEL = "상세혜택";

    private static final String TAB_LINK_SELECTOR = "ul.tabType1 li[id^=topTab] > a[href^=#tabCon]";
    private static final String CARD_NAME_SELECTOR = "h1.tit";

    /**
     * 이 길이에 못 미치면 껍데기로 본다.
     *
     * <p>실측 최소가 398자(K-패스체크카드)였다. 정상 카드가 걸리지 않으면서 빈 껍데기는
     * 잡을 수 있는 선으로 100자를 뒀다.
     */
    private static final int MIN_RAW_TEXT_LENGTH = 100;

    /**
     * @param sourceUrl 수집 출처. 결과에 그대로 담는다
     * @param html      상세 페이지 HTML 전문
     * @throws StubResponseException 카드 상세 페이지가 아니거나 본문이 비었을 때
     * @throws CrawlException        탭 구조가 바뀌어 상세혜택을 못 찾을 때
     */
    public RawCardBenefit parse(String cardCode, String sourceUrl, String html) {
        if (html == null || html.isBlank()) {
            throw new StubResponseException("응답 본문이 비어 있습니다: cardCode=" + cardCode);
        }
        Document document = Jsoup.parse(html);

        Elements tabs = document.select(TAB_LINK_SELECTOR);
        if (tabs.isEmpty()) {
            // 탭이 하나도 없으면 카드 상세 페이지 자체가 아니다(오류 화면·리다이렉트 등).
            throw new StubResponseException(
                    "카드 상세 페이지가 아닙니다(탭 없음): cardCode=" + cardCode
                            + ", 본문 " + html.length() + "바이트");
        }

        Element benefitTab = findBenefitTab(cardCode, tabs);
        String rawText = HtmlText.of(benefitTab);

        if (rawText.length() < MIN_RAW_TEXT_LENGTH) {
            throw new StubResponseException(
                    "상세혜택 본문이 " + rawText.length() + "자뿐입니다: cardCode=" + cardCode);
        }
        return RawCardBenefit.builder()
                .cardCode(cardCode)
                .cardName(findCardName(document))
                .sourceUrl(sourceUrl)
                .rawText(rawText)
                .build();
    }

    /** 라벨이 일치하는 탭의 본문 {@code div}를 찾는다. */
    private Element findBenefitTab(String cardCode, Elements tabs) {
        for (Element tab : tabs) {
            if (!BENEFIT_TAB_LABEL.equals(tab.text().trim())) {
                continue;
            }
            // href="#tabCon01" → id="tabCon01"
            String anchorId = tab.attr("href").substring(1);
            Element body = tabs.first().ownerDocument().getElementById(anchorId);
            if (body == null) {
                throw new CrawlException(
                        "탭은 있는데 본문 #" + anchorId + "이 없습니다: cardCode=" + cardCode);
            }
            return body;
        }
        throw new CrawlException("'" + BENEFIT_TAB_LABEL + "' 탭이 없습니다: cardCode=" + cardCode
                + ", 있는 탭=" + tabs.eachText());
    }

    /**
     * 카드명.
     *
     * <p>{@code <title>}은 전 카드가 {@code 카드상품/안내>카드 | …}로 같고 {@code og:title}은
     * 없다. 남는 건 {@code h1.tit}뿐이다. 못 찾아도 실패로 만들지 않는다 —
     * {@code crawl_raw_card.card_name}이 NULL 허용이고, 이름 하나 때문에 원문을 통째로
     * 버리는 건 손해다.
     */
    private String findCardName(Document document) {
        Element name = document.selectFirst(CARD_NAME_SELECTOR);
        if (name == null) {
            return null;
        }
        String text = name.text().trim();
        return text.isEmpty() ? null : text;
    }
}
