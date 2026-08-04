package com.fitwallet.batch.kb.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fitwallet.batch.kb.dto.KbRawSection;
import com.fitwallet.batch.kb.dto.SectionType;
import com.fitwallet.batch.kb.exception.KbStubResponseException;

/**
 * 픽스처는 2026-08-04에 실제로 받은 응답에서 잘라낸 것이다. 네트워크를 타지 않으므로
 * KB가 죽어 있어도, 오프라인이어도 돈다.
 */
@DisplayName("KB 카드 상세 페이지 파서")
class KbCardPageParserTest {

    /** 굿데이카드. 전월실적 3구간 + 여러 업종 — 가장 복잡한 케이스. */
    private static final String CARD_09061 = "fixtures/kb/card-09061.html";
    /** 예다함 카드. 혜택 하나짜리 최소 케이스. */
    private static final String CARD_04485 = "fixtures/kb/card-04485.html";
    /**
     * 스카이패스 KB국민 플래티늄카드. 부가 탭("플래티늄")이 끼어 탭 번호가 밀린 케이스:
     * 00 주요혜택 | 01 상세혜택 | 02 플래티늄 | 03 연회비 | 04 확인사항
     */
    private static final String CARD_01515 = "fixtures/kb/card-01515.html";
    /** cooperationcode 없이 요청했을 때 오는 JS 리다이렉트 스텁. */
    private static final String STUB = "fixtures/kb/stub-response.html";

    private KbCardPageParser parser;

    @BeforeEach
    void setUp() {
        parser = new KbCardPageParser();
    }

    @Test
    void 상세혜택_섹션에_전월실적_구간_문구가_원문_그대로_남는다() {
        Map<SectionType, KbRawSection> sections = parseAsMap("09061", CARD_09061);

        String detail = sections.get(SectionType.DETAIL).getRawText();

        // 이 문구들이 benefit_tier.min_prev_month_spend 로 번역될 원본이다.
        assertThat(detail)
                .contains("1구간")
                .contains("30만원 이상")
                .contains("2구간")
                .contains("60만원 이상")
                .contains("3구간")
                .contains("120만원 이상");
    }

    @Test
    void 상세혜택_섹션에_할인율과_한도_문구가_남는다() {
        Map<SectionType, KbRawSection> sections = parseAsMap("09061", CARD_09061);

        String detail = sections.get(SectionType.DETAIL).getRawText();

        // value_type=RATE / value_number=10 의 원본
        assertThat(detail).contains("10%");
        // benefit_limit(AMOUNT, DAY) 의 원본
        assertThat(detail).contains("일 할인제공 이용금액 한도");
    }

    @Test
    @DisplayName("확인사항(#tabCon03) 영역은 어떤 섹션에도 섞이지 않는다")
    void 확인사항_영역은_추출_결과에_포함되지_않는다() {
        List<KbRawSection> sections = parse("09061", CARD_09061);

        // 확인사항 탭에만 나오는 문구들. 픽스처에는 이 영역이 통째로 들어 있으므로,
        // 추출 결과에 없다는 것은 파서가 그 탭을 실제로 건너뛰었다는 뜻이다.
        // 여기가 깨지면 KB가 마크업을 바꿔 확인사항이 딸려 들어오기 시작한 것이다.
        for (KbRawSection section : sections) {
            assertThat(section.getRawText())
                    .as("%s 섹션에 확인사항 보일러플레이트가 섞였다", section.getSection())
                    .doesNotContain("연체이자율")
                    .doesNotContain("제외매출")
                    .doesNotContain("최고 연 20%");
        }
    }

    @Test
    void 수집_대상_세_영역이_모두_추출된다() {
        List<KbRawSection> sections = parse("09061", CARD_09061);

        assertThat(sections)
                .extracting(KbRawSection::getSection)
                .containsExactly(SectionType.SUMMARY, SectionType.DETAIL, SectionType.ANNUAL_FEE);
    }

    @Test
    void 카드명은_문서의_첫번째_h1에서_가져온다() {
        List<KbRawSection> sections = parse("09061", CARD_09061);

        // 뒤쪽 h1들("신속발급서비스", "모바일 단독카드 연회비 반환기준")이 아니라 첫 번째여야 한다.
        assertThat(sections).allSatisfy(section ->
                assertThat(section.getCardName()).isEqualTo("굿데이카드"));
    }

    @Test
    void 혜택이_적은_카드도_세_영역이_모두_추출된다() {
        Map<SectionType, KbRawSection> sections = parseAsMap("04485", CARD_04485);

        assertThat(sections).containsOnlyKeys(
                SectionType.SUMMARY, SectionType.DETAIL, SectionType.ANNUAL_FEE);
        assertThat(sections.get(SectionType.DETAIL).getRawText()).contains("예다함");
    }

    @Test
    @DisplayName("부가 탭이 있어 탭 번호가 밀린 카드도 연회비를 제대로 찾는다")
    void 부가_탭이_있는_카드는_탭_번호가_아니라_라벨로_찾는다() {
        Map<SectionType, KbRawSection> sections = parseAsMap("01515", CARD_01515);

        // 이 카드는 tabCon02가 "플래티늄"이고 연회비는 tabCon03이다.
        // 번호로 매핑하던 시절엔 여기에 쿠폰서비스 2만 자가 들어왔다.
        String annualFee = sections.get(SectionType.ANNUAL_FEE).getRawText();

        assertThat(annualFee).contains("연회비");
        assertThat(annualFee)
                .as("연회비 자리에 '플래티늄' 탭 내용이 들어오면 안 된다")
                .doesNotContain("국내선 동반자 항공권");
    }

    @Test
    @DisplayName("수집 대상이 아닌 부가 탭(플래티늄/쿠폰서비스)은 아예 추출하지 않는다")
    void 부가_탭은_추출_대상이_아니다() {
        List<KbRawSection> sections = parse("01515", CARD_01515);

        assertThat(sections)
                .extracting(KbRawSection::getSection)
                .containsExactly(SectionType.SUMMARY, SectionType.DETAIL, SectionType.ANNUAL_FEE);
    }

    @Test
    @DisplayName("부가 탭이 있는 카드에서도 확인사항은 섞이지 않는다")
    void 부가_탭이_있는_카드도_확인사항이_섞이지_않는다() {
        List<KbRawSection> sections = parse("01515", CARD_01515);

        for (KbRawSection section : sections) {
            assertThat(section.getRawText())
                    .as("%s 섹션에 확인사항 보일러플레이트가 섞였다", section.getSection())
                    .doesNotContain("연체이자율")
                    .doesNotContain("제외매출");
        }
    }

    @Test
    @DisplayName("추출 결과가 원본보다 훨씬 작다 — 보일러플레이트가 안 딸려온다는 뜻")
    void 추출_텍스트는_원본_HTML보다_현저히_작다() {
        String html = readFixture(CARD_09061);

        int extracted = parse("09061", CARD_09061).stream()
                .mapToInt(section -> section.getRawText().length())
                .sum();

        // 실측: 원본 응답 357KB -> 추출 3.8KB(약 1%). 픽스처는 앞부분을 이미 잘라낸
        // 37KB짜리라 비율이 그만큼 올라가지만, 그래도 확인사항이 섞이면 곧바로 깨진다.
        assertThat(extracted).isLessThan(html.length() / 4);
    }

    @Test
    void 같은_원문이면_해시가_같고_다른_원문이면_다르다() {
        Map<SectionType, KbRawSection> first = parseAsMap("09061", CARD_09061);
        Map<SectionType, KbRawSection> second = parseAsMap("09061", CARD_09061);

        assertThat(first.get(SectionType.DETAIL).getContentHash())
                .isEqualTo(second.get(SectionType.DETAIL).getContentHash())
                .hasSize(64)
                .matches("[0-9a-f]{64}");

        assertThat(first.get(SectionType.DETAIL).getContentHash())
                .isNotEqualTo(first.get(SectionType.SUMMARY).getContentHash());
    }

    @Test
    void 출처_URL에_제휴코드가_담긴다() {
        List<KbRawSection> sections = parse("09061", CARD_09061);

        assertThat(sections).allSatisfy(section ->
                assertThat(section.getSourceUrl()).endsWith("cooperationcode=09061"));
    }

    @Test
    @DisplayName("cooperationcode 없이 받은 스텁 응답은 예외로 잡는다 — HTTP 200이라 조용히 지나가면 안 된다")
    void 껍데기_응답이면_예외를_던진다() {
        String stub = readFixture(STUB);

        assertThatThrownBy(() -> parser.parse("0000", stub))
                .isInstanceOf(KbStubResponseException.class)
                .hasMessageContaining("0000");
    }

    @Test
    void 본문이_null이어도_예외를_던진다() {
        assertThatThrownBy(() -> parser.parse("0000", null))
                .isInstanceOf(KbStubResponseException.class);
    }

    @Test
    void 길이는_충분하지만_상세혜택_탭이_없으면_예외를_던진다() {
        String html = "<html><body><h1>어떤 카드</h1>" + "가".repeat(20_000) + "</body></html>";

        assertThatThrownBy(() -> parser.parse("0000", html))
                .isInstanceOf(KbStubResponseException.class)
                .hasMessageContaining("상세혜택");
    }

    private List<KbRawSection> parse(String cardCode, String fixturePath) {
        return parser.parse(cardCode, readFixture(fixturePath));
    }

    private Map<SectionType, KbRawSection> parseAsMap(String cardCode, String fixturePath) {
        return parse(cardCode, fixturePath).stream()
                .collect(Collectors.toMap(KbRawSection::getSection, Function.identity()));
    }

    private String readFixture(String path) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("픽스처를 찾을 수 없습니다: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("픽스처를 읽지 못했습니다: " + path, e);
        }
    }
}
