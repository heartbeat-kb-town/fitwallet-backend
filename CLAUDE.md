# CLAUDE.md

이 저장소의 코드 컨벤션과 작업 플로우 정본은 [AGENTS.md](./AGENTS.md)다.
아래 import로 전부 가져오므로, **규칙을 고칠 때는 `AGENTS.md`만 고친다.**
(Codex는 `AGENTS.md`를 직접 읽고 import를 따라가지 않으므로, 정본이 그쪽에 있어야 한다.)

@AGENTS.md

---

## Claude Code 전용

- `AGENTS.md`의 "GitHub 작업 플로우"는 **사용자 확인 없이 연속 실행해도 되는 사전 승인 범위**다.
  이슈 등록 → 브랜치 → 구현 → 커밋 → push → PR 생성까지 멈추지 않고 진행한다.
  분기점과 PR base가 작업 성격에 따라 갈리니(성능은 `main`, 나머지는 `develop`) 시작 전에 확인한다.
  `develop` → `main` 릴리스 플로우는 폐기됐다 — **두 브랜치를 병합하는 PR을 만들지 않는다.**
- 커밋 메시지 끝에 트레일러를 붙인다:

  ```
  Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
  ```

- 로컬 MySQL 포트는 개발자마다 다를 수 있다 (`.env`의 `MYSQL_PORT`).
  접속이 안 되면 `docker compose ps`로 실제 포트를 먼저 확인한다.
