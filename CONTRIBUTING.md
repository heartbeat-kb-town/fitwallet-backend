# Contributing Guide

## 이슈 작성 규칙

- 버그 리포트, 기능 요청 등 각 템플릿을 사용합니다
- 이슈 제목은 명확하게 작성합니다
- 담당자(Assignee)와 라벨을 반드시 지정합니다

## 브랜치 전략

`main` 브랜치는 항상 배포 가능한 상태를 유지합니다.
직접 push는 금지이며, 모든 변경은 Pull Request를 통해 반영합니다.

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
- PR 제목 형식: `[#이슈번호] type: 작업 내용`
  - 예시: `[#1] chore: 프로젝트 초기 세팅`
  - 예시: `[#12] fix: 이자율 계산 오류 수정`
- 최소 1명의 승인(Approve) 이후 merge 가능합니다
- 리뷰어는 48시간 내 리뷰를 완료합니다
- merge 방식은 **Squash and Merge**를 기본으로 합니다

## 코드 리뷰 규칙

- 리뷰는 코드의 동작뿐 아니라 가독성, 컨벤션 준수 여부도 함께 확인합니다
- 요청 사항은 근거와 함께 작성합니다 (막연한 지적 대신 이유와 대안을 제시)
- 사소한 스타일 의견은 `nit:` 접두어를 붙여 필수 수정이 아님을 표시합니다
- 리뷰어의 코멘트에는 반영하거나 이유를 남겨 응답한 뒤 conversation을 resolve 합니다
