package com.fitwallet.batch.kb.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.fitwallet.batch.kb.dto.CrawlRawCardRequest;

/**
 * {@code crawl_raw_card} 접근.
 *
 * <p>{@code issuer} 조회도 여기 있다 — AGENTS.md §2의 "도메인(여기선 배치)은 테이블을
 * 소유하지 않고, 각 Mapper가 필요한 테이블에 직접 접근한다"를 그대로 따른다.
 */
@Mapper
public interface CrawlRawCardMapper {

    /**
     * 카드사 이름으로 {@code issuer_id}를 찾는다.
     *
     * <p>배치가 카드사 PK를 상수로 박지 않기 위해서다. 시드에서 KB국민카드가 3번이지만
     * 그건 시드 사정이고, 환경마다 다를 수 있다.
     *
     * @return 없으면 {@code null}
     */
    Long findIssuerIdByName(@Param("cardCompanyName") String cardCompanyName);

    /**
     * 원문 한 건을 적재한다. 같은 (카드사, 카드, 섹션)이 이미 있으면 덮어쓴다.
     *
     * <p>이력을 쌓지 않고 최신 원문만 들고 있는 테이블이라
     * {@code INSERT ... ON DUPLICATE KEY UPDATE} 한 문장으로 끝낸다. "있으면 UPDATE,
     * 없으면 INSERT"로 나누면 동시 실행 시 둘 다 INSERT 하는 경쟁 조건이 생긴다
     * ({@code search_history} v24가 같은 이유로 이 관용구를 쓴다).
     */
    int insertRawCard(CrawlRawCardRequest request);

    /**
     * 이미 저장된 원문의 해시들을 카드사 단위로 가져온다.
     *
     * <p>다음 단계에서 "해시가 그대로면 LLM을 부르지 않는다"를 판단하는 데 쓴다.
     */
    List<String> findContentHashesByIssuerId(@Param("issuerId") Long issuerId);

    /** 이번 실행에서 수집된 카드 코드 개수(스모크 확인용). */
    int countByIssuerId(@Param("issuerId") Long issuerId);
}
