package com.fitwallet.batch.kb.exception;

/**
 * 내용 없는 껍데기 응답을 받았을 때.
 *
 * <p>KB 카드 상세 페이지는 {@code cooperationcode} 파라미터가 없으면 <b>HTTP 200을 주면서</b>
 * 내용 대신 1.1KB짜리 JS 리다이렉트 스텁을 돌려준다:
 *
 * <pre>
 * GET .../HCAMCXPRICAC0076                            -> 200,   1,125 B  (스텁)
 * GET .../HCAMCXPRICAC0076?...&amp;cooperationcode=04485 -> 200, 387,197 B  (진짜)
 * </pre>
 *
 * <p>상태코드만 보는 크롤러는 여기서 조용히 빈 문서를 쌓는다. 그게 이 예외가 있는 이유다 —
 * 파라미터를 빠뜨렸거나 KB가 페이지 구조를 바꿨다는 뜻이므로, 넘어가지 말고 시끄럽게 실패해야 한다.
 */
public class KbStubResponseException extends KbCrawlException {

    public KbStubResponseException(String message) {
        super(message);
    }
}
