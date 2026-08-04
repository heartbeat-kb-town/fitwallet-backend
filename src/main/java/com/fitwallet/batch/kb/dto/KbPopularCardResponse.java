package com.fitwallet.batch.kb.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 인기카드 API({@code POST /CRD/API/MCAA0004?responseContentType=json}) 응답.
 *
 * <p><b>키가 한글이다.</b> 그래서 필드마다 {@code @JsonProperty}가 필요하다:
 *
 * <pre>
 * {"인기신용카드":[{"카드명":"ALL 카드","제휴코드":"09922","신용체크구분":"신용"}],
 *  "인기체크카드":[...]}
 * </pre>
 *
 * <p>이 API는 전체 카드가 아니라 인기 카드 부분집합만 준다. 열거의 주 소스는 사이트맵이고
 * 이건 보강용이다({@code KbCardCodeCollector} 참고).
 *
 * <p>응답에 우리가 안 쓰는 필드(채널화면대분류일련번호 등)가 섞여 있어
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)}로 무시한다 — 카드사가 필드를
 * 추가해도 역직렬화가 깨지지 않게 한다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class KbPopularCardResponse {

    @JsonProperty("인기신용카드")
    private List<Card> creditCards;

    @JsonProperty("인기체크카드")
    private List<Card> checkCards;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Card {

        @JsonProperty("카드명")
        private String cardName;

        /** 카드 상세 페이지의 {@code cooperationcode} 파라미터로 그대로 쓰인다. */
        @JsonProperty("제휴코드")
        private String cardCode;

        /** {@code "신용"} 또는 {@code "체크"}. */
        @JsonProperty("신용체크구분")
        private String cardType;
    }
}
