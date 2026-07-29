package com.fitwallet.global.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인한 사용자의 {@code user_id}를 컨트롤러 파라미터로 주입받는다.
 *
 * <pre>
 * &#64;GetMapping("/api/cards")
 * public List&lt;CardListResponse&gt; myCards(@LoginUserId Long userId) { ... }
 * </pre>
 *
 * 컨트롤러에서 {@code @RequestParam userId}, 헤더 직접 읽기, 세션 접근은 모두 금지한다.
 * 인증 방식이 바뀌어도 {@code AuthInterceptor} 내부만 교체하면 컨트롤러는 그대로 둔다.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginUserId {
}
