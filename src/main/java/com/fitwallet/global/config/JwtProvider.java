package com.fitwallet.global.config;

import com.fitwallet.global.exception.BusinessException;
import com.fitwallet.global.exception.CommonErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

/**
 * Access Token과 Refresh Token의 생성 및 검증을 담당한다.
 * 사용자 식별자는 subject에 저장하고, 두 토큰은 tokenType claim으로 구분한다.
 */
@Component
public class JwtProvider {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";

    private final SecretKey secretKey;
    private final long accessTokenExpirationMillis;
    private final long refreshTokenExpirationMillis;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-millis}") long accessTokenExpirationMillis,
            @Value("${jwt.refresh-token-expiration-millis}") long refreshTokenExpirationMillis) {

        byte[] keyBytes = Base64.getDecoder().decode(secret);

        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpirationMillis = accessTokenExpirationMillis;
        this.refreshTokenExpirationMillis = refreshTokenExpirationMillis;
    }

    /** 사용자 식별자를 subject로 갖는 Access Token을 생성한다. */
    public String generateAccessToken(Long userId) {
        return generateToken(
                userId,
                ACCESS_TOKEN_TYPE,
                accessTokenExpirationMillis
        );
    }

    /** 사용자 식별자를 subject로 갖는 Refresh Token을 생성한다. */
    public String generateRefreshToken(Long userId) {
        return generateToken(
                userId,
                REFRESH_TOKEN_TYPE,
                refreshTokenExpirationMillis
        );
    }

    /** Refresh Token 만료 시간을 초 단위로 반환한다. */
    public long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpirationMillis / 1000;
    }

    /** Access Token을 검증하고 subject의 사용자 식별자를 반환한다. */
    public Long getUserIdFromAccessToken(String token) {
        Claims claims = parseClaims(token);

        validateTokenType(claims, ACCESS_TOKEN_TYPE);
        return parseUserId(claims);
    }

    /** Refresh Token을 검증하고 subject의 사용자 식별자를 반환한다. */
    public Long getUserIdFromRefreshToken(String token) {
        Claims claims = parseClaims(token);

        validateTokenType(claims, REFRESH_TOKEN_TYPE);
        return parseUserId(claims);
    }

    private String generateToken(Long userId, String tokenType, long expirationMillis) {
        if (userId == null) {
            throw new IllegalArgumentException(
                    "userId는 null일 수 없습니다."
            );
        }

        Date issuedAt = new Date();
        Date expiration = new Date(issuedAt.getTime() + expirationMillis);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }

    private void validateTokenType(Claims claims, String expectedTokenType) {
        String actualTokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);

        if (!expectedTokenType.equals(actualTokenType)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }

    private Long parseUserId(Claims claims) {
        String subject = claims.getSubject();

        if (subject == null || subject.isBlank()) {
            throw new BusinessException(
                    CommonErrorCode.UNAUTHORIZED
            );
        }

        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException e) {
            throw new BusinessException(
                    CommonErrorCode.UNAUTHORIZED
            );
        }
    }
}
