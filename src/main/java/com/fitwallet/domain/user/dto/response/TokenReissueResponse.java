package com.fitwallet.domain.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 재발급 성공 응답의 data에 담기는 Access Token. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenReissueResponse {
    private String accessToken;
}
