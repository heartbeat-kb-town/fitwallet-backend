package com.fitwallet.domain.benefit.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@ApiModel(description = "판정된 혜택 하나. status=AVAILABLE이면 적용될 혜택, LIMIT_EXHAUSTED면 한도가 소진된 혜택이다")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenefitDetailResponse {

    @ApiModelProperty(value = "혜택 서비스 ID", example = "1")
    private Long benefitServiceId;

    @ApiModelProperty(value = "혜택 이름", example = "TIME 할인 - 편의점")
    private String benefitName;

    @ApiModelProperty(value = "화면에 그대로 노출하는 혜택 문구. 서버가 조립해 내려준다(예: \"10% 할인\", \"1,000원 할인\", \"5% 마이신한포인트 적립\")",
            example = "10% 할인")
    private String displayText;

    @ApiModelProperty(value = """
            이번 결제에서 받을 것으로 예상되는 금액(원). **요청에 `amount`를 보냈을 때만 채워지고, 없으면 null이다.**
            적립 혜택도 포인트 개수가 아니라 원화로 환산된 값이라 할인과 그대로 비교할 수 있다.
            건당 캡과 결제금액 상한이 이미 반영돼 있다.""",
            example = "3500")
    private BigDecimal expectedAmount;
}
