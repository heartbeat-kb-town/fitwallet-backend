package com.fitwallet.domain.benefit.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
}
