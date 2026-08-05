package com.fitwallet.global.config;

import com.fitwallet.global.exception.BusinessException;
import com.fitwallet.global.exception.CommonErrorCode;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 요청에서 인증 주체를 꺼내 요청 속성에 담는다.
 * {@code servlet-context.xml}의 {@code <interceptors>}에서 {@code /api/**}에 등록된다.
 *
 * <p>{@code Authorization: Bearer {Access Token}} 헤더를 검증해 사용자 식별자를 꺼낸다.
 * 서명·만료·토큰 타입 검증은 {@link JwtProvider}가 담당하며, 검증에 실패하면
 * {@link BusinessException}을 던져 {@code GlobalExceptionHandler}가 401로 응답한다.
 */
public class AuthInterceptor implements HandlerInterceptor {

    public static final String LOGIN_USER_ID_ATTRIBUTE = "loginUserId";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    public AuthInterceptor(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long userId = jwtProvider.getUserIdFromAccessToken(extractAccessToken(request));
        request.setAttribute(LOGIN_USER_ID_ATTRIBUTE, userId);
        return true;
    }

    private String extractAccessToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }

        String accessToken = authorizationHeader.substring(BEARER_PREFIX.length()).trim();

        if (accessToken.isBlank()) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }

        return accessToken;
    }
}
