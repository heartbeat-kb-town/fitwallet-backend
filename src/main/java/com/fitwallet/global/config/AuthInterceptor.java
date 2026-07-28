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
 * <p><b>임시 구현이다.</b> 인증(JWT)이 아직 없어 {@code X-User-Id} 헤더를 그대로 신뢰한다.
 * 인증 도입 시 이 클래스 내부만 토큰 검증으로 교체하면 되고,
 * 컨트롤러와 {@link com.fitwallet.global.common.annotation.LoginUserId}는 손대지 않는다.
 */
public class AuthInterceptor implements HandlerInterceptor {

    public static final String LOGIN_USER_ID_ATTRIBUTE = "loginUserId";

    private static final String USER_ID_HEADER = "X-User-Id";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(LOGIN_USER_ID_ATTRIBUTE, parseUserId(request.getHeader(USER_ID_HEADER)));
        return true;
    }

    private Long parseUserId(String rawUserId) {
        if (rawUserId == null || rawUserId.isBlank()) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        try {
            return Long.valueOf(rawUserId.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }
}
