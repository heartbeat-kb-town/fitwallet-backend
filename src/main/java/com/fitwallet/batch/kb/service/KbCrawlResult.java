package com.fitwallet.batch.kb.service;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 수집 한 번의 결과 요약.
 *
 * <p>스모크 확인과 로그용이다. 특히 {@code stubCount}가 0이 아니면 KB가 페이지 구조를
 * 바꿨거나 요청이 잘못됐다는 뜻이므로 눈에 띄어야 한다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KbCrawlResult {

    /** 열거 단계가 찾아낸 카드 수. */
    private int enumeratedCount;

    /** 원문을 하나라도 적재한 카드 수. */
    private int succeededCount;

    /** 내용 없는 껍데기 응답을 받은 카드 수. 0이어야 정상이다. */
    private int stubCount;

    /** 그 밖의 이유로 실패한 카드 수. */
    private int failedCount;

    /** 적재된 섹션 행 수(카드당 최대 3). */
    private int sectionCount;

    /** 실패한 카드 코드들. 로그에서 바로 재현해 볼 수 있게 남긴다. */
    private List<String> failedCardCodes;
}
