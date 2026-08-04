package com.fitwallet.batch.kb.parser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fitwallet.batch.kb.client.KbCrawlEndpoints;
import com.fitwallet.batch.kb.dto.KbRawSection;
import com.fitwallet.batch.kb.dto.SectionType;
import com.fitwallet.batch.kb.exception.KbCrawlException;
import com.fitwallet.batch.kb.exception.KbStubResponseException;

/**
 * 카드 상세 HTML에서 혜택 원문만 잘라내는 단계(파이프라인 ③).
 *
 * <p><b>이 클래스는 네트워크를 모른다.</b> 입력이 문자열이라 저장해 둔 HTML만으로 테스트할 수
 * 있고, 파서를 고칠 때 KB 서버를 다시 치지 않아도 된다. 수집(②)과 추출(③)을 나눈 이유가 이것이다.
 *
 * <p>하는 일은 "필요한 탭만 고르기"다. 상세 페이지 337KB 중 혜택은 1~4KB뿐이고 나머지는
 * GNB·헤더·푸터·법적 고지문이다(실측 감축률 약 99%).
 *
 * <p>탭을 <b>번호가 아니라 라벨로</b> 찾는 것이 핵심이다({@link SectionType} 참고).
 * 카드마다 부가 탭이 있어 {@code tabCon02}가 어떤 카드는 연회비, 어떤 카드는 "플래티늄"이다.
 *
 * <p>실패는 조용히 넘기지 않는다. KB는 파라미터가 빠져도 HTTP 200을 주기 때문에
 * ({@link KbStubResponseException} 참고) 본문을 직접 확인해야 빈 문서가 쌓이는 걸 막을 수 있다.
 */
@Component
public class KbCardPageParser {

    private static final Logger log = LoggerFactory.getLogger(KbCardPageParser.class);

    /**
     * 정상 상세 페이지는 실측 387~500KB다. 스텁은 1.1KB.
     * 두 자릿수 이상 차이가 나므로 경계를 어디에 둬도 되지만, KB가 페이지를 가볍게 만들
     * 여지를 남겨 넉넉히 잡는다.
     */
    private static final int MIN_DOCUMENT_LENGTH = 10_000;

    /**
     * 최상위 탭 목록. {@code <li id="topTab0"><a href="#tabCon00"><span>주요혜택</span></a></li>}
     *
     * <p>{@code topTab} 접두사로 한정하는 게 중요하다. 페이지 안쪽엔 같은 모양의 하위 탭
     * ({@code #tabCon020} 등)이 또 있어서, 그냥 {@code a[href^=#tabCon]}으로 잡으면
     * "쿠폰서비스"·"Master 서비스" 같은 하위 탭까지 딸려온다.
     */
    private static final String TOP_TAB_LINK_SELECTOR = "li[id^=topTab] > a[href^=#tabCon]";

    /**
     * 상세 HTML을 섹션별 원문으로 쪼갠다.
     *
     * @param cardCode 제휴코드
     * @param html     상세 페이지 응답 본문
     * @return 뽑힌 섹션들(그 카드에 없는 탭은 빠진다)
     * @throws KbStubResponseException 내용 없는 껍데기 응답일 때
     */
    public List<KbRawSection> parse(String cardCode, String html) {
        if (html == null || html.length() < MIN_DOCUMENT_LENGTH) {
            throw new KbStubResponseException(
                    "카드 %s 응답이 %d바이트로 너무 짧습니다. cooperationcode 누락이거나 페이지 구조가 바뀌었습니다."
                            .formatted(cardCode, html == null ? 0 : html.length()));
        }

        Document document = Jsoup.parse(html, KbCrawlEndpoints.BASE);
        document.select("script, style").remove();

        Map<SectionType, String> tabIds = resolveTabIds(document);
        if (!tabIds.containsKey(SectionType.DETAIL)) {
            throw new KbStubResponseException(
                    "카드 %s 응답에서 '%s' 탭을 찾지 못했습니다. cooperationcode 누락이거나 페이지 구조가 바뀌었습니다."
                            .formatted(cardCode, SectionType.DETAIL.getTabLabel()));
        }

        String cardName = extractCardName(document);
        String sourceUrl = KbCrawlEndpoints.cardDetailUrl(cardCode);

        List<KbRawSection> sections = new ArrayList<>();
        for (SectionType section : SectionType.values()) {
            String tabId = tabIds.get(section);
            if (tabId == null) {
                log.debug("카드 {} — '{}' 탭 없음, 건너뜁니다.", cardCode, section.getTabLabel());
                continue;
            }
            Element element = document.getElementById(tabId);
            if (element == null) {
                log.debug("카드 {} — '{}' 탭이 가리키는 #{} 영역이 없습니다.",
                        cardCode, section.getTabLabel(), tabId);
                continue;
            }
            String rawText = normalize(element.text());
            if (rawText.isEmpty()) {
                log.debug("카드 {} — '{}' 영역이 비어 있어 건너뜁니다.", cardCode, section.getTabLabel());
                continue;
            }
            sections.add(KbRawSection.builder()
                    .cardCode(cardCode)
                    .cardName(cardName)
                    .section(section)
                    .sourceUrl(sourceUrl)
                    .rawText(rawText)
                    .contentHash(sha256(rawText))
                    .build());
        }

        if (sections.isEmpty()) {
            throw new KbStubResponseException(
                    "카드 %s에서 추출된 영역이 하나도 없습니다.".formatted(cardCode));
        }
        return sections;
    }

    /**
     * 최상위 탭 목록을 읽어 "이 카드에서 각 영역이 몇 번 탭인지"를 만든다.
     *
     * <p>같은 라벨이 여러 번 나오면 <b>처음 것</b>을 쓴다. 부가 탭이 많은 카드에서
     * 하위 영역이 같은 이름을 다시 쓰는 경우가 있다.
     */
    private Map<SectionType, String> resolveTabIds(Document document) {
        Map<SectionType, String> tabIds = new EnumMap<>(SectionType.class);
        Elements tabLinks = document.select(TOP_TAB_LINK_SELECTOR);

        for (Element link : tabLinks) {
            String label = normalize(link.text());
            String href = link.attr("href");
            if (href.length() <= 1) {
                continue;
            }
            SectionType.fromTabLabel(label)
                    .ifPresent(section -> tabIds.putIfAbsent(section, href.substring(1)));
        }
        return tabIds;
    }

    /**
     * 카드명은 문서의 <b>첫 번째</b> {@code <h1>}이다(카드 6종 실측: 예다함 카드 / ALL 카드 /
     * 굿데이카드 / 스카이패스 KB국민 플래티늄카드 / myOne KB국민카드 / 첵첵 체크카드).
     *
     * <p>뒤쪽 {@code <h1>}들은 "신속발급서비스", "모바일단독카드 안내" 같은 팝업 제목이라
     * 첫 번째만 쓴다. {@code <title>}은 전 카드가 "카드상품/안내&gt;카드 | ..."로 같아서 못 쓴다.
     */
    private String extractCardName(Document document) {
        Element heading = document.selectFirst("h1");
        if (heading == null) {
            return null;
        }
        String name = normalize(heading.text());
        return name.isEmpty() ? null : name;
    }

    /** 연속 공백·개행을 공백 하나로 접는다. 원문의 글자는 건드리지 않는다. */
    private String normalize(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    private String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 JDK가 반드시 제공한다. 여기 오면 JVM이 깨진 것이다.
            throw new KbCrawlException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}
