package com.fitwallet.batch.crawl.spi;

import java.util.Set;

import com.fitwallet.batch.crawl.dto.RawCardBenefit;

/**
 * 카드사 하나를 수집하기 위해 구현해야 하는 전부.
 *
 * <p><b>카드사를 붙이려면 이 넷만 짜면 된다.</b> 나머지는
 * {@link com.fitwallet.batch.crawl.service.CrawlService}가 처리한다 — 요청 간 딜레이,
 * 재시도, 트랜잭션 경계, 껍데기 응답 집계, 결과 요약, staging 적재.
 *
 * <p>구현체는 {@code com.fitwallet.batch.issuer.{카드사}} 아래에 두고 스프링 빈으로
 * 등록한다. 여러 개가 등록돼 있어도 {@link #issuerName()}으로 골라 쓴다.
 *
 * <h2>왜 인터페이스를 따로 두는가</h2>
 *
 * <p>카드사별로 다른 건 <b>어디서 카드 목록을 얻는가</b>와 <b>HTML의 어느 조각이 혜택인가</b>
 * 둘뿐이다. HTTP를 어떻게 치고 DB에 어떻게 넣을지는 전부 같다. 그 경계를 코드로 굳혀 두지
 * 않으면 카드사가 늘 때마다 같은 클라이언트·같은 Mapper가 한 벌씩 복제된다.
 *
 * <h2>구현 시 지킬 것</h2>
 *
 * <ul>
 *   <li><b>수집 대상 URL을 한곳에 모은다.</b> 그 목록이 곧 이 배치가 카드사에 요청하는
 *       범위의 전부라, 리뷰어가 거기만 보고 판단할 수 있어야 한다</li>
 *   <li><b>{@code robots.txt}가 허용한 경로만 친다.</b> 거부 의사를 밝힌 카드사는 아예
 *       구현하지 않는다</li>
 *   <li><b>카드 코드를 무차별 대입으로 만들지 않는다.</b> 카드사가 스스로 공개한
 *       사이트맵·목록·API에서만 얻는다</li>
 * </ul>
 */
public interface IssuerCrawler {

    /**
     * 카드사 이름. {@code issuer.card_company_name}과 <b>정확히 같아야 한다</b> —
     * 이 값으로 {@code issuer_id}를 찾는다.
     *
     * <p>배치가 카드사 PK를 상수로 박지 않기 위해서다. 시드에서 KB국민카드가 3번이지만
     * 그건 시드 사정이고 환경마다 다를 수 있다.
     */
    String issuerName();

    /**
     * 수집 대상 카드 코드를 열거한다.
     *
     * <p>여기서 빠진 카드는 이번 실행에 아예 없는 것이 된다. 그 카드에만 있던 혜택과
     * 브랜드가 통째로 누락되므로, <b>얼마나 빠짐없이 열거하느냐가 이 어댑터의 품질</b>이다.
     *
     * @return 카드사가 부여한 카드 식별자 집합. 순서는 의미 없다
     */
    Set<String> collectCardCodes();

    /** 카드 코드로 상세 페이지 URL을 만든다. */
    String cardDetailUrl(String cardCode);

    /**
     * 상세 페이지 HTML에서 혜택 원문을 뽑는다.
     *
     * <p><b>네트워크를 몰라야 한다.</b> 입력이 문자열이라 저장해 둔 HTML만으로 테스트가
     * 돌아간다 — 카드사 서버가 죽어 있어도, 오프라인이어도 파서 테스트는 통과해야 한다.
     *
     * <p><b>카드 한 장당 하나를 돌려준다.</b> 페이지를 요약/연회비 따위로 쪼개지 않는다 —
     * 혜택 판정에 쓸 값은 한 영역에 모여 있고, 쪼개는 기준이 카드사마다 달라 얻는 것 없이
     * 어댑터만 복잡해진다. {@code crawl_raw_card}도 카드당 한 행이다.
     *
     * @throws com.fitwallet.batch.crawl.exception.StubResponseException
     *         내용 없는 껍데기 응답일 때. 조용히 빈 결과를 돌려주지 말 것
     */
    RawCardBenefit parse(String cardCode, String html);
}
