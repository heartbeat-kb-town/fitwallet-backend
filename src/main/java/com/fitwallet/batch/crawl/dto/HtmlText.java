package com.fitwallet.batch.crawl.dto;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;

/**
 * HTML 조각 → 순수 텍스트.
 *
 * <p><b>카드사 어댑터가 공유한다.</b> {@link ContentHash}와 같은 이유다 — 추출 방식이
 * 카드사마다 다르면 같은 원문에서 다른 문자열이 나오고, {@code content_hash} 비교로
 * "안 바뀐 카드"를 가려내는 것 자체가 무의미해진다.
 *
 * <p><b>jsoup의 {@code Element.text()}를 그대로 쓰지 않는다.</b> 그건 모든 공백을 스페이스
 * 하나로 접어서 표의 행 경계가 사라진다. 카드 혜택 원문에서 제일 중요한 게 전월실적 구간표
 * (구간 → 적립률 → 한도)인데, 한 줄로 뭉개지면 다음 단계에서 어느 값이 어느 구간의 것인지
 * 복원할 수 없다. 그래서 블록 요소와 표의 행·칸에서 줄바꿈을 넣는다.
 *
 * <pre>
 * &lt;tr&gt;&lt;td&gt;30만원 이상&lt;/td&gt;&lt;td&gt;1%&lt;/td&gt;&lt;/tr&gt;
 * &lt;tr&gt;&lt;td&gt;50만원 이상&lt;/td&gt;&lt;td&gt;2%&lt;/td&gt;&lt;/tr&gt;
 *
 * text()   →  "30만원 이상 1% 50만원 이상 2%"      (구간이 붙어버린다)
 * of()     →  "30만원 이상 | 1%
 *              50만원 이상 | 2%"
 * </pre>
 */
public final class HtmlText {

    /** 줄바꿈을 넣을 블록 요소. */
    private static final String BLOCK_TAGS =
            "p,div,section,article,header,footer,h1,h2,h3,h4,h5,h6,"
                    + "ul,ol,li,dl,dt,dd,table,thead,tbody,tr,caption,br,hr";

    /** 같은 행 안의 칸 구분자. 줄바꿈으로 나누면 표가 세로로 늘어져 구간과 값이 다시 끊긴다. */
    private static final String CELL_SEPARATOR = " | ";

    private HtmlText() {
    }

    /** {@code null}이면 빈 문자열을 돌려준다. */
    public static String of(Element element) {
        if (element == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        element.traverse(new TextCollector(out));
        return normalize(out.toString());
    }

    private static final class TextCollector implements NodeVisitor {

        private final StringBuilder out;

        private TextCollector(StringBuilder out) {
            this.out = out;
        }

        @Override
        public void head(Node node, int depth) {
            if (node instanceof TextNode textNode) {
                String text = textNode.text().trim();
                if (!text.isEmpty()) {
                    out.append(text).append(' ');
                }
            }
        }

        @Override
        public void tail(Node node, int depth) {
            if (!(node instanceof Element element)) {
                return;
            }
            String tag = element.tagName();
            if ("td".equals(tag) || "th".equals(tag)) {
                out.append(CELL_SEPARATOR);
            } else if (isBlock(tag)) {
                out.append('\n');
            }
        }

        private boolean isBlock(String tag) {
            return (',' + BLOCK_TAGS + ',').contains(',' + tag + ',');
        }
    }

    /**
     * 빈 줄과 늘어진 구분자를 걷어낸다.
     *
     * <p>카드사 페이지는 레이아웃용 빈 {@code div}가 많아 정리하지 않으면 원문의 절반이
     * 빈 줄이 된다. 그러면 {@code content_hash}가 내용과 무관한 마크업 변경에도 바뀐다.
     */
    private static String normalize(String raw) {
        String[] lines = raw.split("\n");
        StringBuilder out = new StringBuilder();

        for (String line : lines) {
            String cleaned = line
                    .replace(' ', ' ')            // &nbsp;
                    .replaceAll("[ \t]+", " ")
                    .replaceAll("(?:\\s*\\|\\s*)+", CELL_SEPARATOR)
                    .trim();
            // 칸이 전부 비어 " | " 만 남은 줄은 버린다.
            cleaned = cleaned.replaceAll("^\\|\\s*|\\s*\\|$", "").trim();
            if (!cleaned.isEmpty()) {
                out.append(cleaned).append('\n');
            }
        }
        return out.toString().trim();
    }
}
