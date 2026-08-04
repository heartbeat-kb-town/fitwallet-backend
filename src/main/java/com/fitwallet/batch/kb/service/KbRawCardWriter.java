package com.fitwallet.batch.kb.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitwallet.batch.kb.dto.CrawlRawCardRequest;
import com.fitwallet.batch.kb.dto.KbRawSection;
import com.fitwallet.batch.kb.mapper.CrawlRawCardMapper;

/**
 * 카드 한 장에서 뽑은 섹션들을 staging에 적재한다.
 *
 * <p><b>왜 별도 빈인가.</b> 두 가지 이유가 겹친다.
 *
 * <ol>
 *   <li>{@code @Transactional}은 프록시로 걸린다. {@link DefaultKbCrawlService} 안에서
 *       자기 메서드를 호출하면 프록시를 거치지 않아 트랜잭션이 조용히 안 걸린다.
 *       다른 빈으로 빼야 실제로 걸린다.</li>
 *   <li>네트워크 요청과 DB 쓰기를 같은 트랜잭션에 두면 HTTP 응답을 기다리는 내내
 *       커넥션을 붙잡는다. 수집은 트랜잭션 밖에서 하고, 여기서 쓰기만 짧게 묶는다.</li>
 * </ol>
 *
 * <p>경계를 카드 한 장으로 잡은 이유: 전체를 한 트랜잭션으로 묶으면 마지막 카드에서 실패했을 때
 * 앞의 수십 장이 통째로 롤백된다. 네트워크 상대가 있는 작업에서 그 손해가 너무 크다.
 * 카드 하나가 실패해도 나머지는 남는 편이 낫다. 반대로 <b>한 카드의 섹션들은 함께 저장되거나
 * 함께 실패해야</b> 하므로 그보다 잘게 쪼개지도 않는다.
 */
@Service
public class KbRawCardWriter {

    private static final ZoneId SERVICE_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final CrawlRawCardMapper crawlRawCardMapper;
    private final Clock clock;

    /**
     * <b>공용 {@code clock} 빈을 주입받지 않는다.</b> 그 빈은 로컬 프로파일에서
     * {@code clock.fixed-date=2026-07-24}로 고정돼 있다 — 데모 시드의 거래 날짜와
     * 화면의 "이번 달"을 맞추기 위한 장치다.
     *
     * <p>그런데 {@code fetched_at}은 그런 <i>업무상 날짜</i>가 아니라 "실제로 언제 KB를
     * 쳤는가"라는 운영 사실이다. 고정 시계를 쓰면 며칠 간격으로 두 번 돌려도 같은 시각이
     * 찍혀서, 나중에 "이번 실행에서 안 나온 카드 = 단종 후보"를 가려낼 근거가 사라진다.
     * 그래서 여기서만 실제 시계를 쓴다.
     */
    @Autowired
    public KbRawCardWriter(CrawlRawCardMapper crawlRawCardMapper) {
        this(crawlRawCardMapper, Clock.system(SERVICE_ZONE_ID));
    }

    /** 테스트에서 시각을 고정하기 위한 생성자. */
    KbRawCardWriter(CrawlRawCardMapper crawlRawCardMapper, Clock clock) {
        this.crawlRawCardMapper = crawlRawCardMapper;
        this.clock = clock;
    }

    /**
     * @return 적재한 섹션 행 수
     */
    @Transactional
    public int save(Long issuerId, List<KbRawSection> sections) {
        LocalDateTime fetchedAt = LocalDateTime.now(clock);

        for (KbRawSection section : sections) {
            crawlRawCardMapper.insertRawCard(CrawlRawCardRequest.builder()
                    .issuerId(issuerId)
                    .externalCardCode(section.getCardCode())
                    .cardName(section.getCardName())
                    .section(section.getSection())
                    .sourceUrl(section.getSourceUrl())
                    .rawText(section.getRawText())
                    .contentHash(section.getContentHash())
                    .fetchedAt(fetchedAt)
                    .build());
        }
        return sections.size();
    }
}
