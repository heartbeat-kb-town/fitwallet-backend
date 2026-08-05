# Contributing Guide

## 이슈 작성 규칙

- 버그 리포트, 기능 요청 등 각 템플릿을 사용합니다
- 이슈 제목은 명확하게 작성합니다
- 담당자(Assignee)와 라벨을 반드시 지정합니다

## 브랜치 전략

배포되는 브랜치와 개발이 쌓이는 브랜치를 분리합니다.
`main`, `develop` 모두 직접 push는 금지이며, 모든 변경은 Pull Request를 통해 반영합니다.

| 브랜치 | 역할 | 어떻게 갱신되나 | 병합 방식 |
|------|------|----------------|----------|
| `main` | 배포 브랜치. 배포된 것만 담습니다 | `develop` → `main` 릴리스 PR로만 | **Merge commit** |
| `develop` | 통합 브랜치(기본 브랜치). 다음 배포에 들어갈 작업이 모입니다 | 작업 브랜치 → `develop` PR | **Squash and Merge** |
| 작업 브랜치 | 이슈 하나 단위의 작업 | `develop`에서 분기 | (머지 후 삭제) |

```
feat/login-page ─┐
fix/calc-error ──┼─▶ develop ─(릴리스 PR)─▶ main
docs/update ─────┘
```

- 작업 브랜치는 **항상 최신 `develop`에서 분기**하고, PR의 base도 `develop`입니다
- `main`으로 가는 PR은 릴리스 PR 하나뿐입니다
- 릴리스 PR은 Merge commit으로 합칩니다. Squash로 합치면 `main`과 `develop`의 히스토리가 갈라져
  다음 릴리스마다 충돌이 반복됩니다

### 릴리스 (develop → main)

배포 시점에 `develop`의 내용을 `main`으로 올립니다.

```bash
gh pr create --base main --head develop --title "release: 2026-07-28 배포"
```

- 제목: `release: {날짜 또는 버전} 배포` — 이슈 번호는 붙이지 않습니다
- 본문에는 이번 배포에 포함된 PR 목록을 적습니다
- **Merge commit**으로 머지합니다 (Squash 금지)
- 머지 후 `main`은 `develop`의 조상이 되므로, `main`을 `develop`으로 되돌려 머지하는 작업은 필요 없습니다

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
- PR의 base는 `develop`입니다
- PR 제목 형식: `[#이슈번호] type: 작업 내용`
  - 예시: `[#1] chore: 프로젝트 초기 세팅`
  - 예시: `[#12] fix: 이자율 계산 오류 수정`
- 최소 1명의 승인(Approve) 이후 merge 가능합니다
- 리뷰어는 48시간 내 리뷰를 완료합니다
- merge 방식은 **Squash and Merge**를 기본으로 합니다
- 단, 릴리스 PR(`develop` → `main`)은 예외입니다. 제목은 `release: {날짜 또는 버전} 배포` 형식이고
  이슈 번호를 붙이지 않으며, **Merge commit**으로 합칩니다 (위 "릴리스" 절 참고)

## 코드 리뷰 규칙

- 리뷰는 코드의 동작뿐 아니라 가독성, 컨벤션 준수 여부도 함께 확인합니다
- 요청 사항은 근거와 함께 작성합니다 (막연한 지적 대신 이유와 대안을 제시)
- 사소한 스타일 의견은 `nit:` 접두어를 붙여 필수 수정이 아님을 표시합니다
- 리뷰어의 코멘트에는 반영하거나 이유를 남겨 응답한 뒤 conversation을 resolve 합니다
