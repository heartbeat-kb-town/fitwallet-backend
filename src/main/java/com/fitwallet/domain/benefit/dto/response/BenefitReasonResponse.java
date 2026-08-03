package com.fitwallet.domain.benefit.dto.response;

import com.fitwallet.domain.benefit.dto.BenefitReasonCode;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@ApiModel(description = "혜택을 받지 못하는 사유. code는 화면 분기용, message는 그대로 노출하는 문구다")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenefitReasonResponse {

    @ApiModelProperty(value = """
            사유 코드.
            - `PREV_SPEND_NOT_MET`: 전월실적이 어떤 구간에도 들지 못했다(status=CONDITION_NOT_MET)
            - `LIMIT_EXHAUSTED`: 조건은 만족했으나 할인·적립 한도가 소진됐다(status=CONDITION_NOT_MET)
            - `NO_BENEFIT_FOR_STORE`: 이 가맹점에 걸리는 혜택이 없다(status=NO_BENEFIT)""",
            example = "PREV_SPEND_NOT_MET")
    private BenefitReasonCode code;

    @ApiModelProperty(value = "사용자에게 그대로 보여주는 안내 문구. code마다 문구가 달라진다",
            example = "전월실적 조건이 부족해서 혜택을 받을 수 없어요.")
    private String message;
}
