package com.fitwallet.global.config;

import com.fitwallet.global.common.annotation.LoginUserId;
import com.fitwallet.global.exception.BusinessException;
import com.fitwallet.global.exception.CommonErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link LoginUserId}가 붙은 {@code Long} 파라미터에
 * {@link AuthInterceptor}가 담아둔 사용자 식별자를 주입한다.
 * {@code servlet-context.xml}의 {@code <argument-resolvers>}에 등록된다.
 */
public class LoginUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUserId.class)
                && Long.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {

        Object userId = webRequest.getAttribute(
                AuthInterceptor.LOGIN_USER_ID_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);

        // 인터셉터가 매핑되지 않은 경로에서 @LoginUserId를 쓰면 여기로 온다.
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
