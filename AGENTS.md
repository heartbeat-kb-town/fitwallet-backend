# fitwallet-backend

최적의 결제수단을 추천하고 놓친 혜택을 알려주는 스마트 전자지갑 fitwallet의 백엔드 서버.

이 문서가 **코드 컨벤션과 작업 플로우의 정본**이다. `CLAUDE.md`는 이 파일을 가져다 쓴다.
사람이 읽는 기여 가이드는 [CONTRIBUTING.md](./CONTRIBUTING.md), 스키마 설명은 [docs/erd.md](./docs/erd.md)에 있다.

## 빌드 · 실행

```bash
docker compose up -d      # 로컬 MySQL (스키마 + 시드 자동 적용)
./gradlew build           # 컴파일 + 테스트 + war 패키징
./gradlew appRun          # http://localhost:8080 (Gretty)
./gradlew test            # 테스트만
```

테스트에는 로컬 MySQL이 떠 있어야 한다 (Mapper 통합 테스트가 실제 DB를 쓴다).

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
| 테스트 | JUnit 5, spring-test, AssertJ 3.26.3, Mockito 5.12.0 |

> ⚠️ `javax` 기반이므로 **jakarta 계열 라이브러리를 넣으면 안 된다.**
> Hibernate Validator는 반드시 6.x를 쓴다 (7/8은 jakarta).

설정은 전부 XML이다 — `src/main/resources/root-context.xml`(루트 컨텍스트),
`src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml`(웹 컨텍스트), `web.xml`.

인증(BCrypt, JWT) 라이브러리는 아직 정하지 않았다. 인증 구현을 시작할 때 결정한다.

---

## 2. 패키지 / 도메인 구조

도메인은 6개다: `user`, `card`, `store`, `benefit`, `payment`, `report`.
`auth`는 별도 도메인으로 두지 않는다 (인증 컬럼이 `users` 테이블에 있어 `user` 도메인이 담당한다).

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
│  │  ├─ request/
│  │  └─ response/              # enum도 dto/ 바로 아래에 선언
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

**Service는 인터페이스 + 구현체 한 쌍으로 둔다.** 구현체는 접미사 `Impl`이 아니라
**접두사 `Default`**를 쓴다 — `CardServiceImpl`이 아니라 `DefaultCardService`.
컨트롤러는 인터페이스 타입(`CardService`)만 의존하고 구현체를 직접 참조하지 않는다.

---

## 4. DTO 규칙

`record`를 쓰지 않는다. Request·Response 모두 **class + Lombok**으로 통일한다.

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

---

## 5. 응답 포맷 / 예외 처리

**경로와 응답 필드는 [API 명세](https://app.notion.com/p/3a6a561881a480d0b24afb20e24190ef)를 따른다.**
새 엔드포인트를 만들기 전에 명세에서 해당 행을 먼저 확인한다.

> ⚠️ **봉투 형태만은 예외다.** 명세에 두 가지 형태가 섞여 있다 —
> `{success, code, message, data}`(로그인·보유 카드 목록)와
> `{status, success, message, data}`(놓친 혜택·주변 가맹점).
> **전자로 통일하기로 했다.** `status`는 HTTP 상태코드와 중복이고, `code`가 없으면
> 프론트가 에러를 구분하려고 `message` 문자열을 비교해야 해서 문구만 다듬어도 깨진다.
> 명세에서 후자로 적힌 페이지를 보면 그대로 따르지 말고 아래 형태로 구현한다.

`/api` 아래 모든 응답은 `ApiResponse<T>` 봉투에 담긴다. HTTP 상태코드도 의미대로 쓴다.

```json
// 성공
{ "success": true, "code": "USER_CARDS_FOUND",
  "message": "보유 카드 목록을 조회했습니다.", "data": [ ... ] }

// 실패 — data는 null로 유지된다
{ "success": false, "code": "CARD_NOT_FOUND",
  "message": "카드를 찾을 수 없습니다.", "data": null }

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

```java
// 컨트롤러 — 성공 응답
return ApiResponse.of(CardSuccessCode.USER_CARDS_FOUND, cardService.findMyCards(userId));

// 서비스 — 실패
throw new BusinessException(CardErrorCode.CARD_NOT_FOUND);
```

---

## 6. MyBatis 매퍼 규칙

**커스텀 TypeHandler를 만들지 않는다.** DDL의 CHECK 제약 값 7종
(`card_type`, `benefit_type`, `value_type`, `scope_type`, `limit_basis`, `limit_period`, `payment_session.status`)이
모두 자바 enum 상수 이름 규칙과 일치해 기본 `EnumTypeHandler`가 `name()` 기준으로 자동 변환한다.
`TINYINT(1)`↔`Boolean`, `DECIMAL`↔`BigDecimal`, `DATETIME`↔`LocalDateTime`도 기본 핸들러가 처리한다.
정말 필요해지면 `global/config/typehandler/`에 만들고 `mybatis-config.xml`에 등록한다.

- 전역 설정은 `src/main/resources/mybatis-config.xml` — `mapUnderscoreToCamelCase`, `jdbcTypeForNull`
- **`AS` 별칭을 수동으로 붙이지 않는다.** `mapUnderscoreToCamelCase`가 처리한다
- `<select>`의 `id`는 인터페이스 메서드명과 같아야 한다
- 단순 조회는 `resultType` 자동 매핑, **1:N 중첩만 `resultMap` + `<collection>`**
- **`${}` 금지, `#{}` 필수.** 동적 정렬 컬럼처럼 불가피하면 화이트리스트로 검증한 뒤 쓴다
- **`SELECT *` 금지** — 컬럼을 명시한다. 반복되면 `<sql>` + `<include>`로 뺀다

### 페이징

명세("주변 가맹점 조회")가 정한 형태를 쓴다. 공통 래퍼 클래스를 만들지 않는다 —
페이징 필드가 별도 객체가 아니라 `data` 바로 아래에 목록과 나란히 놓이기 때문이다.

```json
"data": {
  "page": 0, "size": 20, "totalElements": 12, "hasNext": false,
  "stores": [ ... ]
}
```

- 응답 DTO에 `page` / `size` / `totalElements` / `hasNext` 네 필드를 직접 넣는다
- 요청 조건은 `{대상}SearchCondition`에 담고 XML에서 `LIMIT #{cond.size} OFFSET #{cond.offset}`
- 총 개수는 `count*` 메서드로 따로 조회한다

---

## 7. 인증 컨텍스트 전달

컨트롤러가 사용자 식별자를 얻는 경로는 하나뿐이다.

```java
@GetMapping("/api/cards")
public List<CardListResponse> findMyCards(@LoginUserId Long userId) {
    return cardService.findMyCards(userId);
}
```

- **`@RequestParam userId`, 헤더 직접 읽기, 세션 접근은 모두 금지**
- `AuthInterceptor`가 `/api/**`에서 인증 주체를 꺼내 요청 속성에 담고,
  `LoginUserIdArgumentResolver`가 `@LoginUserId` 파라미터에 주입한다
- **`AuthInterceptor`는 현재 임시 구현이다** — 인증이 없어 `X-User-Id` 헤더를 그대로 신뢰한다.
  JWT 도입 시 이 클래스 내부만 교체하고 컨트롤러는 손대지 않는다

---

## 8. 트랜잭션 경계

- `@Transactional`은 **Service에만** 붙인다
- 조회 메서드는 **`@Transactional(readOnly = true)`**
- **Controller와 Mapper에는 붙이지 않는다** (아래 함정 참고)
- **`@Transactional`은 `Default{도메인}Service` 구현체 메서드에 붙인다. 인터페이스 선언에는 붙이지 않는다.**
  지금은 `<tx:annotation-driven>`이 JDK 동적 프록시라 인터페이스에 붙여도 인식되긴 하지만,
  나중에 `proxy-target-class="true"`(CGLIB)로 바뀌면 인터페이스의 애너테이션은 조용히 무시된다.
  구현체에만 붙이는 습관을 들이면 이 전환에 영향받지 않는다.

---

## 9. 스키마 유래 공통 규칙

- **소프트 삭제**: `is_deleted` 컬럼이 있는 테이블은 조회 시 **항상 `is_deleted = 0`** 조건을 건다.
  물리 `DELETE`를 쓰지 않는다 (`payment_transaction`이 `user_card`를 FK 참조한다)
- **금액**: DDL이 전부 `DECIMAL`이므로 자바에서 **`BigDecimal` 필수**. `double`/`float` **금지**
- **감사 컬럼**: `created_at`/`updated_at`는 DB DEFAULT가 채운다 → **INSERT/UPDATE 문에 쓰지 않는다**
- **날짜/시간**: `DATE`→`LocalDate`, `DATETIME`→`LocalDateTime`. 타임존 `Asia/Seoul`, JSON은 ISO-8601
- **불리언**: `TINYINT(1) is_*` 컬럼은 `Boolean`/`boolean`으로 받는다

---

## 10. 테스트 규칙

| 계층 | 도구 | DB | 의무 |
|---|---|---|---|
| Service 단위 | `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks` | 불필요 | **필수** |
| Mapper 통합 | `@SpringJUnitConfig(locations = "classpath:root-context.xml")` + `@Transactional` | 실 MySQL | **필수** |
| Controller | MockMvc `standaloneSetup` + Mock Service | 불필요 | 선택 |

- 테스트 DB는 **docker compose MySQL과 시드 데이터를 그대로 쓴다.** 별도 스키마나 Testcontainers를 두지 않는다
- 격리는 클래스 레벨 `@Transactional` 자동 롤백으로 처리한다. 데이터를 바꾸는 테스트를 써도 된다
- 단언은 **AssertJ `assertThat`으로 통일**한다. JUnit `Assertions.*`를 쓰지 않는다
- 시드 데모 페르소나는 `user_id = 1` (카드 5건, 거래 355건)

> ⚠️ **한 테스트 안에서 "조회 → 변경 → 다시 같은 조회"를 하지 않는다.**
> 같은 트랜잭션은 같은 SqlSession이라 MyBatis 1차 캐시가 첫 결과를 그대로 돌려준다.
> 특히 `JdbcTemplate`으로 바꾼 데이터는 MyBatis가 알 수 없어 캐시가 비워지지 않는다.
> 변경 전 상태와 변경 후 상태는 **테스트를 나눠서** 검증한다.
> (운영 코드에서도 한 트랜잭션 안에서 같은 조회를 반복하면 같은 값이 온다는 뜻이다)

---

## 11. 환경 / 프로파일

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

## 12. XML 설정 함정 (읽지 않으면 반드시 걸린다)

루트 컨텍스트(`root-context.xml`)와 웹 컨텍스트(`servlet-context.xml`)가 분리돼 있어서
빈이 어느 쪽에 등록되는지에 따라 조용히 동작하지 않는 경우가 있다.

1. **`@ControllerAdvice`는 `@Controller`가 아니다.**
   `servlet-context.xml`의 component-scan이 `use-default-filters="false"`라
   include 필터에 `@ControllerAdvice`가 없으면 `GlobalExceptionHandler`가 등록되지 않는다.
   등록에 실패해도 예외가 나지 않고 **모든 오류가 500으로 떨어진다.**

2. **Controller의 `@Transactional`은 무시된다.**
   `<tx:annotation-driven>`은 루트 컨텍스트에 있고 Controller는 웹 컨텍스트에서 스캔되므로
   프록시가 걸리지 않는다. 예외도 나지 않는다. 그래서 트랜잭션은 Service에만 건다.

3. **웹 계층 빈을 양쪽에서 스캔하지 않는다.**
   `root-context.xml`은 `@Controller`와 `@ControllerAdvice`를 exclude하고,
   `servlet-context.xml`이 그 둘만 include한다. 새 스테레오타입을 추가할 때 양쪽을 함께 확인한다.

---

## Git 컨벤션

원본 규칙은 [CONTRIBUTING.md](./CONTRIBUTING.md)에 있다.

- `main`은 **배포 브랜치**다. `develop` → `main` 릴리스 PR로만 갱신하며 병합은 **Merge commit**이다
- `develop`은 **통합 브랜치이자 기본 브랜치**다. 모든 작업 브랜치는 `develop`에서 분기하고
  **PR의 base는 항상 `develop`**이며 병합은 **Squash and Merge**다
- `main`, `develop` 모두 **직접 push 금지**
- 브랜치: `{type}/{설명}` — **영어 소문자 + 하이픈** (`feat`, `fix`, `docs`, `chore`, `style`, `refactor`, `test`, `perf`, `ci`)
- 커밋: `type: 한국어 설명` (Conventional Commits)

## GitHub 작업 플로우 (이슈 → PR)

새 작업은 아래 순서를 **사용자 확인 없이 연속으로** 진행한다 (`gh` CLI, 이미 인증돼 있음).
이슈 등록부터 PR 생성까지는 이 문서로 사전 승인된 자동화 범위다.

### 1. 이슈 등록 (`gh issue create --repo heartbeat-kb-town/fitwallet-backend`)

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

### 2. 브랜치 생성

```bash
git checkout develop && git pull origin develop
git checkout -b {type}/{설명}
```

### 3. 구현 + 검증

`./gradlew build`로 컴파일과 테스트 통과를 확인한다. 위 컨벤션(1~12)을 지킨다.

### 4. 커밋

`type: 한국어 설명` 형식. 이 작업과 무관한 미추적 파일은 같이 add하지 않는다.

### 5. push

```bash
git push -u origin {브랜치명}
```

### 6. PR 생성 (`gh pr create --repo heartbeat-kb-town/fitwallet-backend --base develop`)

- 제목: `[#이슈번호] type: 작업 내용`
- 본문: 관련 이슈(`closes #N`) / 작업 내용 / 변경 유형(체크박스) / 체크리스트 / 리뷰어에게 전달할 내용
- 이슈가 마일스톤에 연결돼 있으면 PR도 같은 마일스톤에 연결한다

이슈가 여러 개로 쪼개지는 큰 작업은 먼저 상위 이슈나 마일스톤으로 묶고, 하위 작업 단위로 이 플로우를 반복한다.

### 릴리스 플로우 (develop → main)

배포 시점에 **사용자가 명시적으로 요청할 때만** 실행한다.

```bash
gh pr create --repo heartbeat-kb-town/fitwallet-backend \
  --base main --head develop --title "release: {날짜 또는 버전} 배포"
```

- 이슈를 만들지 않고 제목에 이슈 번호도 붙이지 않는다
- 본문에 이번 배포에 포함된 PR 목록을 적는다 (`git log main..develop --oneline`)
- **Merge commit**으로 머지한다 (Squash 금지 — 히스토리가 갈라져 다음 릴리스에서 충돌한다)

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
