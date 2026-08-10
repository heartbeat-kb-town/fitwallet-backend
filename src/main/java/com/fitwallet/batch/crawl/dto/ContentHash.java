package com.fitwallet.batch.crawl.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.fitwallet.batch.crawl.exception.CrawlException;

/**
 * 원문 해시 계산.
 *
 * <p>카드사가 늘어도 해시 방식은 같아야 한다 — 어댑터마다 다르게 계산하면
 * {@code content_hash} 비교로 "안 바뀐 카드"를 가려내는 것 자체가 무의미해진다.
 * 그래서 어댑터에 맡기지 않고 여기 한 곳에 둔다.
 *
 * <p>{@code MessageDigest}만 쓴다. 해시 하나 때문에 라이브러리를 더하지 않는다.
 */
public final class ContentHash {

    private ContentHash() {
    }

    /** {@code SHA-256(text)}를 소문자 16진수 64자로 돌려준다. */
    public static String of(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();

        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 JDK가 반드시 제공한다. 여기 오면 런타임이 깨진 것이다.
            throw new CrawlException("SHA-256을 쓸 수 없습니다.", e);
        }
    }
}
