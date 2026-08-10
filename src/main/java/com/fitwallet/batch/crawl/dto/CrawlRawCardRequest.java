package com.fitwallet.batch.crawl.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@code crawl_raw_card} 적재 파라미터.
 *
 * <p>AGENTS.md §4대로 Request DTO가 Mapper 파라미터로 그대로 관통한다. 여기엔
 * 컨트롤러가 없고 배치 서비스가 그 자리를 대신한다.
 *
 * <p>{@code created_at}/{@code updated_at}은 DB DEFAULT가 채우므로 필드를 두지 않는다
 * (AGENTS.md §10).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrawlRawCardRequest {

    private Long issuerId;
    private String externalCardCode;
    private String cardName;
    private SectionType section;
    private String sourceUrl;
    private String rawText;
    private String contentHash;

    /** 수집 시각(비즈니스 시각). 레코드 생성 시각인 {@code created_at}과 분리한다. */
    private LocalDateTime fetchedAt;
}
