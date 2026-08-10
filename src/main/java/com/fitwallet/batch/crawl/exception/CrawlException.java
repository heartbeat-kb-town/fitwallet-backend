package com.fitwallet.batch.crawl.exception;

/**
 * 수집 과정에서 난 오류.
 *
 * <p>도메인 계층의 {@code BusinessException}을 쓰지 않는다. 그쪽은 HTTP 응답으로
 * 번역되는 사용자용 오류이고(AGENTS.md §5), 이건 배치 로그로 끝나는 내부 오류라
 * ErrorCode·HTTP 상태코드 같은 게 붙을 자리가 없다.
 */
public class CrawlException extends RuntimeException {

    public CrawlException(String message) {
        super(message);
    }

    public CrawlException(String message, Throwable cause) {
        super(message, cause);
    }
}
