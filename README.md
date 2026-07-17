# fitwallet-backend

최적의 결제수단을 추천하고 놓친 혜택을 알려주는 스마트 전자지갑 fitwallet의 백엔드 서버

## 기술 스택

- Java 17
- Spring Framework 5.3.x (Spring Legacy, XML 기반 설정)
- MyBatis
- Gradle
- Tomcat (로컬 구동: Gretty 플러그인)
- MySQL (예정 — DB 연결은 추후 Docker로 통일 예정)
- Lombok
- Logback

## 디렉터리 구조

```
src/main/java/com/fitwallet/
├── controller/        # API 진입점
├── service/            # 비즈니스 로직
├── domain/             # 도메인 객체
├── mapper/             # MyBatis 매퍼 인터페이스
├── config/             # 설정 관련 클래스
└── global/exception/   # 공통 예외

src/main/resources/
├── db.properties.sample   # DB 접속 정보 샘플 (실제 db.properties는 gitignore)
├── logback.xml
└── mapper/                 # MyBatis XML 매퍼

src/main/webapp/WEB-INF/
├── web.xml
└── spring/
    ├── root-context.xml            # DataSource, MyBatis, 트랜잭션 설정
    └── appServlet/servlet-context.xml   # DispatcherServlet(MVC) 설정
```

## 로컬 실행 방법

1. 저장소를 clone 합니다.
2. DB 접속 정보 파일을 준비합니다. (아직 값이 없어도 서버 기동에는 문제 없습니다 —
   MyBatis 쿼리를 실제로 호출할 때만 DB 연결이 필요합니다. DB 환경은 추후 Docker로
   통일할 예정입니다.)
   ```bash
   cp src/main/resources/db.properties.sample src/main/resources/db.properties
   ```
3. 빌드합니다.
   ```bash
   ./gradlew build
   ```
4. 서버를 기동합니다. (Gretty가 내장 Tomcat으로 `http://localhost:8080` 에 띄웁니다)
   ```bash
   ./gradlew appRun
   ```
5. 정상 기동 여부를 확인합니다.
   ```bash
   curl http://localhost:8080/
   # fitwallet-backend is running
   ```

## 기여 가이드

브랜치 전략, 커밋 컨벤션, PR 규칙, 코드 리뷰 규칙은 [CONTRIBUTING.md](./CONTRIBUTING.md)를 참고하세요.
