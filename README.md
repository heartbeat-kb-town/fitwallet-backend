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

### 3. 환경 변수 / DB 접속 정보 파일 준비

`.sample` 파일을 복사해서 실제 설정 파일을 만듭니다. (두 파일 모두 `.gitignore`
대상이라 커밋되지 않습니다)

| | macOS / Linux / Git Bash | Windows (cmd) | Windows (PowerShell) |
|---|---|---|---|
| `.env` | `cp .env.sample .env` | `copy .env.sample .env` | `Copy-Item .env.sample .env` |
| `db.properties` | `cp src/main/resources/db.properties.sample src/main/resources/db.properties` | `copy src\main\resources\db.properties.sample src\main\resources\db.properties` | `Copy-Item src\main\resources\db.properties.sample src\main\resources\db.properties` |

### 4. Docker로 로컬 MySQL 기동

OS 상관없이 동일합니다 (Docker Desktop이 실행 중이어야 합니다):

```bash
docker compose up -d
docker compose ps   # mysql 서비스가 healthy 상태인지 확인
```

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

## 기여 가이드

브랜치 전략, 커밋 컨벤션, PR 규칙, 코드 리뷰 규칙은 [CONTRIBUTING.md](./CONTRIBUTING.md)를 참고하세요.
