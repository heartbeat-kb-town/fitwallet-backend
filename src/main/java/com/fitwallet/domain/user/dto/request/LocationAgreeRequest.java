package com.fitwallet.domain.user.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Getter
@NoArgsConstructor
public class LocationAgreeRequest {

    @NotNull(message = "위치 정보 이용 동의 여부는 필수입니다.")
    private Boolean agreed;
}
