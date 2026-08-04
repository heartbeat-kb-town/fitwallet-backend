package com.fitwallet.batch.kb.client;

/**
 * KB 사이트에서 우리가 건드리는 URL 전부.
 *
 * <p>한곳에 모아 둔 이유: 이 목록이 곧 <b>이 배치가 KB에 요청하는 범위의 전부</b>라
 * 리뷰어가 여기만 보고 수집 범위를 판단할 수 있어야 하기 때문이다.
 *
 * <p>전부 KB {@code robots.txt}가 {@code Allow: /CRD/}로 명시 허용한 경로이고,
 * 로그인·쿠키 없이 열리는 공개 페이지다. 접근 제어를 우회하는 곳은 하나도 없다.
 */
public final class KbCrawlEndpoints {

    private KbCrawlEndpoints() {
    }

    public static final String BASE = "https://card.kbcard.com";

    /**
     * KB가 robots.txt에 스스로 공개한 사이트맵. 카드 열거의 주 소스다
     * (여기서 {@code cooperationcode}가 붙은 URL 41개를 얻는다 — 실측).
     */
    public static final String SITEMAP = BASE + "/CMN/sitemap-desktop.xml";

    /** 카드한눈에보기. Referer로도 쓰고, 카드 이미지 경로에서 코드를 줍기도 한다. */
    public static final String CARD_LIST_PAGE = BASE + "/CRD/DVIEW/HCAM0101";

    /** 신용카드 목록. */
    public static final String CREDIT_CARD_LIST_PAGE = BASE + "/CRD/DVIEW/HCAMCXPRICAC0047";

    /** 체크카드 목록. */
    public static final String CHECK_CARD_LIST_PAGE = BASE + "/CRD/DVIEW/HCAMCXPRICAC0056";

    /** 인기카드 API. 전체가 아닌 부분집합이라 열거의 보강용으로만 쓴다. 한글 키 JSON을 준다. */
    public static final String POPULAR_CARD_API = BASE + "/CRD/API/MCAA0004?responseContentType=json";

    /**
     * 카드 상세 페이지.
     *
     * <p>{@code cooperationcode}가 <b>반드시</b> 있어야 한다. 없으면 HTTP 200에 1.1KB짜리
     * JS 리다이렉트 스텁이 온다({@code KbStubResponseException} 참고).
     */
    public static final String CARD_DETAIL_PAGE_FORMAT =
            BASE + "/CRD/DVIEW/HCAMCXPRICAC0076?mainCC=a&cooperationcode=%s";

    /**
     * 요청 사이 기본 대기(ms). 카드 수십 개를 연속으로 치므로 상대 서버에 부담을 주지
     * 않도록 넉넉히 잡는다. 급하게 줄일 이유가 없는 배치다.
     */
    public static final long DEFAULT_REQUEST_DELAY_MILLIS = 1_500L;

    public static String cardDetailUrl(String cardCode) {
        return String.format(CARD_DETAIL_PAGE_FORMAT, cardCode);
    }
}
