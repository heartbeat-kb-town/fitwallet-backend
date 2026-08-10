package com.fitwallet.batch.crawl.service;

import com.fitwallet.batch.crawl.client.CrawlHttpClient;
import com.fitwallet.batch.crawl.dto.RawCardBenefit;
import com.fitwallet.batch.crawl.exception.CrawlException;
import com.fitwallet.batch.crawl.exception.StubResponseException;
import com.fitwallet.batch.crawl.mapper.CrawlRawCardMapper;
import com.fitwallet.batch.crawl.spi.IssuerCrawler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * 오케스트레이션이 카드사를 모른다는 것을 가짜 어댑터로 검증한다.
 *
 * <p>실제 카드사 어댑터가 하나도 없는 상태에서도 이 테스트가 도는 것 자체가,
 * 공통 골격이 어댑터에 의존하지 않는다는 증거다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultCrawlServiceTest {

    private static final String ISSUER_NAME = "테스트카드";
    private static final Long ISSUER_ID = 3L;

    @Mock
    private CrawlHttpClient httpClient;

    @Mock
    private RawCardWriter rawCardWriter;

    @Mock
    private CrawlRawCardMapper crawlRawCardMapper;

    private DefaultCrawlService crawlService;

    @BeforeEach
    void setUp() {
        crawlService = new DefaultCrawlService(httpClient, rawCardWriter, crawlRawCardMapper);
        given(crawlRawCardMapper.findIssuerIdByName(ISSUER_NAME)).willReturn(ISSUER_ID);
        given(httpClient.get(anyString())).willReturn("<html>본문</html>");
        given(rawCardWriter.save(anyLong(), any())).willReturn(1);
    }

    @Test
    void 열거된_카드를_전부_수집한다() {
        CrawlResult result = crawlService.crawl(crawler(Set.of("A", "B", "C"), this::benefit), 0);

        assertThat(result.getIssuerName()).isEqualTo(ISSUER_NAME);
        assertThat(result.getEnumeratedCount()).isEqualTo(3);
        assertThat(result.getSucceededCount()).isEqualTo(3);
        assertThat(result.getFailedCount()).isZero();
        assertThat(result.getStubCount()).isZero();
    }

    @Test
    void limit이_있으면_그만큼만_수집한다() {
        CrawlResult result = crawlService.crawl(crawler(ordered("A", "B", "C"), this::benefit), 2);

        assertThat(result.getEnumeratedCount()).isEqualTo(3);
        assertThat(result.getSucceededCount()).isEqualTo(2);
    }

    @Test
    void 껍데기_응답은_따로_센다() {
        IssuerCrawler crawler = crawler(ordered("A", "B"), code -> {
            if ("B".equals(code)) {
                throw new StubResponseException("본문이 1.1KB입니다");
            }
            return benefit(code);
        });

        CrawlResult result = crawlService.crawl(crawler, 0);

        assertThat(result.getSucceededCount()).isEqualTo(1);
        assertThat(result.getStubCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getFailedCardCodes()).containsExactly("B");
    }

    @Test
    void 카드_하나가_실패해도_나머지는_계속_수집한다() {
        IssuerCrawler crawler = crawler(ordered("A", "B", "C"), code -> {
            if ("B".equals(code)) {
                throw new CrawlException("파싱 실패");
            }
            return benefit(code);
        });

        CrawlResult result = crawlService.crawl(crawler, 0);

        assertThat(result.getSucceededCount()).isEqualTo(2);
        assertThat(result.getFailedCardCodes()).containsExactly("B");
    }

    @Test
    void 추출된_원문이_없으면_성공으로_세지_않는다() {
        CrawlResult result = crawlService.crawl(crawler(Set.of("A"), code -> null), 0);

        assertThat(result.getSucceededCount()).isZero();
        assertThat(result.getFailedCardCodes()).containsExactly("A");
    }

    @Test
    void issuer_테이블에_없는_카드사면_예외를_던진다() {
        given(crawlRawCardMapper.findIssuerIdByName(ISSUER_NAME)).willReturn(null);

        assertThatThrownBy(() -> crawlService.crawl(crawler(Set.of("A"), this::benefit), 0))
                .isInstanceOf(CrawlException.class)
                .hasMessageContaining(ISSUER_NAME);
    }

    private Set<String> ordered(String... codes) {
        return new LinkedHashSet<>(List.of(codes));
    }

    private RawCardBenefit benefit(String cardCode) {
        return RawCardBenefit.builder()
                .cardCode(cardCode)
                .cardName("카드 " + cardCode)
                .sourceUrl("https://example.test/" + cardCode)
                .rawText("상세혜택 원문 " + cardCode)
                .build();
    }

    /** 카드사 어댑터 자리에 꽂는 가짜 구현. */
    private IssuerCrawler crawler(Set<String> codes, Function<String, RawCardBenefit> parser) {
        return new IssuerCrawler() {
            @Override
            public String issuerName() {
                return ISSUER_NAME;
            }

            @Override
            public Set<String> collectCardCodes() {
                return codes;
            }

            @Override
            public String cardDetailUrl(String cardCode) {
                return "https://example.test/" + cardCode;
            }

            @Override
            public RawCardBenefit parse(String cardCode, String html) {
                return parser.apply(cardCode);
            }
        };
    }
}
