package com.fitwallet.domain.user.service;

import com.fitwallet.domain.user.dto.request.SignUpRequest;
import com.fitwallet.domain.user.dto.request.UserLoginRequest;
import com.fitwallet.domain.user.dto.response.FrequentPlaceResponse;
import com.fitwallet.domain.user.dto.response.TokenReissueResponse;
import com.fitwallet.domain.user.dto.response.UserLoginInfoResponse;
import com.fitwallet.domain.user.dto.response.UserLoginTokenResponse;
import com.fitwallet.domain.user.exception.UserErrorCode;
import com.fitwallet.domain.user.mapper.UserMapper;
import com.fitwallet.global.config.JwtProvider;
import com.fitwallet.global.exception.BusinessException;
import com.fitwallet.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultUserService implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    /**
     * 일반 회원가입을 처리한다.
     * <p>
     * 비밀번호와 비밀번호 확인값의 일치 여부를 검증하고,
     * 로그인 아이디가 중복되지 않은 경우 비밀번호를 암호화해 저장한다.
     */
    @Override
    @Transactional
    public void signUp(SignUpRequest request) {
        validatePasswordConfirmation(request);

        if (userMapper.existsByLoginId(request.getLoginId())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_LOGIN_ID);
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());

        userMapper.insertUser(request, passwordHash);
    }

    /** 비밀번호와 비밀번호 확인값이 일치하는지 검증한다. */
    private void validatePasswordConfirmation(SignUpRequest request) {
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new BusinessException(UserErrorCode.PASSWORD_MISMATCH);
        }
    }

    /**
     * 일반 로그인을 처리한다.
     * <p>
     * 아이디와 비밀번호를 같은 실패 응답으로 검증하고, 성공하면 두 토큰을 발급한다.
     * Refresh Token 원문은 클라이언트 쿠키로 전달하고 DB에는 SHA-256 해시만 저장한다.
     */
    @Override
    @Transactional
    public UserLoginTokenResponse login(UserLoginRequest request) {
        UserLoginInfoResponse loginInfo =
                userMapper.findLoginInfoByLoginId(request.getLoginId());

        if (loginInfo == null || !passwordEncoder.matches(
                request.getPassword(),
                loginInfo.getPasswordHash()
        )) {
            throw new BusinessException(UserErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = jwtProvider.generateAccessToken(loginInfo.getUserId());
        String refreshToken = jwtProvider.generateRefreshToken(loginInfo.getUserId());

        userMapper.saveOrUpdateRefreshToken(loginInfo.getUserId(), hashToken(refreshToken));

        return UserLoginTokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .refreshTokenExpirationSeconds(
                        jwtProvider.getRefreshTokenExpirationSeconds()
                )
                .build();
    }

    /** 최근 1개월간 가장 자주 결제한 장소를 최대 3개 조회한다. 집계는 Mapper가 전담한다. */
    @Override
    @Transactional(readOnly = true)
    public List<FrequentPlaceResponse> findFrequentPlaces(Long userId) {
        return userMapper.findFrequentPlaces(userId);
    }

    /**
     * Access Token을 재발급한다.
     * <p>
     * 서명·만료·타입을 검증하고 DB에 저장된 해시와 대조만 한 뒤 그대로 유지한다.
     */
    @Override
    @Transactional(readOnly = true)
    public TokenReissueResponse reissueAccessToken(String refreshToken) {
        Long userId = jwtProvider.getUserIdFromRefreshToken(refreshToken);

        String storedTokenHash = userMapper.findRefreshTokenHashByUserId(userId);

        if (storedTokenHash == null || !storedTokenHash.equals(hashToken(refreshToken))) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }

        String accessToken = jwtProvider.generateAccessToken(userId);

        return TokenReissueResponse.builder()
                .accessToken(accessToken)
                .build();
    }

    /**
     * DB에는 원문 대신 SHA-256 해시를 저장한다.
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
