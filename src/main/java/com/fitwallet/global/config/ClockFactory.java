package com.fitwallet.global.config;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 실행 환경의 기준 시각을 제공하는 Clock 생성기.
 */
public final class ClockFactory {

    private static final ZoneId SERVICE_ZONE_ID = ZoneId.of("Asia/Seoul");

    private ClockFactory() {
    }

    /**
     * 고정 날짜가 있으면 해당 날짜의 자정을, 없으면 현재 시각을 기준으로 Clock을 생성한다.
     */
    public static Clock create(String fixedDate) {
        if (fixedDate == null || fixedDate.isBlank()) {
            return Clock.system(SERVICE_ZONE_ID);
        }

        return Clock.fixed(
                LocalDate.parse(fixedDate).atStartOfDay(SERVICE_ZONE_ID).toInstant(),
                SERVICE_ZONE_ID
        );
    }
}
