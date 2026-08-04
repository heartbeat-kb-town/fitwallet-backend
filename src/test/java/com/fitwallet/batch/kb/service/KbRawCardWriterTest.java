package com.fitwallet.batch.kb.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fitwallet.batch.kb.dto.CrawlRawCardRequest;
import com.fitwallet.batch.kb.dto.KbRawSection;
import com.fitwallet.batch.kb.dto.SectionType;
import com.fitwallet.batch.kb.mapper.CrawlRawCardMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("혜택 원문 적재")
class KbRawCardWriterTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock private CrawlRawCardMapper crawlRawCardMapper;
    @Captor private ArgumentCaptor<CrawlRawCardRequest> requestCaptor;

    @Test
    void 섹션마다_한_행씩_적재한다() {
        KbRawCardWriter writer = new KbRawCardWriter(crawlRawCardMapper, fixedClock());

        int saved = writer.save(3L, List.of(
                section(SectionType.SUMMARY), section(SectionType.DETAIL)));

        assertThat(saved).isEqualTo(2);
        verify(crawlRawCardMapper, org.mockito.Mockito.times(2)).insertRawCard(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues())
                .extracting(CrawlRawCardRequest::getSection)
                .containsExactly(SectionType.SUMMARY, SectionType.DETAIL);
    }

    @Test
    void 한_카드의_섹션들은_같은_수집시각을_공유한다() {
        KbRawCardWriter writer = new KbRawCardWriter(crawlRawCardMapper, fixedClock());

        writer.save(3L, List.of(
                section(SectionType.SUMMARY), section(SectionType.DETAIL), section(SectionType.ANNUAL_FEE)));

        verify(crawlRawCardMapper, org.mockito.Mockito.times(3)).insertRawCard(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues())
                .extracting(CrawlRawCardRequest::getFetchedAt)
                .containsOnly(LocalDateTime.of(2026, 8, 4, 10, 30));
    }

    @Test
    @DisplayName("수집 시각은 데모용 고정 clock 빈이 아니라 실제 시계를 쓴다")
    void 수집시각은_실제_시계를_따른다() {
        // 기본 생성자(= 스프링이 쓰는 경로)는 시스템 시계를 물린다. 공용 clock 빈은 로컬에서
        // 2026-07-24로 고정돼 있어, 그걸 쓰면 몇 번을 돌려도 fetched_at이 같아진다.
        LocalDateTime before = LocalDateTime.now(KST).minusMinutes(1);
        KbRawCardWriter writer = new KbRawCardWriter(crawlRawCardMapper);

        writer.save(3L, List.of(section(SectionType.DETAIL)));

        verify(crawlRawCardMapper).insertRawCard(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getFetchedAt())
                .isAfter(before)
                .isBefore(LocalDateTime.now(KST).plusMinutes(1));
    }

    private Clock fixedClock() {
        return Clock.fixed(
                LocalDateTime.of(2026, 8, 4, 10, 30).atZone(KST).toInstant(), KST);
    }

    private KbRawSection section(SectionType type) {
        return KbRawSection.builder()
                .cardCode("09061")
                .cardName("굿데이카드")
                .section(type)
                .sourceUrl("https://card.kbcard.com/...cooperationcode=09061")
                .rawText(type + " 원문")
                .contentHash("0".repeat(64))
                .build();
    }
}
