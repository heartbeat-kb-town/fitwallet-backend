package com.fitwallet.domain.card.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Future;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.time.LocalDate;

/**
 * 카드 등록 요청.
 * <p>
 * 사용자 식별자는 여기 넣지 않는다. 인증 컨텍스트는 항상 {@code @LoginUserId} 파라미터로 받는다.
 * <p>
 * Jackson이 채우지만 {@code @Setter}는 붙이지 않는다 (필드 직접 주입).
 */
@Getter
@NoArgsConstructor
public class CardRegisterRequest {

    @NotNull(message = "카드 상품을 선택해주세요.")
    private Long cardProductId;

    @Pattern(regexp = "\\d{4}", message = "카드번호 앞 4자리를 숫자로 입력해주세요.")
    private String first4;

    @Pattern(regexp = "\\d{4}", message = "카드번호 뒤 4자리를 숫자로 입력해주세요.")
    private String last4;

    @NotNull(message = "유효기간을 입력해주세요.")
    @Future(message = "이미 만료된 카드입니다.")
    private LocalDate expiryDate;
}
