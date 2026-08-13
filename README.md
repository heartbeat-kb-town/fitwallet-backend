# fitwallet-backend

최적의 결제수단을 추천하고 놓친 혜택을 알려주는 스마트 전자지갑 fitwallet의 백엔드 서버

## 로컬 실행 방법

> Windows 사용자는 [Git Bash](https://git-scm.com/downloads)를 사용하면 아래
> macOS/Linux 명령어를 그대로 쓸 수 있습니다. cmd나 PowerShell을 직접 쓰고 싶다면
> 명령이 갈리는 단계마다 있는 표를 참고하세요.

### 1. Docker Desktop 설치 확인

아직 Docker를 설치하지 않았다면 먼저 설치합니다.

1. https://www.docker.com/products/docker-desktop/ 에서 OS에 맞는 설치 파일을
   다운로드해 설치합니다.
2. 설치 후 Docker Desktop 앱을 실행합니다. (macOS는 메뉴바에 고래 아이콘이 뜨고,
   Windows는 시스템 트레이에 아이콘이 뜨면 준비된 것입니다)
3. 터미널에서 아래 두 명령이 버전 정보를 출력하면 준비 완료입니다.
   ```bash
   docker --version
   docker compose version
   ```

### 2. 저장소 clone

```bash
git clone https://github.com/heartbeat-kb-town/fitwallet-backend.git
cd fitwallet-backend
```

### 3. 환경 변수 파일 준비

`.env.sample`을 복사해서 `.env`를 만듭니다. (`.gitignore` 대상이라 커밋되지 않습니다)

| macOS / Linux / Git Bash | Windows (cmd) | Windows (PowerShell) |
|---|---|---|
| `cp .env.sample .env` | `copy .env.sample .env` | `Copy-Item .env.sample .env` |

DB 접속 정보는 따로 만들 필요가 없습니다. `src/main/resources/config/application-local.properties`가
저장소에 들어 있고, 그 값이 `.env`와 짝이 맞습니다.

> 3306 포트가 이미 쓰이고 있어 `.env`의 `MYSQL_PORT`를 바꿔야 한다면 `.env`만 고치면 됩니다.
> 빌드 스크립트가 `.env`를 읽어 애플리케이션 설정에 그대로 넘겨주므로 두 곳을 맞출 필요가 없습니다.

### 4. Docker로 로컬 MySQL 기동

OS 상관없이 동일합니다 (Docker Desktop이 실행 중이어야 합니다):

```bash
docker compose up -d
docker compose ps   # mysql 서비스가 healthy 상태인지 확인
```

이 단계에서는 **빈 데이터베이스만** 만들어집니다. 스키마(18개 테이블)와 예시 데이터
(카드사 3사·혜택·가맹점·회원·결제내역)는 다음 단계에서 **앱이나 테스트를 처음 돌릴 때
Flyway가** 넣습니다 (`src/main/resources/db/`). 테이블 구조는 [docs/erd.md](./docs/erd.md)를
참고하세요.

> 스키마를 바꾸려면 `src/main/resources/db/migration/`에 `V{다음번호}__{설명}.sql`을
> 추가하면 됩니다. 다음 기동 때 자동으로 적용되므로 볼륨을 지울 필요가 없고, 운영 서버도
> 배포와 함께 같은 파일이 적용됩니다.

> ⚠️ **이미 컨테이너를 띄워 둔 상태에서 이 저장소를 pull 했다면 볼륨까지 지우고 재생성하세요.**
> 예전 방식(`docker/mysql/init/`)으로 이미 데이터가 들어간 볼륨에 그대로 붙으면 데모 데이터가
> 중복 적재돼 앱 기동이 실패합니다. MySQL 서버 타임존(`--default-time-zone=+09:00`) 변경도
> 기존 컨테이너에는 반영되지 않으므로 어차피 한 번은 재생성해야 합니다.
>
> ```bash
> docker compose down -v && docker compose up -d
> docker exec fitwallet-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" \
>   -e "SELECT @@global.time_zone, NOW();"   # +09:00 / 현재 KST 시각
> ```

### 5. 빌드 & 서버 기동

| | macOS / Linux / Git Bash | Windows (cmd) | Windows (PowerShell) |
|---|---|---|---|
| 빌드 | `./gradlew build` | `gradlew.bat build` | `.\gradlew.bat build` |
| 서버 기동 | `./gradlew appRun` | `gradlew.bat appRun` | `.\gradlew.bat appRun` |

Gretty가 내장 Tomcat으로 `http://localhost:8080` 에 서버를 띄웁니다.

### 6. 정상 기동 확인 (브라우저)

브라우저로 `http://localhost:8080/` 을 열어보세요. 아래 두 가지가 함께 보이면
로컬 환경 구축이 끝난 것입니다.

- DB 연결 상태 메시지 (`health_check` 테이블에서 읽어온 내용)
- 환경 구축 완료를 알리는 이미지

DB 컨테이너가 아직 안 떠 있어도 페이지 자체는 뜨고, 대신 "DB 연결 안 됨" 메시지가
표시됩니다. (참고: `GET /health/db`는 같은 정보를 JSON으로 주는 API라 `curl`이나
모니터링 용도로도 쓸 수 있습니다)

### 7. 컨테이너 정리 (선택)

```bash
docker compose down       # 컨테이너만 정리 (데이터는 유지됨)
docker compose down -v    # 데이터까지 완전히 초기화하고 싶을 때
```

## 배포 (운영 서버)

프로파일은 시스템 프로퍼티 `-Denv`로 고릅니다. 지정하지 않으면 `local`입니다.

운영에서는 `-Denv=prod`로 띄우고, DB 접속 정보는 **환경 변수로만** 주입합니다.
`application-prod.properties`에는 값이 아니라 환경 변수 참조만 들어 있어서,
아래 세 변수가 없으면 기동 단계에서 실패합니다. (운영 비밀번호가 저장소에 남지 않도록 한 의도된 동작입니다)

```bash
export DB_URL='jdbc:mysql://{호스트}:3306/fitwallet?serverTimezone=Asia/Seoul&characterEncoding=UTF-8'
export DB_USERNAME='{계정}'
export DB_PASSWORD='{비밀번호}'
```

WAR는 `./gradlew build`로 `build/libs/`에 생성됩니다. Tomcat에 배포할 때는
`CATALINA_OPTS`에 `-Denv=prod`를 넣습니다.

## 코드 컨벤션

패키지 구조, 네이밍, DTO·예외·MyBatis·테스트 규칙은 [AGENTS.md](./AGENTS.md)에 정리돼 있습니다.
새 도메인을 시작할 때는 참조 구현인 `src/main/java/com/fitwallet/domain/card`를 복제하세요.

## 기여 가이드

브랜치는 두 개를 축으로 씁니다.

- `main` — 배포 브랜치. 배포된 것만 담기며, `develop`에서 올라오는 릴리스 PR로만 갱신됩니다.
- `develop` — 통합 브랜치이자 기본 브랜치. 모든 작업 브랜치는 여기서 분기하고 여기로 PR합니다.

브랜치 전략 상세, 커밋 컨벤션, PR 규칙, 코드 리뷰 규칙은 [CONTRIBUTING.md](./CONTRIBUTING.md)를 참고하세요.
