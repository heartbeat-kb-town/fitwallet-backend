package com.fitwallet.domain.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PinAuthInfo {
    private String pinAuthId;
    private LocalDateTime authExpiresAt;
    private boolean authIsUsed;
}
