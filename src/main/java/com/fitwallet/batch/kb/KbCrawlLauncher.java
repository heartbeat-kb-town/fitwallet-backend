package com.fitwallet.batch.kb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.fitwallet.batch.kb.service.KbCrawlResult;
import com.fitwallet.batch.kb.service.KbCrawlService;

/**
 * 크롤러 수동 실행 진입점.
 *
 * <p>웹 서버(WAR)와 무관하게 도는 독립 프로세스다. 루트 컨텍스트만 올리므로 Controller나
 * Swagger 같은 웹 계층은 뜨지 않는다.
 *
 * <p><b>스케줄링은 여기 없다.</b> "주기적으로" 도는 방식(WAR 안 {@code @Scheduled} / cron /
 * 관리자 API)은 따로 정할 문제라 이번 범위에서 뺐다. 지금은 사람이 직접 돌린다.
 *
 * <pre>
 * ./gradlew crawlKb                       # 전체
 * ./gradlew crawlKb --args="5"            # 5장만 (스모크)
 * </pre>
 *
 * <p>인자는 처리할 카드 수 상한이다. 없거나 0 이하면 제한 없음.
 */
public final class KbCrawlLauncher {

    private static final Logger log = LoggerFactory.getLogger(KbCrawlLauncher.class);

    private KbCrawlLauncher() {
    }

    public static void main(String[] args) {
        int limit = parseLimit(args);

        try (ClassPathXmlApplicationContext context =
                     new ClassPathXmlApplicationContext("classpath:root-context.xml")) {

            KbCrawlService crawlService = context.getBean(KbCrawlService.class);
            KbCrawlResult result = crawlService.crawl(limit);

            log.info("""

                    ================ KB 혜택 원문 수집 결과 ================
                     열거된 카드   : {}
                     성공          : {}장 (섹션 {}행)
                     껍데기 응답   : {}장   <- 0이 아니면 페이지 구조 변경 의심
                     실패          : {}장
                     실패 카드코드 : {}
                    =====================================================""",
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

    private static int parseLimit(String[] args) {
        if (args == null || args.length == 0 || args[0].isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(args[0].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "카드 수 상한은 정수여야 합니다: " + args[0], e);
        }
    }
}
