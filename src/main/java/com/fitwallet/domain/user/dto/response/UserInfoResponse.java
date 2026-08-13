package com.fitwallet.domain.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 마이페이지 조회 응답.
 * <p>
 * 현재 화면은 이름만 표시하므로 최소 필드만 둔다. 추가 정보가 필요해지면 필드를 늘린다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInfoResponse {
    private String name;
}
