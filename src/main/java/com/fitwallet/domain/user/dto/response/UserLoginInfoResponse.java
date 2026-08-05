package com.fitwallet.domain.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 로그인 검증을 위해 Mapper가 조회한 사용자 식별자와 비밀번호 해시. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLoginInfoResponse {
    private Long userId;
    private String passwordHash;
}
