package com.fitwallet.batch.crawl.service;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 수집 한 번의 결과 요약.
 *
 * <p>스모크 확인과 로그용이다. 특히 {@code stubCount}가 0이 아니면 카드사가 페이지 구조를
 * 바꿨거나 요청이 잘못됐다는 뜻이므로 눈에 띄어야 한다.
 *
 * <p>{@code enumeratedCount}는 그냥 참고 수치가 아니다 — <b>실행할 때마다 이 값이
 * 줄었는지 보는 것이 열거가 깨졌는지 알아채는 유일한 신호</b>다. 카드가 몇 장 빠지면
 * 그 카드에만 있던 브랜드가 통째로 누락되는데, 원문 테이블만 봐서는 알 수 없다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrawlResult {

    /** 대상 카드사. */
    private String issuerName;

    /** 열거 단계가 찾아낸 카드 수. */
    private int enumeratedCount;

    /** 원문을 하나라도 적재한 카드 수. */
    private int succeededCount;

    /** 내용 없는 껍데기 응답을 받은 카드 수. 0이어야 정상이다. */
    private int stubCount;

    /** 그 밖의 이유로 실패한 카드 수. */
    private int failedCount;

    /** 실패한 카드 코드들. 로그에서 바로 재현해 볼 수 있게 남긴다. */
    private List<String> failedCardCodes;
}
