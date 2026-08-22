# 7단계 최종 부하 테스트 — 인수인계

작성 2026-08-19 · 브랜치 `perf/final-load-test-harness` (`main`에서 분기, **push 안 됨**)

> 다른 세션/계정이 이어받기 위한 문서다. **같은 머신**이라는 전제로 쓰였다.
> 원장(`.superpowers/sdd/…/progress.md`)은 git-ignore라 유실될 수 있어, 거기 있던
> **판정 8건과 검증된 환경 사실을 여기 복제**해 이 문서만으로 재개할 수 있게 했다.

---

## 1. 무엇을 하고 있나

k6 부하 테스트의 시나리오가 전부 고정값이었다 — 좌표 강남역 · 검색어 `스타벅스` ·
카테고리 카페/디저트 · 단일 `storeId`. **한 조합만 반복 측정하고 있었다.**

그래서 (a) 데이터셋을 실사용 분포로 바꾸고, (b) 개선 전 버전으로 되돌려 같은 하네스로 다시 잰 뒤,
(c) 최신 버전에서 최종 측정해 5단계 쿼리 개선의 효과를 보고한다.

**정본 문서 셋:**

| 파일 | 역할 |
|---|---|
| `docs/superpowers/specs/2026-08-19-final-load-test-design.md` | 설계. 결정과 근거. **분쟁 시 최종 권위** |
| `docs/superpowers/plans/2026-08-19-final-load-test.md` | 구현 계획. 10개 태스크, 실행 코드 포함 |
| `.superpowers/sdd/2026-08-19-final-load-test/progress.md` | 원장(있으면 우선). 없으면 이 문서 |

**실행 순서는 `1 → 2 → 10 → 3 → 4 → 5 → 6` 이다.** Task 10은 문서 끝에 있지만 Task 3보다 먼저다
(번호를 다시 매기면 계획의 상호참조가 깨져서 순서만 따로 고정했다).

---

## 2. 어디까지 됐나

| Task | 상태 |
|---|---|
| 1 추출 스크립트 기반 (접속 · 검색어 선택도 · `prod-sql.sh`) | ✅ 완료, 리뷰 clean |
| 2 격자 밀도 · 희소 앵커 | ✅ 완료, 리뷰 clean |
| 4 `load.js` 개조 | ✅ 완료, 리뷰 clean |
| **5 `baseline.js` 개조** | 구현됨(`b0b95f5`) · **리뷰 미완 — 재실행 필요** |
| 10 검색어 풀 재생성 | 미착수. 브리프 준비됨 |
| 3 CSV 생성 | 미착수 (Task 10 뒤) |
| 6 k6 EC2 배포 · 스모크 | 미착수 |
| 7~9 Phase B (측정 · 보고서) | 미착수 |

**운영 환경은 아직 하나도 안 건드렸다.** EB `gh-293.1-9194df4` 그대로 · RDS 인덱스와 데이터 그대로 ·
k6 EC2에 새 파일 없음 · 브랜치 push 안 함.

커밋 14개가 `origin/main..HEAD`에 쌓여 있다. 가장 최근이 `b0b95f5`.

---

## 3. 반드시 알아야 할 환경 사실 (전부 실측 확인됨 — 다시 알아내지 말 것)

- **운영 RDS에 로컬에서 바로 붙는다.** `fitwallet-db.c1g6w2em8fdg.ap-northeast-2.rds.amazonaws.com:3306`,
  DB `fitwallet`. 보안그룹이 3306을 k6 EC2의 `fitwallet-eb-sg`와 개발자 IP 3개에 열어 뒀고
  그중 하나가 이 머신이다
- **자격증명은 저장소에 없다.** EB 환경 속성 `DB_USERNAME`/`DB_PASSWORD`에서 꺼낸다.
  `scripts/perf-k6/prod-sql.sh`가 그 일을 한다 — **저장소의 `load.sh` 기본값
  (`fitwallet`/`fitwallet1234`)은 로컬 전용이고 운영은 거절한다**
- **로컬에 `mysql` 바이너리가 없다.** `docker exec -i fitwallet-mysql-perf mysql ...`로 컨테이너 것을
  빌려 쓴다. **`--default-character-set=utf8mb4`를 빼면 한글이 깨져 조용히 0건이 나온다**
- **k6는 k6 EC2(`i-05eb81746a575ca47`)에서 돌린다.** k6 v2.2.0 설치돼 있고 `/opt/perf` 존재,
  SSM Online. 로컬에서 쏘면 인터넷 왕복이 껴서 1차 측정과 조건이 어긋난다
- **k6 EC2 역할에 S3 권한이 없다**(`AmazonSSMManagedInstanceCore`만). 파일 전달은 **presigned GET URL**로 한다
- **`aws s3 presign`은 `--http-method`를 모른다**(aws-cli 2.36.16 실측). 결과 회수는 SSM 표준출력으로 한다
- **개선 전 WAR `gh-283.1-4b6f6af`가 EB에 아직 있다** — 재빌드 없이 버전 라벨만 바꾸면 되돌아간다
- 데이터: `store` 2,725,562행 · `search_history` 800,063행 · 카테고리 7종 · Flyway 이력 V13까지 ·
  FULLTEXT 아직 없음 · V12 인덱스 `idx_search_history_searched_at_keyword` 존재
- **`~/fitwallet-perf-data/raw/` 공공데이터 원본이 이 머신에만 있다.** Task 10이 그것을 다시 읽는다

---

## 4. 내가 내린 판정 8건 (근거와 함께 — 뒤집으려면 근거부터 보라)

| # | 판정 | 근거 | 틀렸을 때 비용 |
|---|---|---|---|
| P1 | 결과 회수를 presigned PUT → **SSM 표준출력** | `aws s3 presign --http-method` 미지원(실측). EC2엔 S3 권한 없음 | 결과 파일이 안 오거나 잘린다. 24,000자 절단 가드 넣음 |
| P2 | `densityBucket` **중복 유지** | 두 k6 스크립트가 이미 `login`·`ms`·`fire`를 각자 갖고 독립 배포된다 | 리뷰가 DRY로 지적. 모듈 추출은 10분 |
| P3 | baseline 좌표를 store 표본 → **격자 셀** | store 표본은 밀도 가중이라 희소 단계가 빈다 | 층화 좌표가 셀 중심이라 실제 상권과 미세하게 다르다 |
| P4 | CSV를 `git add -f` → **`.gitignore` 파일별 예외** | `active-users.csv`는 실제로 추적 안 됨. 강제 add는 판단을 index에 숨긴다 | `.gitignore` 3줄 되돌리면 끝 |
| P5 | 검색어 분포를 **보고서에 공개 의무화**(Task 9) | 305개 중 174개(57%)가 0건 매칭, 임계값 초과 4개(1.3%) | 숨기면 "찾을 게 없어 빨랐다"를 개선으로 오독한다 |
| P6 | 밀도 경계를 격자 수 → **매장 수 가중** 분위수 | 격자 수 기준이면 매장 91.3%가 한 단계에 몰린다(실측) | 단계 라벨 의미가 "트래픽 분위"가 된다 — 보고서에 그렇게 적으면 됨 |
| P7 | 태스크 순서 `3→4→5`를 **`4→5→3`**으로 | Task 3만 검색어 결정에 묶여 있다 | Task 4·5가 문법 검사만 받고 넘어간다. Task 6 스모크가 실물 검증 |
| P8 | **검색어 재생성 승인**(사용자), Task 10 신설 | 시드 검색어가 상호명과 겹치지 않고 빈도가 균등 | — (사용자 결정) |

**P1·P3·P6은 잡지 않았으면 측정을 망쳤을 것들이다** — 순서대로 결과 파일이 안 오고, 층화 표본이
실행 중 죽고, 밀도 분해가 아무것도 구분하지 못했다.

---

## 5. 실측으로 기각한 접근 두 가지 (다시 시도하지 말 것)

**① 지터를 키워 희소 좌표를 만드는 것.** 좌표를 `store`에서 뽑으면 출발점이 항상 가맹점이라,
지터를 ±1.1km까지 늘려도 **1km 안 0건이 0.0%**다(150표본). 벗어나면 다른 매장이 또 있다.
→ 희소 앵커를 3% 별도 주입한다.

**② 상호명 토큰화로 검색어 풀을 만드는 것.** 120,000개 상호명을 쪼개 세면 빈출 토큰이
`코리아(616)`·`본점(276)`·`2호점(148)`이고 `카페`는 47회뿐인데 `LIKE '%카페%'`는 35,759건이다
(`카페베네`가 한 토큰). **한국어 상호명은 검색어로 쪼개지지 않고 사용자는 부분문자열을 검색한다.**
→ 기존 풀에서 기타 업종을 빼고 가중치만 고친다(Task 10).

---

## 6. 다음에 할 일

1. **Task 5 리뷰 재실행.** diff는 `.superpowers/sdd/…/review-d08233a..b0b95f5.diff`에 있는데
   **커밋 3개가 섞여 있다 — `b0b95f5`의 `baseline.js`만 보라고 리뷰어에게 지시할 것.**
   이미 해소된 항목: `scenarioFor`(61행)가 `const WARMUP`(79행)을 참조하지만 호출은 396~397행
   `default()` 안뿐이라 TDZ 문제 없음 — 다시 제기하지 말 것
2. **Task 10 — 검색어 풀 재생성.** ⚠️ **운영 `search_history` 800,063행 재적재를 포함한다**
   (사용자 승인 완료). `PERF_TABLES="search_history"`를 반드시 지정할 것 — 빼면 전 테이블이 날아간다.
   공공데이터 재파싱 + 3~4분짜리 선택도 재측정이 있어 시간이 걸린다
3. **Task 3 → Task 6.** Task 6 스모크가 Task 4·5의 미검증 4건(인덱스 겹침 · 워밍업 구간 분리 ·
   희소 좌표 `storeId` 폴백 · 밀도 버킷 경계)을 실물로 확인한다
4. **Phase A 최종 리뷰 → Phase B(측정 4회).** Phase B는 개선 전 WAR 배포 · V12 인덱스 DROP ·
   PR #270 머지가 들어간다 — **시작 전 사용자 확인을 받을 것**

---

## 7. 작업 방식

`superpowers:subagent-driven-development` 스킬로 진행했다. 태스크마다 구현 서브에이전트 →
태스크 리뷰 → 필요 시 수정 루프. 컨트롤러(메인 세션)는 직접 코드를 고치지 않는다.

- 브리프 생성: `<skill>/scripts/task-brief <plan> <N>`
- 리뷰 패키지: `<skill>/scripts/review-package <plan> <BASE> <HEAD>`
- 스킬 경로: `~/.claude/plugins/cache/claude-plugins-official/superpowers/6.3.0/skills/subagent-driven-development`

커밋 트레일러는 `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.
성능 작업이라 브랜치도 PR base도 `main`이다(AGENTS.md §14).
