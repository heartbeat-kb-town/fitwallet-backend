package com.fitwallet.batch.issuer.kb;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.fitwallet.batch.crawl.dto.RawCardBenefit;
import com.fitwallet.batch.crawl.exception.StubResponseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KB 상세 페이지 파싱.
 *
 * <p>실제로 받아 저장해 둔 HTML로 돌린다 — 네트워크를 타지 않으므로 KB 서버가 죽어 있어도,
 * 오프라인이어도 통과해야 한다.
 *
 * <p>픽스처 둘은 <b>탭 구성이 서로 다른 카드</b>를 일부러 골랐다.
 *
 * <ul>
 *   <li>{@code card-01507}(신용, 탭 4개) — 주요혜택/상세혜택/연회비/확인사항</li>
 *   <li>{@code card-04124}(체크, 탭 6개) — 사이에 {@code Mastercard 서비스}가 끼어들고
 *       <b>연회비 탭이 아예 없다</b></li>
 * </ul>
 */
class KbCardPageParserTest {

    private static final String CREDIT_4TABS = "card-01507-4tabs.html";
    private static final String CHECK_6TABS = "card-04124-6tabs.html";

    private final KbCardPageParser parser = new KbCardPageParser();

    @Test
    void 상세혜택_탭의_원문을_뽑는다() {
        RawCardBenefit benefit = parse("01507", CREDIT_4TABS);

        assertThat(benefit.getCardCode()).isEqualTo("01507");
        assertThat(benefit.getCardName()).isEqualTo("KT 할부 Plus KB국민카드");
        assertThat(benefit.getSourceUrl()).contains("cooperationcode=01507");
        assertThat(benefit.getRawText()).isNotBlank();
    }

    @Test
    void 탭_개수가_달라도_라벨로_찾는다() {
        // 체크카드는 탭이 6개이고 중간에 카드 전용 탭이 끼어 있다. 인덱스로 잡으면 깨진다.
        RawCardBenefit benefit = parse("04124", CHECK_6TABS);

        assertThat(benefit.getCardName()).isEqualTo("Youth Club 체크카드");
        assertThat(benefit.getRawText()).isNotBlank();
    }

    @Test
    void 상세혜택이_아닌_탭의_내용은_섞이지_않는다() {
        String rawText = parse("04124", CHECK_6TABS).getRawText();

        // 확인사항(법적 고지문)과 해외이용(수수료율)은 혜택이 아니라 저장 대상이 아니다.
        assertThat(rawText)
                .doesNotContain("연체이자율")
                .doesNotContain("국제 브랜드별 안내");
    }

    @Test
    void 표의_행_경계가_보존된다() {
        // 전월실적 구간표가 한 줄로 뭉개지면 어느 값이 어느 구간의 것인지 복원할 수 없다.
        String rawText = parse("01507", CREDIT_4TABS).getRawText();

        assertThat(rawText).contains("\n");
        assertThat(rawText.lines().count()).isGreaterThan(1);
    }

    @Test
    void 카드_상세가_아닌_응답은_껍데기로_처리한다() {
        assertThatThrownBy(() -> parser.parse("99999", "https://example.test", "<html><body>점검 중</body></html>"))
                .isInstanceOf(StubResponseException.class)
                .hasMessageContaining("99999");
    }

    @Test
    void 빈_응답은_껍데기로_처리한다() {
        assertThatThrownBy(() -> parser.parse("99999", "https://example.test", ""))
                .isInstanceOf(StubResponseException.class);
    }

    private RawCardBenefit parse(String cardCode, String fixture) {
        String url = "https://card.kbcard.com/CRD/DVIEW/HCAMCXPRICAC0076?mainCC=a&cooperationcode=" + cardCode;
        return parser.parse(cardCode, url, readFixture(fixture));
    }

    private String readFixture(String fileName) {
        String path = "/fixtures/kb/" + fileName;
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("픽스처가 없습니다: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("픽스처를 읽지 못했습니다: " + path, e);
        }
    }
}
