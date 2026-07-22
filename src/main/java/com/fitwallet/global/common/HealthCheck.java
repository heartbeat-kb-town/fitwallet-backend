package com.fitwallet.global.common;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class HealthCheck {

    private Long id;
    private String message;
    private LocalDateTime checkedAt;
}
