# fitwallet-backend

최적의 결제수단을 추천하고 놓친 혜택을 알려주는 스마트 전자지갑 fitwallet의 백엔드 서버.

이 문서가 **코드 컨벤션과 작업 플로우의 정본**이다. `CLAUDE.md`는 이 파일을 가져다 쓴다.
사람이 읽는 기여 가이드는 [CONTRIBUTING.md](./CONTRIBUTING.md), 스키마 설명은 [docs/erd.md](./docs/erd.md)에 있다.

## 빌드 · 실행

```bash
docker compose up -d      # 로컬 MySQL (빈 DB. 스키마·시드는 앱이 뜰 때 Flyway가 넣는다 — §11)
./gradlew build           # 컴파일 + 테스트 + war 패키징 — MySQL 필요
./gradlew appRun          # http://localhost:8080 (Gretty)
./gradlew test            # 테스트만 — MySQL 필요
```

---

## 1. 기술 스택

**Spring Boot가 아니다.** 순수 Spring Framework MVC를 WAR로 패키징해 서블릿 컨테이너에서 돌린다.
이 스택은 제약이므로 Boot로 전환하지 않는다.

| 항목 | 버전 |
|---|---|
| Spring Framework | 5.3.39 |
| Java / Gradle | 17 / 8.13 |
| 서블릿 | `javax.servlet` 4.0.1 (**jakarta 아님**) |
| 구동 | `war` + Gretty (Tomcat 9), port 8080 |
| 영속성 | MyBatis 3.5.16 + mybatis-spring 2.1.2 |
| 커넥션 풀 | HikariCP 5.1.0 |
| 검증 | Hibernate Validator 6.2.5.Final + validation-api 2.0.1.Final |
| JSON | Jackson 2.17.2 (+ jsr310) |
| 인증 | Spring Security Crypto 5.7.1 (BCrypt) + JJWT 0.13.0 |
| 테스트 | JUnit 5, spring-test, AssertJ 3.26.3, Mockito 5.12.0 |

> ⚠️ `javax` 기반이므로 **jakarta 계열 라이브러리를 넣으면 안 된다.**
> Hibernate Validator는 반드시 6.x를 쓴다 (7/8은 jakarta).

설정은 전부 XML이다 — `src/main/resources/root-context.xml`(루트 컨텍스트),
`src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml`(웹 컨텍스트), `web.xml`.

비밀번호 해시는 Spring Security Crypto 5.7.1의 BCrypt를, JWT는 JJWT 0.13.0을 사용한다.

---

## 2. 패키지 / 도메인 구조

도메인은 6개다: `user`, `card`, `store`, `benefit`, `payment`, `report`.

**도메인은 테이블을 소유하지 않는다.** 각 도메인의 Mapper가 필요한 여러 테이블을 조인해 접근한다.
따라서 테이블과 1:1 대응하는 `entity`나 `vo`를 만들지 않고, **Mapper가 Response DTO를 직접 반환**한다.
스키마가 바뀌면 영향받는 Mapper와 XML을 각각 고친다.

```
com.fitwallet
├─ domain/{user,card,store,benefit,payment,report}/
│  ├─ controller/
│  ├─ service/                  # 인터페이스 + Default 구현체
│  ├─ mapper/                   # MyBatis 인터페이스
│  ├─ dto/
│  │  ├─ *.java                 # enum, SuccessCode (CardType, CardSuccessCode 등)
│  │  ├─ request/
│  │  └─ response/
│  └─ exception/                # {도메인}ErrorCode
└─ global/
   ├─ common/{annotation,dto}/
   ├─ config/
   └─ exception/

src/main/resources/mapper/{도메인}/{도메인}Mapper.xml
```

**`domain/card`가 참조 구현이다.** 새 도메인은 이걸 복제해서 시작한다.

이 구조의 대가로 MyBatis XML이 API 계약에 묶인다. API 응답 스펙이 바뀌면 XML도 함께 고쳐야 한다.

---

## 3. 네이밍 컨벤션

| 대상 | 규칙 | 예 |
|---|---|---|
| 패키지 | 소문자·단수 | `com.fitwallet.domain.card` |
| 컨트롤러/매퍼 | `{도메인}Controller` / `Mapper` | `CardController` |
| 서비스 인터페이스/구현체 | `{도메인}Service` / `Default{도메인}Service` | `CardService` / `DefaultCardService` |
| 매퍼 XML | `resources/mapper/{도메인}/{도메인}Mapper.xml` | `mapper/card/CardMapper.xml` |
| 요청 DTO | `{동작}{대상}Request` | `CardRegisterRequest` |
| 응답 DTO | `{대상}{용도}Response` | `CardListResponse` |
| 조회 조건 | `{대상}SearchCondition` | `PaymentSearchCondition` |
| 매퍼 메서드 | 조회 `find*`/`count*`/`exists*`, 변경 `insert*`/`update*`/`delete*` | `findByUserId` |
| enum 상수 | DDL의 CHECK 값 그대로 | `CardType.CREDIT` |
| DB → 필드 | `snake_case` → `camelCase` | `user_card_id` → `userCardId` |
| 테스트 클래스 | `{대상}Test` / `{대상}IntegrationTest` | `DefaultCardServiceTest` |
| 테스트 메서드 | 한글 + 언더스코어 | `카드_등록시_중복이면_예외를_던진다()` |

---

## 4. DTO 규칙

`record`를 쓰지 않는다. Request·Response 모두 **class + Lombok**으로 통일한다.

Request·Response는 HTTP 바디 전용이 아니다. **Mapper 파라미터·반환 타입으로 그대로 쓰인다** —
Controller에서 끝나지 않고 Service·Mapper·XML까지 같은 객체가 관통한다.

```
Request   Controller ──▶ Service ──▶ Mapper ──▶ XML(SQL) ──▶ DB
          (CardRegisterRequest가 Mapper 파라미터까지 그대로 전달된다)

Response  Controller ◀── Service ◀── Mapper ◀── XML(SQL) ◀── DB
          (CardListResponse는 Mapper가 SQL 결과를 바로 채워 반환한다)
```

```java
// Request
@Getter
@NoArgsConstructor
public class CardRegisterRequest {
    @NotNull  private Long cardProductId;
    @NotBlank @Size(min = 4, max = 4) private String first4;
}

// Response — MyBatis가 리플렉션으로 채운다
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardListResponse {
    private Long userCardId;
    private CardType cardType;
}
```

- **`@Setter` 금지** (양쪽 모두)
- Mapper 파라미터가 2개 이상이면 **`@Param` 필수**
- 인증 컨텍스트(`userId`)는 Request DTO에 넣지 않고 **항상 별도 파라미터**로 전달
- **`{대상}SearchCondition`을 `@ModelAttribute`로 바인딩할 때는 컨트롤러에 `@InitBinder`로
  `binder.initDirectFieldAccess()`를 설정한다.** POST 바디(Jackson)는 `@Setter` 없이도
  역직렬화되지만, GET 쿼리 파라미터를 객체로 묶는 `WebDataBinder`는 기본값이 JavaBean
  프로퍼티(setter) 접근이라 `@Setter` 금지와 충돌한다 — 설정하지 않으면 예외 없이 필드가
  전부 `null`로 조용히 남는다(`StoreController` 참고)

---

## 5. 응답 포맷 / 예외 처리

**경로와 응답 필드는 [API 명세](https://app.notion.com/p/3a6a561881a480d0b24afb20e24190ef)를 따른다.**
새 엔드포인트를 만들기 전에 명세에서 해당 행을 먼저 확인한다.

> ⚠️ `code`는 명세가 번호식(`STORE_400_01`)이어도 **enum 상수 이름**으로 쓰고(`message`는 명세
> 문구 그대로), 401은 도메인 ErrorCode를 만들지 않고 공통 `CommonErrorCode.UNAUTHORIZED`를 쓴다.

`/api` 아래 모든 응답은 `ApiResponse<T>` 봉투에 담긴다. HTTP 상태코드도 의미대로 쓴다.

```json
// 성공
{ "success": true, "code": "USER_CARDS_FOUND",
  "message": "보유 카드 목록을 조회했습니다.", "data": [ ... ] }

// 실패 — data는 기본적으로 null이다
{ "success": false, "code": "CARD_NOT_FOUND",
  "message": "카드를 찾을 수 없습니다.", "data": null }

// 실패 + 부가 데이터 — 명세가 요구할 때만 data를 채운다
{ "success": false, "code": "PIN_MISMATCH",
  "message": "비밀번호가 일치하지 않습니다.", "data": { "remainingAttempts": 2 } }

// 검증 실패 — errors가 추가된다 (이때만 나온다)
{ "success": false, "code": "INVALID_INPUT_VALUE",
  "message": "입력값이 올바르지 않습니다.", "data": null,
  "errors": [ { "field": "first4", "reason": "..." } ] }
```

성공/실패 모두 `code`와 `message`를 갖는다. 문자열을 컨트롤러에 흩뿌리지 않도록 **양쪽 다 enum**으로 관리한다.

| | 인터페이스 | 도메인별 enum |
|---|---|---|
| 성공 | `global/common/code/SuccessCode` | `domain/card/dto/CardSuccessCode` |
| 실패 | `global/exception/ErrorCode` | `domain/card/exception/CardErrorCode` |

- `code`는 enum 상수 이름(`name()`) 그대로. 둘 다 HTTP 상태코드를 함께 들고 있다
- 전역 단일 enum을 쓰지 않는다 — 6명이 병렬로 개발하면 그 파일에서 계속 충돌한다
- 예외는 **`BusinessException` 하나만** 쓴다. 새 에러는 예외 클래스가 아니라 ErrorCode 상수를 추가한다
- **컨트롤러에서 try-catch로 에러 응답을 만들지 않는다.** `GlobalExceptionHandler`가 전부 처리한다
- 실패 응답에 부가 데이터가 필요하면(예: 남은 시도 횟수) `BusinessException`/`ApiResponse.error()`의
  2-인자 오버로드를 쓴다. 안 쓰면 기존과 동일하게 `data: null`로 나간다

```java
// 컨트롤러 — 성공 응답
return ApiResponse.of(CardSuccessCode.USER_CARDS_FOUND, cardService.findMyCards(userId));

// 서비스 — 실패 (data 없음)
throw new BusinessException(CardErrorCode.CARD_NOT_FOUND);

// 서비스 — 실패 + 부가 데이터
throw new BusinessException(PaymentErrorCode.PIN_MISMATCH,
        PinMismatchResponse.builder().remainingAttempts(remainingAttempts).build());
```

### API 계약의 정본은 코드다

**프론트엔드는 이 저장소를 직접 읽는다.** 컨트롤러·DTO·`ErrorCode`·매퍼 XML이 곧 API 문서다.
Swagger는 경로 목록을 훑는 보조 수단이며 **정본이 아니다.**

> 노션 API 명세도 대체 관계가 아니다. **노션은 "왜/무엇을"의 정본, 코드는 "실제 계약"의 정본**이다.
> 둘이 어긋나면 코드가 맞고 노션을 고친다.

#### 코드가 문서다

어노테이션을 강제하지 않는 대신, **아래 세 가지는 프론트가 그대로 읽는다는 전제로 쓴다.**
어차피 써야 하는 것들이라 추가 부담이 없고, 손으로 베낀 사본과 달리 드리프트하지 않는다.

- **`{도메인}ErrorCode`의 `message`** — 에러 계약의 정본. 프론트가 화면에 띄울 문구다
- **검증 어노테이션의 `message`** — `@Pattern(regexp = "\\d{4}", message = "카드번호 앞 4자리를 숫자로 입력해주세요.")`
  처럼 규칙과 문구를 함께 적는다. 폼 검증이 이걸 그대로 쓴다
- **DTO 필드명과 `{도메인}SuccessCode`** — 이름 자체가 설명이 되도록 짓는다

의미가 이름만으로 안 드러나면 **주석/Javadoc에 적는다.** 예를 들어 `cardId` 경로 변수는
`card_product_id`가 아니라 `user_card_id`인데, 이런 건 읽는 쪽이 조용히 틀리는 지점이라 반드시 남긴다.

> ⚠️ `resultType` 매핑은 SQL이 안 뽑은 컬럼을 조용히 버린다(§6). **Java DTO에 필드가 있어도
> 매퍼 XML이 그 컬럼을 SELECT하지 않으면 항상 `null`이다.** 응답 모양을 확인할 때는 DTO와
> 매퍼 XML을 함께 본다 — 이건 Swagger 스펙으로는 절대 드러나지 않는다.

#### Swagger 어노테이션 — 권장, 강제 아님

Springfox 3.0.0이 `/v3/api-docs`(OpenAPI 3.0 JSON)와 `/swagger-ui/index.html`을 만든다.
설정은 `global/config/SwaggerConfig`에 있다 — **이 저장소의 유일한 자바 `@Configuration`**이며
§1의 "설정은 전부 XML" 관례에 대한 의도된 예외다(Docket은 빌더 체인이라 XML로 옮기면 더 읽기 어렵다).

- **기존 코드를 소급해 채우지 않는다.** benefit·card에 붙어 있는 것은 그대로 두고, 나머지 도메인에
  맞추려 백필하지 않는다
- **새 엔드포인트에는 여력이 될 때만 붙인다.** 짜면서 쓰면 1분이고 그때는 머릿속에 다 있다.
  빠뜨렸다고 리뷰에서 막지 않는다
- 어노테이션이 없으면 Swagger는 자바 메서드명·파라미터명을 fallback으로 쓴다. 비지 않는다

붙일 때만 아래를 지킨다.

```java
@Api(tags = "혜택")                                    // ① 컨트롤러 클래스
public class BenefitController {

    @ApiOperation(value = "예상 혜택 조회", notes = """  // ② 메서드
            가맹점 기준으로 내 카드별 예상 혜택을 판정한다.

            | HTTP | code | message |
            |---|---|---|
            | 400 | STORE_ID_REQUIRED | 가맹점 ID가 필요합니다. |
            | 404 | STORE_NOT_FOUND | 가맹점을 찾을 수 없습니다. |
            """)
    @GetMapping("/benefit/expected")
    public ResponseEntity<ApiResponse<ExpectedBenefitResponse>> findExpectedBenefits(
            @LoginUserId Long userId,                                        // ④ 아무것도 달지 않는다
            @ApiParam(value = "가맹점 ID(숫자 문자열)")                        // ③ 파라미터
            @RequestParam(required = false) String storeId) { ... }
}
```

- **`@ApiResponses` / `io.swagger.annotations.ApiResponse`를 쓰지 않는다.** 응답 봉투
  `global/common/dto/ApiResponse`와 클래스명이 정면 충돌해 풀 패키지명을 써야 한다.
  에러 응답은 위처럼 `@ApiOperation(notes)`에 마크다운 표로 적는다 — **정본은 `ErrorCode` enum이다**
- Request·Response DTO 필드에는 `@ApiModelProperty(value = "...", example = "...")`
- **`@ApiParam`에 `example`을 쓰지 않는다.** Springfox 3의 OAS30 출력에서 조용히 누락된다
  (`value`와 `required`만 살아남는다)
- **`@LoginUserId` 파라미터에는 아무것도 달지 않는다.** Docket이 통째로 무시한다 —
  클라이언트가 보내는 값이 아니라 `AuthInterceptor`가 넣어준 값이라 문서에 뜨면 안 된다.
  `HttpServletResponse`도 같은 이유로 무시된다
- 문서에 나오는 범위는 **`com.fitwallet.domain` 패키지 + `/api/**` 경로**뿐이다
  (`HomeController`가 그래서 빠져 있다)
- 인증이 필요 없는 엔드포인트는 `SwaggerConfig.PUBLIC_PATHS`에 추가한다
  (`servlet-context.xml`의 `AuthInterceptor` exclude-mapping과 **같은 목록을 유지한다**)

> **JSR-303 검증 규칙은 스펙에 반영되지 않는다.** `springfox-bean-validators`가 없어
> `@Pattern`·`@Size`·`@NotNull`이 스키마에 0건 실린다. 프론트가 폼 검증에 필요한 정규식과
> 에러 문구는 **소스에만 있다** — 어노테이션을 아무리 더 달아도 채워지지 않는 항목이다.
> Swagger를 정본으로 삼지 않는 이유 중 하나다.

로컬에서 스펙을 확인하고 싶을 때만 직접 돌린다. 커밋 대상이 아니다(`.gitignore`).

```bash
./gradlew openapiDump      # 앱을 띄워 /v3/api-docs를 docs/openapi.json에 저장 (docker compose MySQL 필요)
```

Gretty의 `integrationTestTask` 훅에 얹어 서버 기동·종료를 자동으로 처리한다
(`appStart`를 `dependsOn`으로 걸면 그 자리에서 빌드가 멈춘다).

⚠️ **XML 배선을 잡아주는 자동 테스트가 없다.** 컨트롤러 테스트가 전부 `standaloneSetup`이라 XML을
안 탄다 — `./gradlew build`가 green이어도 Swagger는 깨져 있을 수 있다. 설정을 건드렸으면
`./gradlew appStart` 후 `/v3/api-docs`와 `/swagger-ui/index.html`을 직접 확인한다.

---

## 6. MyBatis 매퍼 규칙

**커스텀 TypeHandler를 만들지 않는다.** DDL의 CHECK 값이 자바 enum 상수 이름과 일치해 기본
`EnumTypeHandler`가 자동 변환하고, `TINYINT(1)`↔`Boolean`·`DECIMAL`↔`BigDecimal`·
`DATETIME`↔`LocalDateTime`도 기본 핸들러가 처리한다. 정말 필요해지면
`global/config/typehandler/`에 만들고 `mybatis-config.xml`에 등록한다.

- 전역 설정은 `src/main/resources/mybatis-config.xml` — `mapUnderscoreToCamelCase`, `jdbcTypeForNull`
- **`AS` 별칭을 수동으로 붙이지 않는다.** `mapUnderscoreToCamelCase`가 처리한다
- `<select>`의 `id`는 인터페이스 메서드명과 같아야 한다
- **응답 DTO 모양에 따라 매핑 방식을 고른다:**

  | DTO 모양 | 매핑 | 비고 |
  |---|---|---|
  | 평면 | `resultType` | 못 채운 컬럼은 에러 없이 조용히 버려진다 |
  | 중첩 1:1(객체 하나) | `resultMap` + `<association>` | |
  | 중첩 1:N(목록) | `resultMap` + `<collection>` | |

  `<association>`/`<collection>`이 들어가면 그 resultMap은 자동 매핑이 꺼지므로
  (`autoMappingBehavior` 기본값 `PARTIAL`) 바깥 컬럼도 `<result>`로 전부 명시해야 한다
- **`${}` 금지, `#{}` 필수.** 동적 정렬 컬럼처럼 불가피하면 화이트리스트로 검증한 뒤 쓴다
- **`SELECT *` 금지** — 컬럼을 명시한다. 반복되면 `<sql>` + `<include>`로 뺀다

---

## 7. 페이징

현재 명세에 이용 실적 상세 조회(`GET /api/card/{cardId}/usage`)가
`page`/`size`/`totalElements`/`hasNext`를 쓰는 페이징 응답을 가진다(미구현).
이 API를 구현할 때 명세의 필드를 그대로 따른다.

- 공통 래퍼 클래스는 미리 만들지 않는다 — 다른 페이징이 필요해질 때 모양이 같은지 그때 확인한다
- `{대상}SearchCondition`(§3) 네이밍은 그대로 쓴다

---

## 8. 인증 컨텍스트 전달

컨트롤러가 사용자 식별자를 얻는 경로는 하나뿐이다.

```java
@GetMapping("/api/cards")
public List<CardListResponse> findMyCards(@LoginUserId Long userId) {
    return cardService.findMyCards(userId);
}
```

- **`@RequestParam userId`, 헤더 직접 읽기, 세션 접근은 모두 금지**
- `AuthInterceptor`가 `/api/**`에서 `Authorization: Bearer {Access Token}` 헤더를 검증해
  인증 주체를 요청 속성에 담고, `LoginUserIdArgumentResolver`가 `@LoginUserId` 파라미터에 주입한다
- 서명·만료·토큰 타입(ACCESS/REFRESH) 검증은 `JwtProvider`가 담당한다. 헤더가 없거나
  형식이 틀리거나 검증에 실패하면 `BusinessException(CommonErrorCode.UNAUTHORIZED)`로 401 응답한다
- 인증이 필요 없는 엔드포인트는 `servlet-context.xml`의 `AuthInterceptor` exclude-mapping과
  `SwaggerConfig.PUBLIC_PATHS`(§5) 양쪽에 같은 목록을 유지한다

---

## 9. 트랜잭션 경계

- `@Transactional`은 **Service에만** 붙인다
- 조회 메서드는 **`@Transactional(readOnly = true)`**
- **Controller와 Mapper에는 붙이지 않는다** — Controller에 붙여도 걸리지 않는다.
  `<tx:annotation-driven>`이 루트 컨텍스트에만 있어 웹 컨텍스트에서 스캔되는 Controller엔
  프록시가 안 걸리기 때문이다(`DefaultCardService` 클래스 주석 참고)
- **`@Transactional`은 `Default{도메인}Service` 구현체 메서드에 붙인다. 인터페이스 선언에는 붙이지 않는다**

---

## 10. 스키마 유래 공통 규칙

- **소프트 삭제**: `is_deleted` 컬럼이 있는 테이블은 조회 시 **항상 `is_deleted = 0`** 조건을 건다.
  물리 `DELETE`를 쓰지 않는다 (`payment_transaction`이 `user_card`를 FK 참조한다)
- **금액**: DDL이 전부 `DECIMAL`이므로 자바에서 **`BigDecimal` 필수**. `double`/`float` **금지**
- **감사 컬럼**: `created_at`/`updated_at`는 DB DEFAULT가 채운다 → **INSERT/UPDATE 문에 쓰지 않는다**
- **날짜/시간**: `DATE`→`LocalDate`, `DATETIME`→`LocalDateTime`. 타임존 `Asia/Seoul`, JSON은 ISO-8601
- **불리언**: `TINYINT(1) is_*` 컬럼은 `Boolean`/`boolean`으로 받는다

---

## 11. DB 마이그레이션

스키마와 참조 데이터는 **Flyway가 앱 기동 시점에 적용한다.** 손으로 SQL을 돌리는 절차는 없다.

```
src/main/resources/db/
├─ migration/     # 스키마 + 참조 데이터 — 모든 환경 공통
│  ├─ V1__baseline_schema.sql        기준 스키마 (갱신하지 않는다)
│  ├─ V2__reference_data.sql         카드사·카드·혜택·가맹점 마스터
│  └─ V3.. V6..                      그 뒤의 변경
└─ seed-local/    # 데모 데이터 — 로컬·CI만 (V900)
   └─ V900__demo_data.sql            회원·보유카드·검색기록·결제내역
```

- **스키마를 바꾸려면 `db/migration/`에 `V{다음번호}__{설명}.sql`을 새로 만든다.**
  `V1__baseline_schema.sql`을 고치지 않는다 — 이미 적용된 DB는 다시 읽지 않으므로 아무 효과가 없다
- **마이그레이션은 멱등하게 쓴다.** 운영 RDS의 실제 상태를 완전히 알 수 없어 baseline이
  실제보다 낮게 잡혀 있을 수 있다. 컬럼 추가는 `information_schema`를 확인한 뒤
  `PREPARE`로 실행하고(`V5` 참고), 데이터는 조건 없는 `UPDATE`로 쓴다
- **참조 데이터와 데모 데이터를 섞지 않는다.** 카드·혜택·가맹점 마스터는 모든 환경이 같아야 하니
  `db/migration/`에, 회원·결제내역은 환경마다 달라야 하니 `db/seed-local/`에 둔다.
  운영은 `flyway.locations`에 `db/seed-local`을 넣지 않는다 (§13)
- 적용 시점은 `FlywayMigrator`이고, `sqlSessionFactory`가 `depends-on`으로 그 뒤에 뜬다.
  **마이그레이션이 실패하면 앱이 뜨지 않는다** — 스키마가 안 맞는 코드가 서비스되지 않게 하는 장치다
- CI에서 돌리지 않는 이유는 접근 경로다. 운영 RDS 보안그룹이 3306을 EB 보안그룹과 지정 IP에만
  여는데 GitHub Actions 러너는 IP가 매번 바뀐다. 앱은 이미 접속 권한이 있다

> ⚠️ **`docker compose up -d`는 빈 DB만 만든다.** 스키마는 앱이나 테스트를 처음 돌릴 때 선다.
> 이 구조 이전의 볼륨을 그대로 쓰면 데모 데이터가 중복 적재돼 기동이 실패하므로,
> `docker compose down -v && docker compose up -d`로 볼륨을 새로 만든다.

---

## 12. 테스트 규칙

| 계층 | 도구 | DB | 의무 |
|---|---|---|---|
| Service 단위 | `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks` | 불필요 | **필수** |
| Mapper 통합 | `@SpringJUnitConfig(locations = "classpath:root-context.xml")` + `@Transactional` | 실 MySQL | **필수** |
| Controller | MockMvc `standaloneSetup` + Mock Service | 불필요 | 선택 |

- 테스트 DB는 **docker compose MySQL과 시드 데이터를 그대로 쓴다.** 별도 스키마나 Testcontainers를 두지 않는다
- 격리는 클래스 레벨 `@Transactional` 자동 롤백으로 처리한다. 데이터를 바꾸는 테스트를 써도 된다
- 단언은 **AssertJ `assertThat`으로 통일**한다. JUnit `Assertions.*`를 쓰지 않는다
- 시드 데모 페르소나는 `user_id = 1` (카드 5건, 거래 355건)

> ⚠️ **한 테스트 안에서 "조회 → 변경 → 다시 같은 조회"를 하지 않는다.** 변경 전 상태와
> 변경 후 상태는 **테스트를 나눠서** 검증한다(운영 코드도 마찬가지다).

---

## 13. 환경 / 프로파일

Boot가 아니라 `application-{profile}.yml` 자동 로딩이 없다. 시스템 프로퍼티 `-Denv`로 파일을 고른다.

```
src/main/resources/config/
├─ application-local.properties   # 기본값. docker compose 기준값
└─ application-prod.properties    # 값 자리에 환경변수 참조만
```

- **프로파일 기본값은 `local`.** 지정하지 않으면 로컬로 뜬다 (CI도 이 기본값으로 돈다)
- 운영: `-Denv=prod` + `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` 환경변수.
  환경변수가 없으면 기동 단계에서 실패한다 (의도된 동작)
- **커밋되는 파일에 운영 비밀번호를 넣지 않는다**
- 로컬에서 포트·계정을 바꾸려면 `.env`만 고치면 된다. `build.gradle`이 `.env`를 읽어
  시스템 프로퍼티로 넘겨주므로 `application-local.properties`의 기본값을 덮어쓴다

---

## 14. Git / GitHub 워크플로우

### Git 컨벤션

원본 규칙은 [CONTRIBUTING.md](./CONTRIBUTING.md)에 있다.

- `main`은 **배포 브랜치**다. `develop` → `main` 릴리스 PR로만 갱신하며 병합은 **Merge commit**이다
- `develop`은 **통합 브랜치이자 기본 브랜치**다. 모든 작업 브랜치는 `develop`에서 분기하고
  **PR의 base는 항상 `develop`**이며 병합은 **Squash and Merge**다
- `main`, `develop` 모두 **직접 push 금지**
- 브랜치: `{type}/{설명}` — **영어 소문자 + 하이픈** (`feat`, `fix`, `docs`, `chore`, `style`, `refactor`, `test`, `perf`, `ci`)
- 커밋: `type: 한국어 설명` (Conventional Commits)

### GitHub 작업 플로우 (이슈 → PR)

새 작업은 아래 순서를 **사용자 확인 없이 연속으로** 진행한다 (`gh` CLI, 이미 인증돼 있음).
이슈 등록부터 PR 생성까지는 이 문서로 사전 승인된 자동화 범위다.

#### 1. 이슈 등록 (`gh issue create --repo heartbeat-kb-town/fitwallet-backend`)

제목은 `[TYPE] 한국어 설명`. **타입 라벨은 제목 접두사와 1:1로 반드시 매칭**한다.

| 접두사 | 템플릿 | 라벨 | 본문 섹션 |
|---|---|---|---|
| `[BUG]` | `bug_report.md` | `🐛 버그` | 버그 설명 / 재현 방법 / 예상 동작 / 실제 동작 / 스크린샷 / 환경 / 추가 정보 |
| `[FEAT]` | `feature_request.md` | `✨ 기능` | 기능 요약 / 배경 및 이유 / 구현 방법 나열 / 추가 정보 |
| `[TASK]` | `task.md` | `🛠️ 작업` | 작업 내용 / 작업 목표 / 세부 작업 목록(체크박스) / 참고 자료 / 완료 조건 |
| `[REFACTOR]` | `refactor.md` | `🧹 리팩터링` | 리팩토링 대상 / 현재 문제점 / 개선 방향 / 예상 영향 범위 / 주의사항 |
| `[DOCS]` | `docs.md` | `📝 문서` | 문서 작업 내용 / 작업 이유 / 작업 범위(체크박스) / 참고 자료 |
| `[QUESTION]` | `question.md` | (없음) | 질문 내용 / 배경 / 시도해본 것 / 참고 자료 |
| `[LEARN]` | `learn.md` | (없음) | 무엇을 했나 / 왜 이렇게 했나 / 어떻게 동작하나 / 몰랐다가 알게 된 것 / 참고 사항 |

내용상 해당되면 아래 라벨을 **추가로** 붙인다:
- 우선순위 `🔼 높음` / `➖ 보통` / `🔽 낮음`
- 상태 `🧊 대기` / `👀 검토필요` / `🚧 진행중`
- 영역 `🌐 API` / `🗄️ DB` / `🔌 외부연동` / `🔐 인증` / `🔒 보안` / `✅ 테스트` / `🧭 도메인` / `🧰 인프라`
- 긴급하면 `🔥 긴급`

관련 마일스톤이 있으면 `--milestone`으로 연결한다.

#### 2. 브랜치 생성

```bash
git checkout develop && git pull origin develop
git checkout -b {type}/{설명}
```

#### 3. 구현 + 검증

`./gradlew build`로 컴파일과 테스트 통과를 확인한다. 위 컨벤션(1~12)을 지킨다.

#### 4. 커밋

`type: 한국어 설명` 형식. 이 작업과 무관한 미추적 파일은 같이 add하지 않는다.

#### 5. push

```bash
git push -u origin {브랜치명}
```

#### 6. PR 생성 (`gh pr create --repo heartbeat-kb-town/fitwallet-backend --base develop`)

- 제목: `[#이슈번호] type: 작업 내용`
- 본문: 관련 이슈(`closes #N`) / 작업 내용 / 변경 유형(체크박스) / 체크리스트 / 리뷰어에게 전달할 내용
- 이슈가 마일스톤에 연결돼 있으면 PR도 같은 마일스톤에 연결한다

이슈가 여러 개로 쪼개지는 큰 작업은 먼저 상위 이슈나 마일스톤으로 묶고, 하위 작업 단위로 이 플로우를 반복한다.

#### 릴리스 플로우 (develop → main)

배포 시점에 **사용자가 명시적으로 요청할 때만** 실행한다.

```bash
gh pr create --repo heartbeat-kb-town/fitwallet-backend \
  --base main --head develop --title "release: {날짜 또는 버전} 배포"
```

- 이슈를 만들지 않고 제목에 이슈 번호도 붙이지 않는다
- 본문에 이번 배포에 포함된 PR 목록을 적는다 (`git log main..develop --oneline`)
- **Merge commit**으로 머지한다 (Squash 금지 — 히스토리가 갈라져 다음 릴리스에서 충돌한다)

#### 자동 배포 (CD)

**이 PR이 머지되면 운영 환경이 갱신된다.** `.github/workflows/ci.yml`의 `deploy` 잡이
main push에서 실행돼, `build` 잡이 만든 WAR를 그대로 Elastic Beanstalk
`fitwallet-backend` / `fitwallet-prod`(`ap-northeast-2`)에 올린다. 배포 시점에 다시
빌드하지 않으므로 테스트를 통과한 바이너리가 그대로 올라간다.

인증은 GitHub OIDC로 IAM 역할 `fitwallet-github-actions-deploy`를 수임한다 — 저장소에
AWS 액세스 키를 두지 않는다. 역할 ARN만 저장소 Variable `AWS_ROLE_ARN`에 있다.

- 버전 라벨은 `gh-{run_number}.{run_attempt}-{sha 앞 7자}`.
  **`run_attempt`를 빼면 안 된다** — `create-application-version` 이후 단계에서 실패하면
  그 라벨이 EB에 남아, 재실행 때 "이미 존재함"으로 거부돼 사람이 버전을 지우기 전까지
  재배포가 막힌다
- 배포 후 EB 상태와 `GET /health/db`를 확인하고, 실패하면 EB 이벤트를 로그에 찍는다
- **환경 속성은 워크플로가 건드리지 않는다.** `JWT_SECRET`·DB 접속정보·`env=prod`는
  EB에만 있고 저장소에 없다 (§13)
- 비용 절감으로 환경을 내려둔 상태면 `Check environment is Ready`에서 실패한다.
  환경을 먼저 띄우고 Actions에서 workflow_dispatch로 재실행한다
- 릴리스와 무관하게 배선만 확인하거나 재배포하려면 Actions 탭에서 main을 골라
  workflow_dispatch로 실행한다

#### 배포 IAM 설정에서 실제로 겪은 함정 (2026-08-05 첫 자동 배포)

**EB 배포용 최소 권한을 손으로 깎지 않는다.** `UpdateEnvironment`는 EB가 CloudFormation
스택을 조작하는 작업이라 여러 서비스의 권한을 연쇄로 요구한다. 최소 권한에서 출발해
거부될 때마다 하나씩 추가하는 방식은 수렴 지점이 없고, 매 시도가 운영 배포를 한 번씩 태운다.
실제로 네 번 실패했다 — 신뢰 정책 `sub` 불일치 → `s3:GetObject` → `s3:CreateBucket` 등
버킷 레벨 → `cloudformation:GetTemplate`. 지금은 관리형 정책
`AdministratorAccess-AWSElasticBeanstalk`를 쓴다.

> **IAM 정책 시뮬레이터는 내가 나열한 액션만 평가한다.** 누락된 액션은 찾아주지 못하므로
> "시뮬레이터 통과 = 배포 가능"이 아니다.

**이 조직은 OIDC immutable subject claim을 쓴다.** 토큰의 `sub`가 표준형이 아니라
`repo:{org}@{orgId}/{repo}@{repoId}:ref:refs/heads/main` 형태로 온다. 표준형만 신뢰 정책에
넣으면 `Not authorized to perform sts:AssumeRoleWithWebIdentity`로 막힌다.

> GitHub의 `GET /repos/{owner}/{repo}/actions/oidc/customization/sub`는
> `use_immutable_subject: false`로 응답하는데 실제 토큰은 ID형이다 — **이 API를 믿지 말 것.**
> 같은 응답의 `sub_claim_prefix`가 실제로 쓰이는 값이다.

`sub` 실측값은 추측하지 말고 CloudTrail에서 확인한다. 실패한 호출의 `sub`가
`userIdentity.userName`에 그대로 남는다:

```bash
aws cloudtrail lookup-events --region ap-northeast-2 \
  --lookup-attributes AttributeKey=EventName,AttributeValue=AssumeRoleWithWebIdentity
```

**GitHub Environment를 도입하면 `sub`의 `:ref:refs/heads/main` 부분이 `:environment:{이름}`으로
바뀌어 수임이 깨진다.** 도입할 거면 신뢰 정책을 함께 고쳐야 한다.

롤백은 자동이 아니다. 이전 애플리케이션 버전으로 되돌린다:

```bash
aws elasticbeanstalk describe-application-versions \
  --application-name fitwallet-backend --query 'ApplicationVersions[].VersionLabel'
aws elasticbeanstalk update-environment \
  --environment-name fitwallet-prod --version-label {이전 라벨}
```

## 라벨 전체 목록

| 라벨 | 설명 |
|---|---|
| `✨ 기능` | 새 기능 또는 기존 기능 개선 |
| `🐛 버그` | 재현 가능한 오류 또는 예상과 다른 동작 |
| `🛠️ 작업` | 구현, 설정, 정리처럼 실행할 작업 |
| `📝 문서` | README, API 문서, 가이드, 주석 개선 |
| `🧹 리팩터링` | 동작 변경 없는 구조와 품질 개선 |
| `✅ 테스트` | 테스트 추가, 수정, 검증 작업 |
| `🔒 보안` | 취약점, 민감정보, 권한 오남용 방지 |
| `🧰 인프라` | CI/CD, 빌드, 설정, 의존성, 개발환경 |
| `🌐 API` | 컨트롤러, 요청과 응답, API 스펙 |
| `🗄️ DB` | 데이터베이스, 마이그레이션, 쿼리, 모델 |
| `🔌 외부연동` | 외부 API, SDK, 웹훅, 연동 오류 |
| `🔐 인증` | 로그인, 토큰, 인가, 세션 |
| `🔥 긴급` | 즉시 처리해야 하는 장애, 보안, 차단 이슈 |
| `🧭 도메인` | 핵심 비즈니스 로직, 정책, 유스케이스 |
| `➖ 보통` / `🔼 높음` / `🔽 낮음` | 우선순위 |
| `🧊 대기` / `👀 검토필요` / `🚧 진행중` | 진행 상태 |
