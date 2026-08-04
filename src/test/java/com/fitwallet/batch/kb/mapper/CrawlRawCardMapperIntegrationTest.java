package com.fitwallet.batch.kb.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

import com.fitwallet.batch.kb.dto.CrawlRawCardRequest;
import com.fitwallet.batch.kb.dto.SectionType;

/**
 * Mapper 통합 테스트. docker compose로 띄운 실제 MySQL을 쓴다.
 *
 * <p>클래스 레벨 {@code @Transactional}이 테스트마다 롤백하므로 데이터를 넣어도 된다.
 */
@SpringJUnitConfig(locations = "classpath:root-context.xml")
@Transactional
@DisplayName("crawl_raw_card 매퍼")
class CrawlRawCardMapperIntegrationTest {

    private static final String KB = "KB국민카드";
    private static final String CARD_CODE = "09061";
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @Autowired private CrawlRawCardMapper mapper;

    /**
     * 저장 결과를 컬럼 단위로 들여다보기 위한 용도. 루트 컨텍스트에 JdbcTemplate 빈이 없어
     * DataSource에서 직접 만든다({@code BenefitMapperIntegrationTest}와 같은 방식).
     * DataSource가 테스트 트랜잭션에 묶여 있어 롤백 대상에 함께 들어간다.
     */
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp(@Autowired DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void 카드사_이름으로_issuer_id를_찾는다() {
        Long issuerId = mapper.findIssuerIdByName(KB);

        assertThat(issuerId).isNotNull().isPositive();
    }

    @Test
    void 없는_카드사_이름이면_null을_반환한다() {
        assertThat(mapper.findIssuerIdByName("없는카드사")).isNull();
    }

    @Test
    void 원문을_적재한다() {
        Long issuerId = mapper.findIssuerIdByName(KB);

        int affected = mapper.insertRawCard(request(issuerId, SectionType.DETAIL, "상세혜택 원문", HASH_A));

        assertThat(affected).isEqualTo(1);
    }

    @Test
    void 적재한_원문이_컬럼별로_그대로_저장된다() {
        Long issuerId = mapper.findIssuerIdByName(KB);
        LocalDateTime fetchedAt = LocalDateTime.of(2026, 8, 4, 10, 30, 0);
        mapper.insertRawCard(CrawlRawCardRequest.builder()
                .issuerId(issuerId)
                .externalCardCode(CARD_CODE)
                .cardName("굿데이카드")
                .section(SectionType.DETAIL)
                .sourceUrl("https://card.kbcard.com/CRD/DVIEW/HCAMCXPRICAC0076?mainCC=a&cooperationcode=09061")
                .rawText("1구간 (30만원 이상) 통신 10%")
                .contentHash(HASH_A)
                .fetchedAt(fetchedAt)
                .build());

        var row = jdbcTemplate.queryForMap(
                "SELECT card_name, section, raw_text, content_hash, fetched_at, created_at "
                        + "FROM crawl_raw_card WHERE issuer_id = ? AND external_card_code = ? AND section = ?",
                issuerId, CARD_CODE, SectionType.DETAIL.name());

        assertThat(row.get("card_name")).isEqualTo("굿데이카드");
        // enum 상수 이름이 DDL CHECK 값과 같아 기본 EnumTypeHandler가 그대로 변환한다.
        assertThat(row.get("section")).isEqualTo("DETAIL");
        assertThat(row.get("raw_text")).isEqualTo("1구간 (30만원 이상) 통신 10%");
        assertThat(row.get("content_hash")).isEqualTo(HASH_A);
        assertThat(row.get("fetched_at")).isNotNull();
        // created_at은 DB DEFAULT가 채운다 — INSERT 문에 없다.
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    @DisplayName("같은 카드의 같은 섹션을 다시 적재하면 행이 늘지 않고 덮어쓴다")
    void 같은_섹션_재적재는_행을_늘리지_않는다() {
        Long issuerId = mapper.findIssuerIdByName(KB);
        mapper.insertRawCard(request(issuerId, SectionType.DETAIL, "예전 원문", HASH_A));

        mapper.insertRawCard(request(issuerId, SectionType.DETAIL, "새 원문", HASH_B));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM crawl_raw_card WHERE issuer_id = ? AND external_card_code = ? AND section = ?",
                Integer.class, issuerId, CARD_CODE, SectionType.DETAIL.name());
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void 재적재하면_원문과_해시가_새_값으로_바뀐다() {
        Long issuerId = mapper.findIssuerIdByName(KB);
        mapper.insertRawCard(request(issuerId, SectionType.DETAIL, "예전 원문", HASH_A));

        mapper.insertRawCard(request(issuerId, SectionType.DETAIL, "새 원문", HASH_B));

        var row = jdbcTemplate.queryForMap(
                "SELECT raw_text, content_hash FROM crawl_raw_card "
                        + "WHERE issuer_id = ? AND external_card_code = ? AND section = ?",
                issuerId, CARD_CODE, SectionType.DETAIL.name());
        assertThat(row.get("raw_text")).isEqualTo("새 원문");
        assertThat(row.get("content_hash")).isEqualTo(HASH_B);
    }

    @Test
    @DisplayName("같은 카드라도 섹션이 다르면 별개 행이다")
    void 섹션이_다르면_별개_행으로_쌓인다() {
        Long issuerId = mapper.findIssuerIdByName(KB);

        mapper.insertRawCard(request(issuerId, SectionType.SUMMARY, "주요혜택", HASH_A));
        mapper.insertRawCard(request(issuerId, SectionType.DETAIL, "상세혜택", HASH_B));
        mapper.insertRawCard(request(issuerId, SectionType.ANNUAL_FEE, "연회비", HASH_A));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM crawl_raw_card WHERE issuer_id = ? AND external_card_code = ?",
                Integer.class, issuerId, CARD_CODE);
        assertThat(rows).isEqualTo(3);
    }

    @Test
    void 카드사_단위로_해시_목록을_가져온다() {
        Long issuerId = mapper.findIssuerIdByName(KB);
        mapper.insertRawCard(request(issuerId, SectionType.SUMMARY, "주요혜택", HASH_A));
        mapper.insertRawCard(request(issuerId, SectionType.DETAIL, "상세혜택", HASH_B));

        List<String> hashes = mapper.findContentHashesByIssuerId(issuerId);

        assertThat(hashes).contains(HASH_A, HASH_B);
    }

    @Test
    void 카드사_단위로_적재_건수를_센다() {
        // §11: "조회 -> 변경 -> 같은 조회"를 하지 않는다. 적재 전 건수를 읽지 않고,
        // 방금 넣은 3건이 카드사 집계에 잡히는지만 확인한다(다른 카드가 이미 있을 수 있다).
        Long issuerId = mapper.findIssuerIdByName(KB);
        mapper.insertRawCard(request(issuerId, SectionType.SUMMARY, "주요혜택", HASH_A));
        mapper.insertRawCard(request(issuerId, SectionType.DETAIL, "상세혜택", HASH_B));
        mapper.insertRawCard(request(issuerId, SectionType.ANNUAL_FEE, "연회비", HASH_A));

        assertThat(mapper.countByIssuerId(issuerId)).isGreaterThanOrEqualTo(3);
    }

    private CrawlRawCardRequest request(Long issuerId, SectionType section, String rawText, String hash) {
        return CrawlRawCardRequest.builder()
                .issuerId(issuerId)
                .externalCardCode(CARD_CODE)
                .cardName("굿데이카드")
                .section(section)
                .sourceUrl("https://card.kbcard.com/CRD/DVIEW/HCAMCXPRICAC0076?mainCC=a&cooperationcode=09061")
                .rawText(rawText)
                .contentHash(hash)
                .fetchedAt(LocalDateTime.of(2026, 8, 4, 10, 30, 0))
                .build();
    }
}
