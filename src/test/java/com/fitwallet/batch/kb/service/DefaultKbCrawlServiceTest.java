package com.fitwallet.batch.kb.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fitwallet.batch.kb.client.KbHttpClient;
import com.fitwallet.batch.kb.collector.KbCardCodeCollector;
import com.fitwallet.batch.kb.dto.KbRawSection;
import com.fitwallet.batch.kb.dto.SectionType;
import com.fitwallet.batch.kb.exception.KbCrawlException;
import com.fitwallet.batch.kb.exception.KbStubResponseException;
import com.fitwallet.batch.kb.mapper.CrawlRawCardMapper;
import com.fitwallet.batch.kb.parser.KbCardPageParser;

@ExtendWith(MockitoExtension.class)
@DisplayName("KB 혜택 원문 수집 서비스")
class DefaultKbCrawlServiceTest {

    private static final Long ISSUER_ID = 3L;

    @Mock private KbCardCodeCollector cardCodeCollector;
    @Mock private KbHttpClient httpClient;
    @Mock private KbCardPageParser pageParser;
    @Mock private KbRawCardWriter rawCardWriter;
    @Mock private CrawlRawCardMapper crawlRawCardMapper;

    @InjectMocks private DefaultKbCrawlService service;

    @Test
    void 카드사가_등록돼_있지_않으면_예외를_던진다() {
        given(crawlRawCardMapper.findIssuerIdByName("KB국민카드")).willReturn(null);

        assertThatThrownBy(() -> service.crawl(0))
                .isInstanceOf(KbCrawlException.class)
                .hasMessageContaining("KB국민카드");

        // 카드사도 못 찾은 상태에서 KB 서버를 치면 안 된다.
        verify(cardCodeCollector, never()).collectCardCodes();
    }

    @Test
    void 열거된_카드마다_수집하고_적재한다() {
        givenIssuerExists();
        given(cardCodeCollector.collectCardCodes()).willReturn(codes("09061", "09922"));
        given(httpClient.get(anyString())).willReturn("<html>...</html>");
        given(pageParser.parse(anyString(), anyString())).willReturn(threeSections("09061"));
        given(rawCardWriter.save(eq(ISSUER_ID), anyList())).willReturn(3);

        KbCrawlResult result = service.crawl(0);

        assertThat(result.getEnumeratedCount()).isEqualTo(2);
        assertThat(result.getSucceededCount()).isEqualTo(2);
        assertThat(result.getSectionCount()).isEqualTo(6);
        assertThat(result.getStubCount()).isZero();
        assertThat(result.getFailedCount()).isZero();
        verify(rawCardWriter, times(2)).save(eq(ISSUER_ID), anyList());
    }

    @Test
    @DisplayName("limit을 주면 그만큼만 수집한다 — 스모크 실행용")
    void limit만큼만_수집한다() {
        givenIssuerExists();
        given(cardCodeCollector.collectCardCodes()).willReturn(codes("01", "02", "03", "04", "05"));
        given(httpClient.get(anyString())).willReturn("<html>...</html>");
        given(pageParser.parse(anyString(), anyString())).willReturn(threeSections("01"));
        given(rawCardWriter.save(eq(ISSUER_ID), anyList())).willReturn(3);

        KbCrawlResult result = service.crawl(2);

        assertThat(result.getEnumeratedCount()).isEqualTo(5);
        assertThat(result.getSucceededCount()).isEqualTo(2);
        verify(httpClient, times(2)).get(anyString());
    }

    @Test
    @DisplayName("껍데기 응답은 stubCount로 따로 센다 — 페이지 구조 변경 신호라 묻히면 안 된다")
    void 껍데기_응답은_따로_집계한다() {
        givenIssuerExists();
        given(cardCodeCollector.collectCardCodes()).willReturn(codes("09061", "00000"));
        given(httpClient.get(anyString())).willReturn("<html>...</html>");
        given(pageParser.parse(eq("09061"), anyString())).willReturn(threeSections("09061"));
        willThrow(new KbStubResponseException("카드 00000 응답이 1085바이트로 너무 짧습니다."))
                .given(pageParser).parse(eq("00000"), anyString());
        given(rawCardWriter.save(eq(ISSUER_ID), anyList())).willReturn(3);

        KbCrawlResult result = service.crawl(0);

        assertThat(result.getStubCount()).isEqualTo(1);
        assertThat(result.getSucceededCount()).isEqualTo(1);
        assertThat(result.getFailedCardCodes()).containsExactly("00000");
    }

    @Test
    @DisplayName("카드 하나가 실패해도 나머지는 계속 수집한다")
    void 개별_카드_실패가_전체를_멈추지_않는다() {
        givenIssuerExists();
        given(cardCodeCollector.collectCardCodes()).willReturn(codes("01", "02", "03"));
        willThrow(new KbCrawlException("KB 요청을 3회 시도했지만 실패했습니다."))
                .given(httpClient).get(contains("cooperationcode=02"));
        given(httpClient.get(contains("cooperationcode=01"))).willReturn("<html>...</html>");
        given(httpClient.get(contains("cooperationcode=03"))).willReturn("<html>...</html>");
        given(pageParser.parse(anyString(), anyString())).willReturn(threeSections("01"));
        given(rawCardWriter.save(eq(ISSUER_ID), anyList())).willReturn(3);

        KbCrawlResult result = service.crawl(0);

        assertThat(result.getSucceededCount()).isEqualTo(2);
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getStubCount()).isZero();
        assertThat(result.getFailedCardCodes()).containsExactly("02");
    }

    @Test
    void 수집할_카드가_없으면_아무것도_적재하지_않는다() {
        givenIssuerExists();
        given(cardCodeCollector.collectCardCodes()).willReturn(codes());

        KbCrawlResult result = service.crawl(0);

        assertThat(result.getSucceededCount()).isZero();
        assertThat(result.getSectionCount()).isZero();
        verify(rawCardWriter, never()).save(any(), anyList());
    }

    private void givenIssuerExists() {
        given(crawlRawCardMapper.findIssuerIdByName("KB국민카드")).willReturn(ISSUER_ID);
    }

    private Set<String> codes(String... values) {
        return new LinkedHashSet<>(List.of(values));
    }

    private List<KbRawSection> threeSections(String cardCode) {
        return List.of(
                section(cardCode, SectionType.SUMMARY),
                section(cardCode, SectionType.DETAIL),
                section(cardCode, SectionType.ANNUAL_FEE));
    }

    private KbRawSection section(String cardCode, SectionType type) {
        return KbRawSection.builder()
                .cardCode(cardCode)
                .cardName("테스트 카드")
                .section(type)
                .sourceUrl("https://card.kbcard.com/...cooperationcode=" + cardCode)
                .rawText(type + " 원문")
                .contentHash("0".repeat(64))
                .build();
    }
}
