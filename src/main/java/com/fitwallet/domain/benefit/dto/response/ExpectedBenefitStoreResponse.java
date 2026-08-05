package com.fitwallet.domain.benefit.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@ApiModel(description = "조회 기준 가맹점")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpectedBenefitStoreResponse {

    @ApiModelProperty(value = "가맹점 ID. 요청의 storeId와 같은 값이며 응답에서는 숫자다", example = "1")
    private Long storeId;

    @ApiModelProperty(value = "가맹점 이름", example = "컴포즈커피 세종대학교점")
    private String storeName;
}
