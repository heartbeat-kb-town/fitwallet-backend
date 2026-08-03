package com.fitwallet.global.config;

import com.fitwallet.global.exception.BusinessException;
import com.fitwallet.global.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    /*
     * 테스트에서는 운영·로컬 설정 파일을 읽지 않고 JwtProvider를 직접 생성한다.
     * 32바이트 배열을 Base64로 인코딩하여 HS256 최소 키 길이를 충족한다.
     */
    private static final String TEST_SECRET =
            Base64.getEncoder().encodeToString(new byte[32]);

    private static final long ACCESS_TOKEN_EXPIRATION_MILLIS = 1_800_000L;
    private static final long REFRESH_TOKEN_EXPIRATION_MILLIS = 1_209_600_000L;

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(
                TEST_SECRET,
                ACCESS_TOKEN_EXPIRATION_MILLIS,
                REFRESH_TOKEN_EXPIRATION_MILLIS
        );
    }

    @Test
    void 액세스_토큰을_정상적으로_생성한다() {
        Long userId = 1L;

        String accessToken = jwtProvider.generateAccessToken(userId);

        assertThat(accessToken).isNotBlank();
    }

    @Test
    void 리프레시_토큰을_정상적으로_생성한다() {
        Long userId = 1L;

        String refreshToken = jwtProvider.generateRefreshToken(userId);

        assertThat(refreshToken).isNotBlank();
    }

    @Test
    void 액세스_토큰에서_사용자_ID를_추출한다() {
        Long userId = 10L;
        String accessToken = jwtProvider.generateAccessToken(userId);

        Long extractedUserId =
                jwtProvider.getUserIdFromAccessToken(accessToken);

        assertThat(extractedUserId).isEqualTo(userId);
    }

    @Test
    void 리프레시_토큰에서_사용자_ID를_추출한다() {
        Long userId = 10L;
        String refreshToken = jwtProvider.generateRefreshToken(userId);

        Long extractedUserId =
                jwtProvider.getUserIdFromRefreshToken(refreshToken);

        assertThat(extractedUserId).isEqualTo(userId);
    }

    @Test
    void 리프레시_토큰을_액세스_토큰으로_사용하면_UNAUTHORIZED_예외를_던진다() {
        String refreshToken = jwtProvider.generateRefreshToken(1L);

        assertUnauthorized(
                () -> jwtProvider.getUserIdFromAccessToken(refreshToken)
        );
    }

    @Test
    void 액세스_토큰을_리프레시_토큰으로_사용하면_UNAUTHORIZED_예외를_던진다() {
        String accessToken = jwtProvider.generateAccessToken(1L);

        assertUnauthorized(
                () -> jwtProvider.getUserIdFromRefreshToken(accessToken)
        );
    }

    @Test
    void 만료된_액세스_토큰은_UNAUTHORIZED_예외를_던진다() {
        /*
         * 현재 시각보다 충분히 이전에 만료되도록 음수 유효시간을 사용한다.
         */
        JwtProvider expiredTokenProvider = new JwtProvider(
                TEST_SECRET,
                -60_000L,
                -60_000L
        );

        String expiredAccessToken =
                expiredTokenProvider.generateAccessToken(1L);

        assertUnauthorized(
                () -> jwtProvider.getUserIdFromAccessToken(expiredAccessToken)
        );
    }

    @Test
    void 서명이_변조된_액세스_토큰은_UNAUTHORIZED_예외를_던진다() {
        String accessToken = jwtProvider.generateAccessToken(1L);

        /*
         * JWT의 header.payload.signature 중 signature 첫 문자를 변경한다.
         */
        String[] parts = accessToken.split("\\.");
        String signature = parts[2];

        char first = signature.charAt(0);
        char changed = first == 'A' ? 'B' : 'A';

        String tamperedToken = parts[0]
                + "."
                + parts[1]
                + "."
                + changed
                + signature.substring(1);

        assertUnauthorized(
                () -> jwtProvider.getUserIdFromAccessToken(tamperedToken)
        );
    }

    @Test
    void 잘못된_문자열을_토큰으로_사용하면_UNAUTHORIZED_예외를_던진다() {
        String invalidToken = "invalid.jwt.token";

        assertUnauthorized(
                () -> jwtProvider.getUserIdFromAccessToken(invalidToken)
        );
    }

    @Test
    void 사용자_ID가_null이면_액세스_토큰_생성에_실패한다() {
        assertThatThrownBy(
                () -> jwtProvider.generateAccessToken(null)
        )
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 사용자_ID가_null이면_리프레시_토큰_생성에_실패한다() {
        assertThatThrownBy(
                () -> jwtProvider.generateRefreshToken(null)
        )
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 실행 중 발생한 예외가 인증 실패 예외인지 공통으로 검증한다.
     */
    private void assertUnauthorized(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).getErrorCode()
                )
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
    }
}
