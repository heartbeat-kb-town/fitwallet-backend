package com.fitwallet.batch.crawl.mapper;

import com.fitwallet.batch.crawl.dto.CrawlRawCardRequest;
import com.fitwallet.batch.crawl.dto.SectionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code crawl_raw_card} 적재 SQL을 실제 MySQL로 검증한다. */
@SpringJUnitConfig(locations = "classpath:root-context.xml")
@Transactional
class CrawlRawCardMapperIntegrationTest {

    private static final String KB = "KB국민카드";
    private static final LocalDateTime FETCHED_AT = LocalDateTime.of(2026, 8, 10, 3, 0, 0);

    @Autowired
    private CrawlRawCardMapper crawlRawCardMapper;

    @Test
    void 카드사_이름으로_issuer_id를_찾는다() {
        Long issuerId = crawlRawCardMapper.findIssuerIdByName(KB);

        assertThat(issuerId).isNotNull();
    }

    @Test
    void 없는_카드사_이름이면_null을_돌려준다() {
        assertThat(crawlRawCardMapper.findIssuerIdByName("없는카드")).isNull();
    }

    @Test
    void 원문_한_건을_적재한다() {
        Long issuerId = crawlRawCardMapper.findIssuerIdByName(KB);

        int affected = crawlRawCardMapper.insertRawCard(request(issuerId, "99001", SectionType.DETAIL, "상세혜택 원문"));

        assertThat(affected).isEqualTo(1);
    }

    @Test
    void 같은_카드의_다른_섹션은_별개_행으로_쌓인다() {
        Long issuerId = crawlRawCardMapper.findIssuerIdByName(KB);
        int before = crawlRawCardMapper.countByIssuerId(issuerId);

        crawlRawCardMapper.insertRawCard(request(issuerId, "99002", SectionType.SUMMARY, "주요혜택"));
        crawlRawCardMapper.insertRawCard(request(issuerId, "99002", SectionType.DETAIL, "상세혜택"));
        crawlRawCardMapper.insertRawCard(request(issuerId, "99002", SectionType.ANNUAL_FEE, "연회비"));

        assertThat(crawlRawCardMapper.countByIssuerId(issuerId)).isEqualTo(before + 3);
    }

    @Test
    void 같은_카드의_같은_섹션을_다시_적재하면_행이_늘지_않는다() {
        Long issuerId = crawlRawCardMapper.findIssuerIdByName(KB);
        crawlRawCardMapper.insertRawCard(request(issuerId, "99003", SectionType.DETAIL, "처음 원문"));
        int after1st = crawlRawCardMapper.countByIssuerId(issuerId);

        crawlRawCardMapper.insertRawCard(request(issuerId, "99003", SectionType.DETAIL, "바뀐 원문"));

        assertThat(crawlRawCardMapper.countByIssuerId(issuerId)).isEqualTo(after1st);
    }

    @Test
    void 같은_카드의_같은_섹션을_다시_적재하면_최신_해시로_덮어쓴다() {
        Long issuerId = crawlRawCardMapper.findIssuerIdByName(KB);
        crawlRawCardMapper.insertRawCard(request(issuerId, "99004", SectionType.DETAIL, "처음 원문"));

        crawlRawCardMapper.insertRawCard(CrawlRawCardRequest.builder()
                .issuerId(issuerId)
                .externalCardCode("99004")
                .cardName("바뀐 카드명")
                .section(SectionType.DETAIL)
                .sourceUrl("https://example.test/card/99004")
                .rawText("바뀐 원문")
                .contentHash("bbbb")
                .fetchedAt(FETCHED_AT)
                .build());

        List<String> hashes = crawlRawCardMapper.findContentHashesByIssuerId(issuerId);
        assertThat(hashes).contains("bbbb").doesNotContain("aaaa");
    }

    private CrawlRawCardRequest request(Long issuerId, String cardCode, SectionType section, String rawText) {
        return CrawlRawCardRequest.builder()
                .issuerId(issuerId)
                .externalCardCode(cardCode)
                .cardName("테스트 카드")
                .section(section)
                .sourceUrl("https://example.test/card/" + cardCode)
                .rawText(rawText)
                .contentHash("aaaa")
                .fetchedAt(FETCHED_AT)
                .build();
    }
}
