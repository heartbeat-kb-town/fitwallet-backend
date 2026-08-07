package com.fitwallet.domain.card.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fitwallet.domain.card.dto.CardEventTargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** 카드별 이벤트 조회 결과의 이벤트 한 건. MyBatis가 조회 결과를 직접 채운다. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardEventItemResponse {

    private Long eventId;
    private CardEventTargetType targetType;
    private String summary;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startsAt;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endsAt;
    private Long daysRemaining;
    private String detailUrl;
    private Boolean detailAvailable;
}
