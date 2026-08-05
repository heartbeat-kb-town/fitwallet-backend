package com.fitwallet.global.config;

import com.fitwallet.global.common.annotation.LoginUserId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.oas.annotations.EnableOpenApi;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.AuthorizationScope;
import springfox.documentation.service.HttpAuthenticationScheme;
import springfox.documentation.service.SecurityReference;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.service.contexts.SecurityContext;
import springfox.documentation.spring.web.plugins.Docket;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * Swagger(OpenAPI 3.0) 문서 설정. {@code /v3/api-docs}와 {@code /swagger-ui/index.html}을 연다.
 *
 * <p><b>이 저장소의 유일한 자바 {@code @Configuration}이다.</b> 설정은 전부 XML이라는 관례(AGENTS.md §1)의
 * 의도된 예외다 — Docket은 빌더 체인으로만 구성할 수 있어 XML {@code <bean>}으로 옮기면
 * {@code MethodInvokingFactoryBean}을 겹겹이 쌓아야 하고, 그 편이 훨씬 읽기 어렵다.
 *
 * <p><b>배선 주의.</b> 이 클래스는 반드시 <b>웹 컨텍스트</b>(servlet-context.xml)에만 등록돼야 한다.
 * springfox는 {@code RequestMappingHandlerMapping}을 읽어 문서를 만드는데 그 빈이 웹 컨텍스트에만 있다.
 * 그런데 두 컨텍스트의 스캔 설정이 정확히 반대로 동작한다:
 * <ul>
 *   <li>servlet-context.xml은 {@code use-default-filters="false"}라 {@code @Configuration}을 <b>못 잡는다</b>
 *       → 명시적 {@code <bean class="...SwaggerConfig"/>}로 등록한다</li>
 *   <li>root-context.xml은 {@code com.fitwallet}을 기본 필터로 스캔해 <b>잡아버린다</b>
 *       → {@code exclude-filter}로 제외한다. 안 하면 핸들러 매핑이 없는 루트 컨텍스트에 springfox 인프라가
 *       중복 생성돼 문서가 비거나 기동이 깨진다</li>
 * </ul>
 *
 * <p>Swagger UI 정적 리소스도 비-Boot 환경에서는 자동 등록되지 않아
 * servlet-context.xml의 {@code <mvc:resources>}로 직접 매핑한다.
 */
@Configuration
@EnableOpenApi
public class SwaggerConfig {

    /** securitySchemes에 등록한 이름. securityContexts가 이 이름으로 스킴을 참조한다. */
    private static final String BEARER_AUTH = "bearerAuth";

    /** 인증 없이 호출하는 엔드포인트. servlet-context.xml의 AuthInterceptor exclude-mapping과 같아야 한다. */
    private static final List<String> PUBLIC_PATHS =
            List.of("/api/user/signup", "/api/user/login", "/api/user/reissue");

    @Bean
    public Docket api() {
        return new Docket(DocumentationType.OAS_30)
                .select()
                // HomeController(`/`, `/health/db`)는 API가 아니라 기동 확인용이라 문서에서 뺀다.
                .apis(RequestHandlerSelectors.basePackage("com.fitwallet.domain"))
                .paths(PathSelectors.ant("/api/**"))
                .build()
                .apiInfo(apiInfo())
                // 컨트롤러가 전부 ResponseEntity<ApiResponse<T>>를 반환한다. ResponseEntity를 벗겨야
                // 프론트가 실제로 받는 모양(ApiResponse 봉투)이 스키마 최상위로 올라온다.
                .genericModelSubstitutes(ResponseEntity.class)
                // @LoginUserId는 AuthInterceptor가 넣어준 값을 꺼내는 것이지 클라이언트가 보내는 값이 아니다.
                // 빼지 않으면 인증이 필요한 모든 엔드포인트에 존재하지도 않는 userId 파라미터가 문서에 뜨고,
                // 프론트가 그대로 구현하면 바로 틀린다. HttpServletResponse도 같은 이유로 뺀다
                // (UserController.login이 Refresh Token 쿠키를 심으려고 받는 서버 측 객체다).
                .ignoredParameterTypes(LoginUserId.class, HttpServletResponse.class)
                .securitySchemes(List.of(bearerScheme()))
                .securityContexts(List.of(securityContext()));
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("fitwallet API")
                .description("""
                        최적의 결제수단을 추천하고 놓친 혜택을 알려주는 스마트 전자지갑 fitwallet의 백엔드 API.

                        **인증** — `POST /api/user/login`으로 받은 accessToken을 우측 상단 `Authorize`에 넣으면
                        이후 요청에 `Authorization: Bearer {token}` 헤더가 자동으로 붙습니다.
                        Refresh Token은 응답 바디가 아니라 HttpOnly 쿠키로 내려갑니다.

                        **응답 형태** — `/api` 아래 모든 응답은 `success`/`code`/`message`/`data` 봉투에 담깁니다.
                        실제 데이터는 스키마의 `data`를 열어야 나옵니다.
                        """)
                .version("v1")
                .build();
    }

    private HttpAuthenticationScheme bearerScheme() {
        return HttpAuthenticationScheme.JWT_BEARER_BUILDER
                .name(BEARER_AUTH)
                .description("로그인 응답의 accessToken을 그대로 붙여넣는다. `Bearer ` 접두사는 자동으로 붙는다.")
                .build();
    }

    /**
     * 자물쇠 아이콘이 붙는 범위. 회원가입·로그인은 AuthInterceptor가 제외하는 경로라 여기서도 뺀다 —
     * 인증이 필요 없는 API에 자물쇠가 붙어 있으면 프론트가 토큰을 먼저 구하려다 막힌다.
     */
    private SecurityContext securityContext() {
        return SecurityContext.builder()
                .securityReferences(List.of(
                        new SecurityReference(BEARER_AUTH, new AuthorizationScope[0])))
                .operationSelector(context -> !PUBLIC_PATHS.contains(context.requestMappingPattern()))
                .build();
    }
}
