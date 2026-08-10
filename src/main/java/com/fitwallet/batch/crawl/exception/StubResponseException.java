package com.fitwallet.batch.crawl.exception;

/**
 * 내용 없는 껍데기 응답을 받았을 때.
 *
 * <p>카드사 상세 페이지는 필수 파라미터가 빠져도 <b>HTTP 200을 주면서</b> 내용 대신
 * 리다이렉트 스텁을 돌려주는 경우가 있다. KB 실측:
 *
 * <pre>
 * GET .../HCAMCXPRICAC0076                            -> 200,   1,125 B  (스텁)
 * GET .../HCAMCXPRICAC0076?...&amp;cooperationcode=04485 -> 200, 387,197 B  (진짜)
 * </pre>
 *
 * <p>상태코드만 보는 크롤러는 여기서 조용히 빈 문서를 쌓는다. 그게 이 예외가 있는 이유다 —
 * 파라미터를 빠뜨렸거나 카드사가 페이지 구조를 바꿨다는 뜻이므로, 넘어가지 말고 시끄럽게
 * 실패해야 한다.
 *
 * <p><b>감지는 어댑터의 몫이다.</b> 껍데기의 모양이 카드사마다 달라
 * {@link com.fitwallet.batch.crawl.spi.IssuerCrawler#parse} 안에서 던진다. 공통 쪽은
 * 그것을 잡아 세기만 한다 — {@code stubCount}가 0이 아니면 눈에 띄어야 하기 때문이다.
 */
public class StubResponseException extends CrawlException {

    public StubResponseException(String message) {
        super(message);
    }
}
