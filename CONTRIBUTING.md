# Contributing Guide

## 이슈 작성 규칙

- 버그 리포트, 기능 요청 등 각 템플릿을 사용합니다
- 이슈 제목은 명확하게 작성합니다
- 담당자(Assignee)와 라벨을 반드시 지정합니다

## 브랜치 전략

**`main`과 `develop`은 목적이 다른 두 갈래이고, 서로 병합하지 않습니다.**
둘 다 직접 push는 금지이며, 모든 변경은 Pull Request를 통해 반영합니다.

| 브랜치 | 역할 | 어떻게 갱신되나 | 병합 방식 |
|------|------|----------------|----------|
| `main` | **성능 최적화 + 배포.** CD가 여기서 운영에 올립니다 | `perf/*` 작업 브랜치 → `main` PR | **Squash and Merge** |
| `develop` | **추가 기능 개발.** 로컬 시연용입니다 | `feat/*` 등 작업 브랜치 → `develop` PR | **Squash and Merge** |
| 작업 브랜치 | 이슈 하나 단위의 작업 | **분기점은 작업 성격에 따라 갈립니다(아래)** | (머지 후 삭제) |

| 작업 | 분기 | PR base | 예 |
|------|------|---------|-----|
| **성능 측정 · 성능 개선** | **`main`** | **`main`** | `perf/store-search-bbox` |
| 기능 개발 · 버그 수정 · 문서 등 | `develop` | `develop` | `feat/login-page` |

```
perf/store-search-bbox ─┐
perf/k6-baseline ───────┼─▶ main ─(CD)─▶ 운영
                        ┘

feat/login-page ─┐
fix/calc-error ──┼─▶ develop ─▶ 로컬 시연
docs/update ─────┘
```

### 두 갈래가 갈라진 채로 가는 이유

성능 개선의 산출물은 **개선 전/후 수치**입니다. 그 수치는 실제로 배포되어 돌고 있는 코드를
기준으로 재야 의미가 있습니다. 아직 배포되지 않은 기능이 측정에 섞여 들어오면
"무엇을 고쳐서 빨라졌는지"를 가를 수 없게 됩니다. 그래서 성능 작업은 `main`에서만 갈라지고,
기능 개발은 `develop`에 쌓인 채 로컬에서 시연합니다.

> [!IMPORTANT]
> **되돌려 머지(back-merge)하지 않습니다.** 두 브랜치는 앞으로 계속 갈라진 상태로 갑니다.
> 예전의 `develop` → `main` 릴리스 PR도 더 이상 만들지 않습니다.
>
> 그 대가로 알고 있어야 할 것 세 가지입니다.
>
> 1. **`develop`의 기능은 운영에 배포되지 않습니다.** 의도된 동작입니다 — 시연은 로컬에서 합니다
> 2. **`main`에 머지하면 그 즉시 운영에 배포됩니다.** 성능 PR도 예외가 아닙니다
>    (`.github/workflows/ci.yml`의 `deploy` 잡이 main push에서 돕니다)
> 3. **양쪽 모두에 필요한 변경은 각 브랜치에 따로 반영해야 합니다.** `AGENTS.md`,
>    `CONTRIBUTING.md`, `scripts/` 같은 공통 파일이 여기 해당합니다.
>    실제로 PR #236(`load.sh --reset`의 고아 FK 수정)이 `main`에만 있어
>    `develop`의 `load.sh`는 아직 고아 행을 남깁니다

### 브랜치 네이밍 규칙

| 타입 | 패턴 | 예시 |
|------|------|------|
| 기능 개발 | `feat/{설명}` | `feat/login-page` |
| 버그 수정 | `fix/{설명}` | `fix/button-not-responding` |
| 문서 작업 | `docs/{설명}` | `docs/update-readme` |
| 설정·의존성 | `chore/{설명}` | `chore/upgrade-eslint` |
| 스타일 수정 | `style/{설명}` | `style/header-layout` |
| 리팩토링 | `refactor/{설명}` | `refactor/auth-module` |
| 테스트 | `test/{설명}` | `test/add-login-spec` |
| 성능 개선 | `perf/{설명}` | `perf/loan-calc-optimize` |
| CI 설정 | `ci/{설명}` | `ci/add-github-actions` |

- 영어 소문자 + 하이픈(`-`) 사용
- 단어는 간결하게

---

## 커밋 메시지 컨벤션

[Conventional Commits](https://www.conventionalcommits.org/) 스펙을 따릅니다.

### 형식

```
<type>: <subject>

[body]

[footer]
```

### type 목록

| type | 설명 |
|------|------|
| `feat` | 새로운 기능 |
| `fix` | 버그 수정 |
| `docs` | 문서 변경 |
| `style` | 포맷팅, 세미콜론 등 로직 변경 없음 |
| `refactor` | 리팩토링 |
| `test` | 테스트 추가/수정 |
| `chore` | 빌드 설정, 패키지 등 |
| `perf` | 성능 개선 |
| `ci` | CI 설정 변경 |

### 예시

```
feat: 소셜 로그인 기능 추가
Closes #12
```

```
fix: 모바일에서 클릭 이벤트 미작동 수정
```

---

## Pull Request 규칙

- PR은 하나의 목적만 담습니다 (기능 하나, 버그 하나)
- PR의 base는 **분기점과 같습니다** — 성능 작업(`perf/`)은 `main`, 나머지는 `develop`
  (위 "브랜치 전략" 참고)
- PR 제목 형식: `[#이슈번호] type: 작업 내용`
  - 예시: `[#1] chore: 프로젝트 초기 세팅`
  - 예시: `[#12] fix: 이자율 계산 오류 수정`
- 최소 1명의 승인(Approve) 이후 merge 가능합니다
- 리뷰어는 48시간 내 리뷰를 완료합니다
- merge 방식은 **Squash and Merge**를 기본으로 합니다
- 예전에 있던 릴리스 PR(`develop` → `main`, Merge commit)은 두 브랜치를 병합하지 않기로 하면서
  더 이상 만들지 않습니다 (위 "브랜치 전략" 참고)

## 코드 리뷰 규칙

- 리뷰는 코드의 동작뿐 아니라 가독성, 컨벤션 준수 여부도 함께 확인합니다
- 요청 사항은 근거와 함께 작성합니다 (막연한 지적 대신 이유와 대안을 제시)
- 사소한 스타일 의견은 `nit:` 접두어를 붙여 필수 수정이 아님을 표시합니다
- 리뷰어의 코멘트에는 반영하거나 이유를 남겨 응답한 뒤 conversation을 resolve 합니다
