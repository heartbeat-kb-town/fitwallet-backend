package com.fitwallet.batch.crawl.service;

import com.fitwallet.batch.crawl.dto.ContentHash;
import com.fitwallet.batch.crawl.dto.CrawlRawCardRequest;
import com.fitwallet.batch.crawl.dto.RawCardBenefit;
import com.fitwallet.batch.crawl.mapper.CrawlRawCardMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RawCardWriterTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Long ISSUER_ID = 3L;

    @Mock
    private CrawlRawCardMapper crawlRawCardMapper;

    @Captor
    private ArgumentCaptor<CrawlRawCardRequest> requestCaptor;

    @Test
    void 카드_한_장을_한_행으로_적재한다() {
        RawCardWriter writer = new RawCardWriter(crawlRawCardMapper, fixedClock());

        int saved = writer.save(ISSUER_ID, benefit("상세혜택 원문", null));

        assertThat(saved).isEqualTo(1);
        verify(crawlRawCardMapper).insertRawCard(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getExternalCardCode()).isEqualTo("04485");
    }

    @Test
    void 어댑터가_해시를_비워두면_원문에서_계산해_채운다() {
        RawCardWriter writer = new RawCardWriter(crawlRawCardMapper, fixedClock());

        writer.save(ISSUER_ID, benefit("상세혜택 원문", null));

        verify(crawlRawCardMapper).insertRawCard(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getContentHash())
                .isEqualTo(ContentHash.of("상세혜택 원문"));
    }

    @Test
    void 어댑터가_해시를_넣었으면_그대로_쓴다() {
        RawCardWriter writer = new RawCardWriter(crawlRawCardMapper, fixedClock());

        writer.save(ISSUER_ID, benefit("상세혜택 원문", "미리계산한해시"));

        verify(crawlRawCardMapper).insertRawCard(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getContentHash()).isEqualTo("미리계산한해시");
    }

    /**
     * 공용 {@code clock} 빈은 로컬에서 날짜가 고정돼 있어, 며칠 간격으로 두 번 돌려도 같은
     * 시각이 찍힌다. 그러면 "이번 실행에서 안 나온 카드 = 단종 후보"를 가려낼 근거가 사라진다.
     * 되돌려지지 않게 못 박는다.
     */
    @Test
    void 수집시각은_실제_시계를_따른다() {
        RawCardWriter writer = new RawCardWriter(crawlRawCardMapper);
        LocalDateTime before = LocalDateTime.now(SEOUL);

        writer.save(ISSUER_ID, benefit("상세혜택", null));

        verify(crawlRawCardMapper).insertRawCard(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getFetchedAt())
                .isBetween(before.minusMinutes(1), LocalDateTime.now(SEOUL).plusMinutes(1));
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-08-10T03:00:00Z"), SEOUL);
    }

    private RawCardBenefit benefit(String rawText, String contentHash) {
        return RawCardBenefit.builder()
                .cardCode("04485")
                .cardName("테스트 카드")
                .sourceUrl("https://example.test/card/04485")
                .rawText(rawText)
                .contentHash(contentHash)
                .build();
    }
}
