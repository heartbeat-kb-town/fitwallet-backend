package com.fitwallet.domain.card.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 보유 카드 표시 순서 변경 요청.
 * <p>
 * 새 순서로 정렬된 {@code userCardId} 전체 목록을 받는다. 인덱스가 곧 새
 * {@code display_order}다(0번째가 1번, 1번째가 2번, ...).
 * <p>
 * 보유 카드가 없는 사용자는 빈 배열이 정상 요청이라 {@code @NotEmpty}는 두지 않는다.
 * 대신 이 목록이 실제 보유 카드 집합과 정확히 일치하는지는 서비스에서 검증한다
 */
@Getter
@NoArgsConstructor
public class CardDisplayOrderUpdateRequest {

    @NotNull(message = "카드 순서는 필수입니다.")
    private List<@NotNull(message = "카드 ID는 필수입니다.") Long> userCardIds;
}
