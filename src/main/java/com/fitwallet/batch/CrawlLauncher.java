package com.fitwallet.batch;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.fitwallet.batch.crawl.service.CrawlResult;
import com.fitwallet.batch.crawl.service.CrawlService;
import com.fitwallet.batch.crawl.spi.IssuerCrawler;

/**
 * 크롤러 수동 실행 진입점.
 *
 * <p>웹 서버(WAR)와 무관하게 도는 독립 프로세스다. 루트 컨텍스트만 올리므로 Controller나
 * Swagger 같은 웹 계층은 뜨지 않는다.
 *
 * <p><b>카드사마다 태스크를 만들지 않는다.</b> 첫 인자로 카드사를 받아
 * {@link IssuerCrawler} 구현체 중에서 고른다. 카드사가 늘어도 Gradle 태스크는 그대로다.
 *
 * <pre>
 * ./gradlew crawl --args="KB국민카드"        # 전체
 * ./gradlew crawl --args="KB국민카드 5"      # 5장만 (스모크)
 * </pre>
 *
 * <p>두 번째 인자는 처리할 카드 수 상한이다. 없거나 0 이하면 제한 없음.
 *
 * <p><b>스케줄링은 여기 없다.</b> "주기적으로" 도는 방식(WAR 안 {@code @Scheduled} / cron /
 * 관리자 API)은 따로 정할 문제라 이번 범위에서 뺐다. 지금은 사람이 직접 돌린다.
 */
public final class CrawlLauncher {

    private static final Logger log = LoggerFactory.getLogger(CrawlLauncher.class);

    private CrawlLauncher() {
    }

    public static void main(String[] args) {
        if (args == null || args.length == 0 || args[0].isBlank()) {
            throw new IllegalArgumentException(
                    "카드사 이름이 필요합니다. 예: ./gradlew crawl --args=\"KB국민카드\"");
        }
        String issuerName = args[0].trim();
        int limit = parseLimit(args);

        try (ClassPathXmlApplicationContext context =
                     new ClassPathXmlApplicationContext("classpath:root-context.xml")) {

            IssuerCrawler crawler = selectCrawler(context, issuerName);
            CrawlResult result = context.getBean(CrawlService.class).crawl(crawler, limit);

            log.info("""

                    ================ 혜택 원문 수집 결과 ================
                     카드사        : {}
                     열거된 카드   : {}     <- 지난 실행보다 줄었으면 열거가 깨진 것이다
                     성공          : {}장 (섹션 {}행)
                     껍데기 응답   : {}장   <- 0이 아니면 페이지 구조 변경 의심
                     실패          : {}장
                     실패 카드코드 : {}
                    ===================================================""",
                    result.getIssuerName(),
                    result.getEnumeratedCount(),
                    result.getSucceededCount(), result.getSectionCount(),
                    result.getStubCount(),
                    result.getFailedCount(),
                    result.getFailedCardCodes());

            // 한 장도 못 받았으면 실패로 끝낸다. 조용히 0을 반환하면 CI나 cron이 성공으로 오해한다.
            if (result.getSucceededCount() == 0) {
                System.exit(1);
            }
        }
    }

    /**
     * 등록된 어댑터 중 이름이 맞는 것을 고른다.
     *
     * <p>못 찾으면 등록된 목록을 함께 보여준다. 어댑터가 하나도 없는 상태(이 배치 골격만
     * 있고 카드사 구현이 아직 없는 상태)에서도 "무엇이 없는지"가 바로 드러나야 한다.
     */
    private static IssuerCrawler selectCrawler(ClassPathXmlApplicationContext context,
                                               String issuerName) {
        Map<String, IssuerCrawler> beans = context.getBeansOfType(IssuerCrawler.class);

        return beans.values().stream()
                .filter(crawler -> crawler.issuerName().equals(issuerName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "'" + issuerName + "' 어댑터가 없습니다. 등록된 카드사: " + registeredNames(beans.values())));
    }

    private static String registeredNames(java.util.Collection<IssuerCrawler> crawlers) {
        List<String> names = crawlers.stream()
                .map(IssuerCrawler::issuerName)
                .sorted()
                .collect(Collectors.toList());
        return names.isEmpty() ? "(없음)" : String.join(", ", names);
    }

    private static int parseLimit(String[] args) {
        if (args.length < 2 || args[1].isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(args[1].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("카드 수 상한은 정수여야 합니다: " + args[1], e);
        }
    }
}
