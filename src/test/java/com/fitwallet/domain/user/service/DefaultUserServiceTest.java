package com.fitwallet.domain.user.service;

import com.fitwallet.domain.user.dto.request.SignUpRequest;
import com.fitwallet.domain.user.dto.request.UserLoginRequest;
import com.fitwallet.domain.user.dto.response.UserLoginInfoResponse;
import com.fitwallet.domain.user.dto.response.UserLoginTokenResponse;
import com.fitwallet.domain.user.exception.UserErrorCode;
import com.fitwallet.domain.user.mapper.UserMapper;
import com.fitwallet.global.config.JwtProvider;
import com.fitwallet.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Service 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
class DefaultUserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private DefaultUserService userService;

    @Test
    void 비밀번호와_비밀번호확인이_다르면_PASSWORD_MISMATCH_예외를_던진다() {
        SignUpRequest request = signUpRequest(
                "test-user",
                "password123",
                "different123"
        );

        assertThatThrownBy(() -> userService.signUp(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(UserErrorCode.PASSWORD_MISMATCH);

        then(userMapper).shouldHaveNoInteractions();
        then(passwordEncoder).shouldHaveNoInteractions();
    }

    @Test
    void 아이디가_중복이면_DUPLICATE_LOGIN_ID_예외를_던진다() {
        SignUpRequest request = signUpRequest(
                "duplicate-user",
                "password123",
                "password123"
        );
        given(userMapper.existsByLoginId("duplicate-user")).willReturn(true);

        assertThatThrownBy(() -> userService.signUp(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(UserErrorCode.DUPLICATE_LOGIN_ID);

        then(passwordEncoder).shouldHaveNoInteractions();
        then(userMapper).should(never()).insertUser(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void 회원가입시_비밀번호를_암호화해_저장한다() {
        SignUpRequest request = signUpRequest(
                "new-user",
                "password123",
                "password123"
        );

        given(userMapper.existsByLoginId("new-user")).willReturn(false);
        given(passwordEncoder.encode("password123"))
                .willReturn("encoded-password");

        userService.signUp(request);

        then(passwordEncoder).should().encode("password123");
        then(userMapper).should()
                .insertUser(request, "encoded-password");
    }

    @Test
    void 로그인_성공시_토큰을_발급하고_리프레시토큰_해시를_저장한다() throws NoSuchAlgorithmException {
        UserLoginRequest request = loginRequest("test-user", "password123");
        given(userMapper.findLoginInfoByLoginId("test-user")).willReturn(
                UserLoginInfoResponse.builder()
                        .userId(1L)
                        .passwordHash("encoded-password")
                        .build()
        );
        given(passwordEncoder.matches("password123", "encoded-password")).willReturn(true);
        given(jwtProvider.generateAccessToken(1L)).willReturn("access-token");
        given(jwtProvider.generateRefreshToken(1L)).willReturn("refresh-token");
        given(jwtProvider.getRefreshTokenExpirationSeconds()).willReturn(1_209_600L);

        UserLoginTokenResponse tokens = userService.login(request);

        assertThat(tokens.getAccessToken()).isEqualTo("access-token");
        assertThat(tokens.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(tokens.getRefreshTokenExpirationSeconds()).isEqualTo(1_209_600L);

        String expectedHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest("refresh-token".getBytes(StandardCharsets.UTF_8))
        );
        then(userMapper).should().saveOrUpdateRefreshToken(1L, expectedHash);
    }

    @Test
    void 존재하지_않는_아이디로_로그인하면_INVALID_CREDENTIALS_예외를_던진다() {
        UserLoginRequest request = loginRequest("no-such-user", "password123");
        given(userMapper.findLoginInfoByLoginId("no-such-user")).willReturn(null);

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(UserErrorCode.INVALID_CREDENTIALS);

        then(passwordEncoder).shouldHaveNoInteractions();
        then(jwtProvider).shouldHaveNoInteractions();
    }

    @Test
    void 비밀번호가_틀리면_INVALID_CREDENTIALS_예외를_던진다() {
        UserLoginRequest request = loginRequest("test-user", "wrong-password");
        given(userMapper.findLoginInfoByLoginId("test-user")).willReturn(
                UserLoginInfoResponse.builder()
                        .userId(1L)
                        .passwordHash("encoded-password")
                        .build()
        );
        given(passwordEncoder.matches("wrong-password", "encoded-password")).willReturn(false);

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(UserErrorCode.INVALID_CREDENTIALS);

        then(jwtProvider).shouldHaveNoInteractions();
    }

    private UserLoginRequest loginRequest(String loginId, String password) {
        UserLoginRequest request = new UserLoginRequest();

        ReflectionTestUtils.setField(request, "loginId", loginId);
        ReflectionTestUtils.setField(request, "password", password);

        return request;
    }

    private SignUpRequest signUpRequest(
            String loginId,
            String password,
            String passwordConfirm
    ) {
        SignUpRequest request = new SignUpRequest();

        ReflectionTestUtils.setField(request, "name", "테스트회원");
        ReflectionTestUtils.setField(request, "loginId", loginId);
        ReflectionTestUtils.setField(request, "phone", "01012345678");
        ReflectionTestUtils.setField(request, "password", password);
        ReflectionTestUtils.setField(
                request,
                "passwordConfirm",
                passwordConfirm
        );
        ReflectionTestUtils.setField(
                request,
                "marketingAgreed",
                false
        );

        return request;
    }
}
