package com.fitwallet.batch.crawl.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fitwallet.batch.crawl.exception.CrawlException;

/**
 * 카드사 공용 HTTP 클라이언트.
 *
 * <p>JDK 17 내장 {@link HttpClient}를 감싼다 — OkHttp나 Apache HttpClient를 추가하지 않는다.
 *
 * <p><b>카드사별 어댑터가 이 클래스를 공유한다.</b> 카드사마다 다른 건 어느 URL을 치느냐이지
 * 어떻게 치느냐가 아니다. 딜레이·재시도·인코딩을 어댑터마다 다시 짜면 그중 하나가 딜레이를
 * 빠뜨렸을 때 그 카드사만 조용히 예의 없는 크롤러가 된다.
 *
 * <p>이 클래스가 책임지는 건 넷이다.
 * <ul>
 *   <li><b>브라우저 흉내</b> — User-Agent가 없으면 카드사가 정상 응답을 주지 않는다(KB 실측).
 *       크롤러임을 숨기려는 게 아니라 UA가 비면 서버가 응답을 달리 주기 때문이다</li>
 *   <li><b>요청 간 딜레이</b> — 카드 수십 개를 연속으로 치므로 매 요청 전에 쉰다.
 *       상대 서버에 부담을 주지 않기 위한 것으로, 이 배치의 예의이자 안전장치다</li>
 *   <li><b>재시도</b> — 일시적 네트워크 오류와 5xx만 다시 시도한다. 4xx는 우리가 잘못
 *       요청한 것이므로 재시도해도 같은 결과라 즉시 포기한다</li>
 *   <li><b>인코딩 고정</b> — UTF-8로 읽는다</li>
 * </ul>
 *
 * <p>인증·쿠키·토큰은 다루지 않는다. 로그인 없이 열리는 공개 페이지만 대상이기 때문이고,
 * 그 전제가 깨지는 카드사는 애초에 수집 대상이 아니다.
 */
@Component
public class CrawlHttpClient {

    private static final Logger log = LoggerFactory.getLogger(CrawlHttpClient.class);

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MILLIS = 2_000L;

    /**
     * 요청 사이 기본 대기(ms).
     *
     * <p>급하게 줄일 이유가 없는 배치다. 카드 100장이라도 1.5초면 2분 반이고, 그 대가로
     * 상대 서버에 부담을 주지 않는다.
     */
    public static final long DEFAULT_REQUEST_DELAY_MILLIS = 1_500L;

    private final HttpClient httpClient;
    private final long requestDelayMillis;

    public CrawlHttpClient() {
        this(DEFAULT_REQUEST_DELAY_MILLIS);
    }

    /** 테스트에서 딜레이를 없애기 위한 생성자. */
    public CrawlHttpClient(long requestDelayMillis) {
        this.requestDelayMillis = requestDelayMillis;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                // 상세 페이지 접근 시 리다이렉트를 태우는 카드사가 있어 따라간다.
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** 페이지를 받아 본문 문자열로 돌려준다. */
    public String get(String url) {
        return get(url, null);
    }

    /**
     * Referer를 붙여 GET 한다.
     *
     * <p>목록 페이지를 Referer로 요구하는 카드사가 있어 어댑터가 지정할 수 있게 열어 뒀다.
     */
    public String get(String url, String referer) {
        return send(baseRequest(url, referer).GET().build(), url);
    }

    /**
     * 폼 인코딩 POST. GET을 받지 않는 목록 API가 있어 필요하다(바디는 비어 있어도 된다).
     */
    public String postForm(String url, String formBody, String referer) {
        HttpRequest request = baseRequest(url, referer)
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                .build();
        return send(request, url);
    }

    private HttpRequest.Builder baseRequest(String url, String referer) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "ko-KR,ko;q=0.9");

        if (referer != null) {
            builder.header("Referer", referer);
        }
        return builder;
    }

    private String send(HttpRequest request, String url) {
        IOException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            sleep(requestDelayMillis);
            try {
                HttpResponse<String> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();

                if (status >= 200 && status < 300) {
                    return response.body();
                }
                // 4xx는 우리 요청이 틀린 것이라 다시 보내도 같은 답이 온다. 즉시 포기한다.
                if (status < 500) {
                    throw new CrawlException("응답이 " + status + "입니다: " + url);
                }
                log.warn("5xx 응답 (attempt {}/{}, status {}): {}", attempt, MAX_ATTEMPTS, status, url);

            } catch (IOException e) {
                lastFailure = e;
                log.warn("요청 실패 (attempt {}/{}): {} - {}", attempt, MAX_ATTEMPTS, url, e.toString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CrawlException("요청이 중단되었습니다: " + url, e);
            }

            if (attempt < MAX_ATTEMPTS) {
                sleep(RETRY_BACKOFF_MILLIS * attempt);
            }
        }
        throw new CrawlException(
                "요청을 " + MAX_ATTEMPTS + "회 시도했지만 실패했습니다: " + url, lastFailure);
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CrawlException("대기 중 중단되었습니다.", e);
        }
    }
}
