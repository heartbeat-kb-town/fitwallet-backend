package com.fitwallet.global.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DB 연결 확인용 응답. {@code health_check} 테이블의 최신 한 건.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthCheckResponse {

    private Long id;
    private String message;
    private LocalDateTime checkedAt;
}
