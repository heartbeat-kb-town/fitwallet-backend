package com.fitwallet.batch.crawl.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitwallet.batch.crawl.dto.ContentHash;
import com.fitwallet.batch.crawl.dto.CrawlRawCardRequest;
import com.fitwallet.batch.crawl.dto.RawCardBenefit;
import com.fitwallet.batch.crawl.mapper.CrawlRawCardMapper;

/**
 * 카드 한 장의 혜택 원문을 staging에 적재한다.
 *
 * <p><b>왜 별도 빈인가.</b> 두 가지 이유가 겹친다.
 *
 * <ol>
 *   <li>{@code @Transactional}은 프록시로 걸린다. {@link DefaultCrawlService} 안에서
 *       자기 메서드를 호출하면 프록시를 거치지 않아 트랜잭션이 조용히 안 걸린다.
 *       다른 빈으로 빼야 실제로 걸린다.</li>
 *   <li>네트워크 요청과 DB 쓰기를 같은 트랜잭션에 두면 HTTP 응답을 기다리는 내내
 *       커넥션을 붙잡는다. 수집은 트랜잭션 밖에서 하고, 여기서 쓰기만 짧게 묶는다.</li>
 * </ol>
 *
 * <p>경계를 카드 한 장으로 잡은 이유: 전체를 한 트랜잭션으로 묶으면 마지막 카드에서 실패했을 때
 * 앞의 수십 장이 통째로 롤백된다. 네트워크 상대가 있는 작업에서 그 손해가 너무 크다.
 * 카드 하나가 실패해도 나머지는 남는 편이 낫다.
 */
@Service
public class RawCardWriter {

    private static final ZoneId SERVICE_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final CrawlRawCardMapper crawlRawCardMapper;
    private final Clock clock;

    /**
     * <b>공용 {@code clock} 빈을 주입받지 않는다.</b> 그 빈은 로컬 프로파일에서
     * {@code clock.fixed-date}로 고정돼 있다 — 데모 시드의 거래 날짜와 화면의 "이번 달"을
     * 맞추기 위한 장치다.
     *
     * <p>그런데 {@code fetched_at}은 그런 <i>업무상 날짜</i>가 아니라 "실제로 언제 카드사를
     * 쳤는가"라는 운영 사실이다. 고정 시계를 쓰면 며칠 간격으로 두 번 돌려도 같은 시각이
     * 찍혀서, 나중에 "이번 실행에서 안 나온 카드 = 단종 후보"를 가려낼 근거가 사라진다.
     * 그래서 여기서만 실제 시계를 쓴다.
     */
    @Autowired
    public RawCardWriter(CrawlRawCardMapper crawlRawCardMapper) {
        this(crawlRawCardMapper, Clock.system(SERVICE_ZONE_ID));
    }

    /** 테스트에서 시각을 고정하기 위한 생성자. */
    RawCardWriter(CrawlRawCardMapper crawlRawCardMapper, Clock clock) {
        this.crawlRawCardMapper = crawlRawCardMapper;
        this.clock = clock;
    }

    /**
     * @return 적재한 행 수(항상 1). 같은 카드를 다시 수집하면 덮어쓴다
     */
    @Transactional
    public int save(Long issuerId, RawCardBenefit benefit) {
        crawlRawCardMapper.insertRawCard(CrawlRawCardRequest.builder()
                .issuerId(issuerId)
                .externalCardCode(benefit.getCardCode())
                .cardName(benefit.getCardName())
                .sourceUrl(benefit.getSourceUrl())
                .rawText(benefit.getRawText())
                .contentHash(resolveContentHash(benefit))
                .fetchedAt(LocalDateTime.now(clock))
                .build());
        return 1;
    }

    /**
     * 어댑터가 해시를 안 넣었으면 여기서 계산한다.
     *
     * <p>해시 방식이 카드사마다 다르면 {@code content_hash} 비교로 "안 바뀐 카드"를
     * 가려내는 것 자체가 무의미해지므로, 어댑터에 맡기지 않고 이 경로로 수렴시킨다.
     */
    private String resolveContentHash(RawCardBenefit benefit) {
        String given = benefit.getContentHash();
        if (given != null && !given.isBlank()) {
            return given;
        }
        return ContentHash.of(benefit.getRawText());
    }
}
