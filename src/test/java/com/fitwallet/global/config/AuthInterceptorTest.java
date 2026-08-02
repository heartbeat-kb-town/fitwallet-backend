package com.fitwallet.global.config;

import com.fitwallet.global.exception.BusinessException;
import com.fitwallet.global.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthInterceptorTest {

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private AuthInterceptor authInterceptor;

    @Test
    void Authorization_헤더가_없으면_UNAUTHORIZED_예외를_던진다() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> authInterceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
    }

    @Test
    void Bearer_접두사가_없으면_UNAUTHORIZED_예외를_던진다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "access-token-value");

        assertThatThrownBy(() -> authInterceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
    }

    @Test
    void Bearer_뒤에_토큰이_없으면_UNAUTHORIZED_예외를_던진다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer ");

        assertThatThrownBy(() -> authInterceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
    }

    @Test
    void 유효한_Access_Token이면_사용자_ID를_요청_속성에_저장한다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access-token-value");
        given(jwtProvider.getUserIdFromAccessToken("access-token-value")).willReturn(1L);

        boolean result = authInterceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(result).isTrue();
        assertThat(request.getAttribute(AuthInterceptor.LOGIN_USER_ID_ATTRIBUTE)).isEqualTo(1L);
    }
}
