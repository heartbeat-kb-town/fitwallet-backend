# 4단계 1차 부하 테스트 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 운영 환경에 동시 사용자 100명을 5분간 걸어 엔드포인트 19개의 SLO 통과/실패를 판정하는 k6 스크립트와 그 입력 데이터를 만든다.

**Architecture:** k6 스크립트 하나(`load.js`)가 유저 여정 1회(READ 19호출 + 화면 사이 think time)를 정의하고, VU마다 서로 다른 활성 유저를 배정해 실행한다. 유저 목록은 별도 셸 스크립트가 RDS에서 CSV로 뽑아 두고 k6가 `SharedArray`로 읽는다. 워밍업(ramping-vus)과 판정 구간(constant-vus)을 **별도 시나리오로 분리**하고 `phase` 태그로 구분해, SLO 임계값과 처리량 집계가 판정 구간만 보게 한다.

**Tech Stack:** k6 (JavaScript ES module), MySQL client (`mysql` CLI), bash

**Spec:** 노션 「SLI/SLO 정의 · 1차 부하 테스트 설계 (4단계)」
https://app.notion.com/p/3bfa561881a481eb9622ef76af16668a

## Global Constraints

- **브랜치는 `main`에서 갈라 `main`으로 돌아간다** (성능 작업). 브랜치명 `perf/load-test-4th-stage`
- **`main` 머지는 즉시 운영 배포다.** 측정 진행 중에는 어떤 PR도 머지하지 않는다
- 저장소에는 **도구만** 둔다. 수치와 해석의 정본은 노션이다 (`scripts/perf-k6/README.md` 참고)
- `scripts/perf-k6/results/`와 `*.csv`는 이미 `.gitignore` 대상이다. **유저 목록 CSV를 커밋하지 않는다**
- 참조 구현은 `scripts/perf-k6/baseline.js`다. 주석 밀도·한국어 설명·`__ENV` 기본값 패턴을 그대로 따른다
- 커밋 메시지: `type: 한국어 설명` + 트레일러 `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`
- 측정 대상 기본값: `BASE_URL=http://fitwallet-backend-prod.ap-northeast-2.elasticbeanstalk.com`
- 성능 계정: `perf000001`~`perf050000`, 비밀번호 `11112222`, PIN `123456`
- 앱 시계 고정값 `clock.fixed-date=2026-07-24` → 측정 월 `YEAR_MONTH=2026-07`

### SLO 값 (계획 문서 §3 표 — 바꾸지 않는다)

| 계열 | p95 | 해당 엔드포인트 |
|---|---:|---|
| 단순 조회 | 200ms | `health_db` `user_me` `user_frequent_places` `user_cards` `user_cards_recent` |
| 검색 | 300ms | `store_search_coords` `store_search_keyword` `store_search_category` `store_keywords` |
| 집계 · 리포트 | 500ms | `card_*` 5종 · `benefit_expected` · `report_*` 4종 |
| 공통 | 에러율 1% 이하 | 전체 |

---

## File Structure

| 파일 | 책임 |
|---|---|
| `scripts/perf-k6/extract-active-users.sh` (신규) | RDS에서 활성 유저 `login_id`를 뽑아 CSV로 저장. k6와 완전히 분리 — DB 접속은 이 파일에만 있다 |
| `scripts/perf-k6/load.js` (신규) | 부하 시나리오 전부. 여정 정의 · setup · 실행 · 결과 표 |
| `scripts/perf-k6/README.md` (수정) | 4단계 절 추가. 실행법과 함정 |
| `scripts/perf-k6/active-users.csv` (생성물) | 커밋하지 않는다 (`.gitignore`의 `*.csv`) |

`load.js`를 한 파일로 두는 이유: `baseline.js`가 이미 같은 구조(단일 파일 400줄)이고, 두 스크립트가 공유하는 것은 `login()`·`authHeaders()` 정도라 공통 모듈로 빼면 얻는 것보다 간접 참조가 늘어나는 비용이 크다. 섣부른 추상화를 하지 않는다.

---

## Task 0: 브랜치 준비

**Files:** 없음 (git 작업만)

> ⚠️ 현재 워킹트리가 `main`이 아닌 다른 브랜치일 수 있다. 성능 작업은 **`main`에서 갈라 `main`으로** 돌아간다.

- [ ] **Step 1: `main`에서 분기한다**

```bash
git checkout main && git pull origin main
git checkout -b perf/load-test-4th-stage
```

- [ ] **Step 2: 분기점을 확인한다**

Run: `git log --oneline -1 && git merge-base --is-ancestor origin/main HEAD && echo "main 기준 OK"`
Expected: `main 기준 OK`

> 워킹트리에 `src/main/java/com/fitwallet/domain/payment/dto/PaymentTransactionValues.java`가 미추적으로 남아 있을 수 있다. **`develop` 레인 물건이므로 이 작업에 add하지 않는다.**

---

## Task 1: 활성 유저 CSV 추출 스크립트

**Files:**
- Create: `scripts/perf-k6/extract-active-users.sh`

**Interfaces:**
- Produces: `scripts/perf-k6/active-users.csv` — 헤더 없이 한 줄에 `login_id` 하나. Task 3의 `setup()`이 읽는다

**왜 필요한가:** 3단계는 유저 한 명(`perf000100`)으로 쟀다. 부하에서 한 유저로만 쏘면 그 유저의 행만 버퍼 풀에 눌러앉아 적중률이 비현실적으로 100%가 되고, 재산정으로 확보한 유저당 거래 편차 7.8배가 전혀 반영되지 않는다.

**왜 k6가 아니라 셸인가:** k6는 표준 배포판에서 MySQL에 붙지 못한다(`xk6-sql` 확장 필요). 그리고 유저 목록은 재적재할 때만 바뀌므로 매 실행마다 뽑을 이유가 없다.

- [ ] **Step 1: 스크립트를 작성한다**

```bash
#!/usr/bin/env bash
#
# 활성 유저 login_id를 CSV로 뽑는다. load.js가 SharedArray로 읽는다.
#
#   scripts/perf-k6/extract-active-users.sh
#   PERF_DB_HOST=<RDS 엔드포인트> PERF_DB_PASSWORD=<비번> scripts/perf-k6/extract-active-users.sh
#
# ⚠️ 측정 대상이 운영 RDS이므로 목록도 운영 RDS에서 뽑아야 한다. 로컬 성능 DB(3308)에서
#    뽑으면 존재하지 않는 login_id가 섞여 setup()이 로그인 실패로 죽는다.
#    k6 EC2가 fitwallet-eb-sg를 달고 있어 RDS 3306에 닿는다 — 거기서 돌리는 것이 가장 쉽다.
#
# 접속 정보는 EB 환경 속성에만 있다:
#   aws elasticbeanstalk describe-configuration-settings \
#     --application-name fitwallet-backend --environment-name fitwallet-prod \
#     --query "ConfigurationSettings[0].OptionSettings[?Namespace=='aws:elasticbeanstalk:application:environment']"
set -euo pipefail

DB_HOST="${PERF_DB_HOST:-127.0.0.1}"
DB_PORT="${PERF_DB_PORT:-3308}"
DB_USER="${PERF_DB_USER:-fitwallet}"
DB_PASSWORD="${PERF_DB_PASSWORD:-fitwallet1234}"
DB_NAME="${PERF_DB_NAME:-fitwallet}"

# 측정 월. 앱 시계가 clock.fixed-date=2026-07-24로 고정돼 있어 앱이 말하는 "이번 달"이다.
YEAR_MONTH="${YEAR_MONTH:-2026-07}"
MONTH_START="${YEAR_MONTH}-01"
NEXT_MONTH_START="$(date -j -f '%Y-%m-%d' -v+1m "${MONTH_START}" '+%Y-%m-%d' 2>/dev/null \
    || date -d "${MONTH_START} +1 month" '+%Y-%m-%d')"

# 뽑을 인원. VU 수보다 넉넉해야 한다 — VU를 늘려도 CSV를 다시 뽑지 않게.
LIMIT="${LIMIT:-500}"

# 그 달 거래 최소 건수.
#
# 0건인 유저를 넣으면 집계 API가 빈 응답을 20ms에 돌려주는데 **0행 응답도 HTTP 200이라
# 에러율 0%로 초록불이 뜬다.** 표만 보면 "리포트는 이미 빠르다"로 읽혀 개선 대상에서 빠진다.
#
# 다만 하한을 높이면 실제 분포의 꼬리를 잘라 편차가 줄어든다 — 유저당 거래 편차를 재현하려고
# 데이터를 재산정했으므로 최소한만 거른다. 2026-07 유저당 거래는 4~633건(평균 76.9)이다.
MIN_TX="${MIN_TX:-5}"

OUT="${OUT:-$(dirname "$0")/active-users.csv}"

echo "추출: ${DB_HOST}:${DB_PORT}/${DB_NAME} — ${MONTH_START} ~ ${NEXT_MONTH_START}, 거래 ${MIN_TX}건 이상, 최대 ${LIMIT}명"

# -N 헤더 없음 / -B 탭 구분(따옴표 없음). 컬럼이 하나라 그대로 CSV가 된다.
MYSQL_PWD="$DB_PASSWORD" mysql -N -B \
    --default-character-set=utf8mb4 \
    -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" "$DB_NAME" <<SQL > "$OUT"
SELECT u.login_id
  FROM payment_transaction pt
  JOIN user_card uc ON uc.user_card_id = pt.user_card_id
  JOIN users u      ON u.user_id = uc.user_id
 WHERE pt.paid_at >= '${MONTH_START}'
   AND pt.paid_at <  '${NEXT_MONTH_START}'
 GROUP BY u.login_id
HAVING COUNT(*) >= ${MIN_TX}
 ORDER BY RAND()
 LIMIT ${LIMIT};
SQL

COUNT="$(wc -l < "$OUT" | tr -d ' ')"
echo "완료: ${OUT} — ${COUNT}명"

# 조용히 빈 파일이 나오면 setup()이 엉뚱한 에러로 죽는다. 여기서 끊는다.
if [[ "$COUNT" -lt 100 ]]; then
    echo "중단: 100명 미만이다. 측정 월(${YEAR_MONTH})에 데이터가 있는지, 접속한 DB가 맞는지 확인해라." >&2
    exit 1
fi
```

- [ ] **Step 2: 실행 권한을 준다**

```bash
chmod +x scripts/perf-k6/extract-active-users.sh
```

- [ ] **Step 3: 문법을 검사한다 (DB 없이)**

Run: `bash -n scripts/perf-k6/extract-active-users.sh && echo OK`
Expected: `OK`

- [ ] **Step 4: 실제로 뽑아 형식을 확인한다**

> AWS 스택이 켜져 있고 RDS에 닿을 수 있어야 한다. 로컬에서 붙으려면 `fitwallet-rds-sg`에 내 IP가 열려 있어야 한다.

```bash
PERF_DB_HOST=<RDS 엔드포인트> PERF_DB_PORT=3306 \
PERF_DB_USER=<유저> PERF_DB_PASSWORD=<비번> \
  scripts/perf-k6/extract-active-users.sh

wc -l scripts/perf-k6/active-users.csv
head -3 scripts/perf-k6/active-users.csv
```

Expected: 500줄, 각 줄이 `perf0NNNNN` 형태의 문자열 하나. 따옴표·탭·헤더가 없어야 한다.

- [ ] **Step 5: CSV가 커밋 대상이 아닌지 확인한다**

Run: `git status --short scripts/perf-k6/active-users.csv`
Expected: 출력 없음 (`.gitignore`의 `*.csv`가 잡는다)

- [ ] **Step 6: 커밋**

```bash
git add scripts/perf-k6/extract-active-users.sh
git commit -m "perf: 활성 유저 목록을 CSV로 뽑는 스크립트를 추가한다

부하 테스트에서 VU마다 다른 유저를 쓰려면 활성 유저 목록이 필요하다.
한 유저로만 쏘면 그 유저의 행만 버퍼 풀에 남아 적중률이 100%가 되고,
재산정으로 확보한 유저당 거래 편차 7.8배가 반영되지 않는다.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 2: `load.js` — 여정 정의와 시나리오 옵션

**Files:**
- Create: `scripts/perf-k6/load.js`

**Interfaces:**
- Produces:
  - `SCREENS` — `[{ screen: string, calls: Call[] }]`
  - `Call` — `{ name: string, slo: number, url: (u: UserCtx) => string, auth?: false }`
    (`auth: false`는 `health_db`에만 붙는다 — `/api/**` 밖이라 `AuthInterceptor`를 타지 않는다)
  - `ALL_CALLS` — `Call[]` (SCREENS를 평탄화한 것)
  - `UserCtx` — `{ loginId: string, token: string, userCardId: number, storeId: number }` (Task 3이 채운다)
  - `options` — k6 시나리오/임계값

**설계 근거:** 화면 그룹 사이에만 think time을 넣으므로 여정을 평탄한 배열이 아니라 **화면 단위로 중첩**해서 정의한다. 워밍업과 판정 구간을 별도 시나리오로 나눠 `phase` 태그를 붙이면, 임계값이 판정 구간만 보게 만들 수 있다 — 한 시나리오로 두면 램프업의 느린 구간이 p95에 섞인다.

- [ ] **Step 1: 파일을 만든다**

```javascript
/**
 * 4단계 — 1차 부하 테스트 (동시 사용자 100명)
 *
 * 3단계 baseline이 "병목이 어디고 왜인가"를 규명했다면, 이 스크립트는
 * **"우리 규모에서 서비스 가능한가"**만 판정한다. 병목을 다시 찾지 않는다.
 *
 * 실행:
 *   k6 run scripts/perf-k6/load.js
 *
 * 산출물: stdout 마크다운 표 + load-summary.md / load-summary.json
 *
 * ──────────────────────────────────────────────────────────────────────────
 * 왜 동시 사용자 100명인가
 *
 * 계획 문서의 "300 TPS"는 근거가 없다 — 멘토 한 마디가 전부고 우리 역산은 약 15 TPS다.
 * TPS로는 유도가 안 되지만 동시 사용자 수는 깔끔하게 나온다.
 *
 *   DAU 5,000명 × 피크 시간대 방문 20% = 피크 1시간에 1,000명
 *   1,000명 × (세션 3분 ÷ 60분)        = 동시 사용자 약 50명
 *
 * 측정은 그 2배인 100명에서 한다. 50명은 커넥션 풀(20)에 여유가 있어(11개 필요) 경합이
 * 거의 없고 baseline과 비슷한 수치가 나온다. 100명은 22개가 필요해 **풀 상한을 넘는다** —
 * 부하 때문에 새로 실패하는 항목이 여기서 드러난다.
 *
 * 대가: 판정 문장이 "우리 규모에서 되는가"가 아니라 "우리 규모의 2배에서 되는가"가 된다.
 * ──────────────────────────────────────────────────────────────────────────
 * 왜 VU 고정인가 (도착률 고정이 아니라)
 *
 * 도착률 고정(ramping-arrival-rate)은 "N TPS를 견디나"를 직접 답하지만, 목표 TPS가 있어야
 * 의미가 있다. 그 목표를 버렸으므로 장점이 사라진다. 게다가 예상 용량(88 TPS)의 3.4배인
 * 300 TPS를 밀어넣으면 대기열이 무한히 자라 "완전히 무너진 상태"만 오래 재게 된다.
 *
 * ⚠️ VU 고정의 알려진 한계 — coordinated omission. 서버가 느려지면 VU가 응답을 기다리느라
 * 요청을 덜 보내서 **느린 응답이 지연시간 분포에 실제보다 적게 실린다.** 그래서 p95를
 * 항상 달성 처리량과 함께 읽는다 — 97 TPS에 크게 못 미치면 p95는 낙관적인 값이다.
 * ──────────────────────────────────────────────────────────────────────────
 */

import http from 'k6/http';
import exec from 'k6/execution';
import { sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://fitwallet-backend-prod.ap-northeast-2.elasticbeanstalk.com')
    .replace(/\/$/, '');

const VUS = Number(__ENV.VUS || 100);
const RAMP = __ENV.RAMP || '1m';
const DURATION = __ENV.DURATION || '5m';
/** 판정 구간 초. 달성 처리량을 직접 계산할 때 쓴다 (k6 요약은 전체 구간 평균이라 램프에 희석된다). */
const DURATION_SEC = Number(__ENV.DURATION_SEC || 300);

/**
 * 화면 사이 대기(초). 이 값이 없으면 VU 1명이 쉬지 않고 요청을 쏴서 실제 사용자
 * 여러 명분이 되고, **VU 수와 동시 사용자 수가 어긋나** 위 역산과 연결되지 않는다.
 */
const THINK = Number(__ENV.THINK || 3);

const USER_FILE = __ENV.USER_FILE || './active-users.csv';
const PASSWORD = __ENV.PERF_PASSWORD || '11112222';
const YEAR_MONTH = __ENV.YEAR_MONTH || '2026-07';

/** 서울 강남역. 272만 건 중 밀도가 높아 거리 계산 후 남는 행이 많다. */
const LAT = 37.4979;
const LNG = 127.0276;

/** 계획 문서 §3의 SLO 표. 측정값으로 조정하지 않는다. */
const SLO_SIMPLE = 200;
const SLO_SEARCH = 300;
const SLO_AGG = 500;

/**
 * 활성 유저 목록. extract-active-users.sh가 만든다.
 * SharedArray는 VU 전체가 메모리 한 벌을 공유한다 — VU마다 복사하면 100배가 된다.
 */
const users = new SharedArray('active users', function () {
    return open(USER_FILE).split('\n').map((s) => s.trim()).filter(Boolean);
});

// ── 여정 정의 ──────────────────────────────────────────────────────────────
// 화면 그룹 사이에만 think time을 넣으므로 평탄한 배열이 아니라 화면 단위로 중첩한다.
// name은 k6 태그로 쓰이고 정적이어야 한다(URL에 ID가 박힌 요청들이 따로 집계되지 않게).

const SCREENS = [
    {
        screen: '홈',
        calls: [
            { name: 'user_me',              slo: SLO_SIMPLE, url: () => '/api/user/me' },
            { name: 'user_cards',           slo: SLO_SIMPLE, url: () => '/api/user-cards' },
            // 5단계 개선 2순위 — 상관관계 없는 파생 테이블로 payment_transaction 전체를 집계한다
            { name: 'user_cards_recent',    slo: SLO_SIMPLE, url: () => '/api/user-cards?sort=RECENTLY_USED' },
            { name: 'report_summary',       slo: SLO_AGG,    url: () => `/api/report/benefit/summary?yearMonth=${YEAR_MONTH}` },
            { name: 'user_frequent_places', slo: SLO_SIMPLE, url: () => '/api/user/frequent-places' },
        ],
    },
    {
        screen: '혜택',
        calls: [
            { name: 'report_missed_app',    slo: SLO_AGG, url: () => `/api/report/benefit/missed?yearMonth=${YEAR_MONTH}&lossType=APP_UNUSED` },
            { name: 'report_missed_card',   slo: SLO_AGG, url: () => `/api/report/benefit/missed?yearMonth=${YEAR_MONTH}&lossType=CARD_MISMATCH` },
            { name: 'report_card_received', slo: SLO_AGG, url: (u) => `/api/report/benefit/received/cards/${u.userCardId}?yearMonth=${YEAR_MONTH}` },
        ],
    },
    {
        screen: '카드',
        calls: [
            { name: 'card_summary',      slo: SLO_AGG, url: (u) => `/api/card/${u.userCardId}/summary` },
            { name: 'card_event',        slo: SLO_AGG, url: (u) => `/api/card/${u.userCardId}/event` },
            { name: 'card_benefit',      slo: SLO_AGG, url: (u) => `/api/card/${u.userCardId}/benefit` },
            { name: 'card_transactions', slo: SLO_AGG, url: (u) => `/api/card/${u.userCardId}/transactions?yearMonth=${YEAR_MONTH}` },
            { name: 'card_usage',        slo: SLO_AGG, url: (u) => `/api/card/${u.userCardId}/usage?yearMonth=${YEAR_MONTH}` },
        ],
    },
    {
        screen: '검색',
        calls: [
            { name: 'store_keywords',        slo: SLO_SEARCH, url: () => '/api/store/keywords' },
            // 1 VU에서 이미 SLO의 7.3배다. 부하에서 실패하는 것은 새 정보가 아니다.
            { name: 'store_search_coords',   slo: SLO_SEARCH, url: () => `/api/store/search?latitude=${LAT}&longitude=${LNG}&radiusMeters=3000` },
            { name: 'store_search_keyword',  slo: SLO_SEARCH, url: () => `/api/store/search?keyword=%EC%8A%A4%ED%83%80%EB%B2%85%EC%8A%A4&latitude=${LAT}&longitude=${LNG}` },
            { name: 'store_search_category', slo: SLO_SEARCH, url: () => `/api/store/search?categoryId=1&latitude=${LAT}&longitude=${LNG}&radiusMeters=3000` },
        ],
    },
    {
        screen: '결제 전',
        calls: [
            { name: 'benefit_expected', slo: SLO_AGG, url: (u) => `/api/benefit/expected?storeId=${u.storeId}&amount=15000` },
        ],
    },
    {
        screen: '기타',
        calls: [
            { name: 'health_db', slo: SLO_SIMPLE, auth: false, url: () => '/health/db' },
        ],
    },
];

const ALL_CALLS = SCREENS.flatMap((s) => s.calls);

// 판정 구간의 요청 수를 직접 센다. k6 요약의 http_reqs.rate는 램프업까지 포함한
// 전체 구간 평균이라 판정 구간 처리량보다 낮게 나온다.
const steadyReqs = new Counter('steady_reqs');
/** 500 응답. 커넥션 풀 고갈(connectionTimeout=3000)이 이 형태로 나타난다. */
const serverErrors = new Counter('server_errors');

// ── 시나리오 ───────────────────────────────────────────────────────────────
/*
 * 워밍업과 판정 구간을 **별도 시나리오로 나눈다.** 한 시나리오로 두면 램프업 동안의
 * 느린 응답이 p95에 섞여 판정이 흐려진다. scenario의 tags는 그 안에서 나온 모든 메트릭에
 * 붙으므로, 임계값을 phase:steady로 좁힐 수 있다.
 *
 * 워밍업이 필요한 이유: 데이터 9.21GB가 버퍼 풀 5.25GB의 1.75배라 캐시가 자리 잡는 데
 * 시간이 걸린다. 3단계까지는 전부 캐시(적중률 99.76%)라 변수가 아니었다.
 */
const thresholds = {
    'http_req_failed{phase:steady}': ['rate<0.01'],
};
for (const c of ALL_CALLS) {
    thresholds[`http_req_duration{name:${c.name},phase:steady}`] = [`p(95)<${c.slo}`];
}

export const options = {
    scenarios: {
        warmup: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [{ target: VUS, duration: RAMP }],
            gracefulRampDown: '0s',
            tags: { phase: 'warmup' },
        },
        steady: {
            executor: 'constant-vus',
            vus: VUS,
            duration: DURATION,
            startTime: RAMP,
            tags: { phase: 'steady' },
        },
    },
    thresholds,
    // setup()이 VU 수만큼 로그인하고 카드를 조회한다. 100명이면 1분 가까이 걸려
    // 기본값 60초로는 모자란다.
    setupTimeout: '5m',
    summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'max'],
    // 응답 본문을 남긴다. 500이 났을 때 CannotGetJdbcConnection인지 봐야
    // 커넥션 풀 고갈을 확인할 수 있다(§설계 5). 응답이 작아 메모리 부담이 없다.
    discardResponseBodies: false,
};
```

- [ ] **Step 2: 임시 스텁을 붙여 파싱이 되게 한다**

파일 끝에 추가한다. Task 3·4가 이걸 실제 구현으로 대체한다.

```javascript
export function setup() {
    return { pool: [] };
}

export default function () {
    // Task 4에서 구현한다.
}
```

- [ ] **Step 3: k6가 스크립트와 옵션을 읽는지 확인한다**

> DB도 서버도 필요 없다. 파싱과 옵션 구성만 검사한다.

```bash
cd scripts/perf-k6
printf 'perf000100\nperf000200\n' > active-users.csv   # 임시 더미
k6 inspect load.js | head -40
```

Expected: JSON이 출력되고 그 안에
- `scenarios.warmup.executor` = `ramping-vus`, `scenarios.steady.executor` = `constant-vus`
- `scenarios.steady.vus` = 100, `scenarios.steady.startTime` = `1m`
- `thresholds`에 **20개 항목** (엔드포인트 19 + `http_req_failed`)
- `setupTimeout` = `5m`

- [ ] **Step 4: 임계값 개수를 정확히 센다**

Run:
```bash
k6 inspect scripts/perf-k6/load.js | python3 -c "
import json,sys
t=json.load(sys.stdin)['thresholds']
p95=[k for k,v in t.items() if any('p(95)' in str(x) for x in v)]
print('총', len(t), '· p(95)', len(p95), '· steady 태그 없는 키', [k for k in t if 'phase:steady' not in k])
"
```
Expected: `총 20 · p(95) 19 · steady 태그 없는 키 []`

> ⚠️ `grep -c 'p(95)<'`로 세면 **0이 나온다.** k6의 JSON 출력이 Go `encoding/json` 기본값대로
> `<`를 `<`로 이스케이프하기 때문이다(`p(95)<500`). 코드 문제가 아니다.

- [ ] **Step 5: 커밋**

```bash
git add scripts/perf-k6/load.js
git commit -m "perf: 부하 시나리오의 여정 정의와 시나리오 옵션을 만든다

워밍업(ramping-vus)과 판정 구간(constant-vus)을 별도 시나리오로 나누고
phase 태그로 구분한다. 한 시나리오로 두면 램프업의 느린 응답이 p95에 섞인다.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 3: `load.js` — `setup()` 유저 풀 확보

**Files:**
- Modify: `scripts/perf-k6/load.js` (Step 2에서 만든 `setup()` 스텁을 대체)

**Interfaces:**
- Consumes: `users` (SharedArray), `VUS`, `BASE_URL`, `PASSWORD`, `YEAR_MONTH`, `LAT`, `LNG`
- Produces: `setup()` 반환값 `{ pool: UserCtx[] }` — Task 4의 `default()`가 `__VU`로 인덱싱한다

**왜 미리 확보하나:** 로그인이 BCrypt라 102ms다. VU 100개가 각자 로그인하면 시작 구간에 스파이크가 생겨 판정 구간을 오염시킨다. `setup()`은 1회만 돌고 반환값이 전 VU에 공유된다. Access Token 유효시간이 30분이라 6분 테스트 동안 만료되지 않는다.

- [ ] **Step 1: `setup()` 스텁을 실제 구현으로 대체한다**

```javascript
function authHeaders(token) {
    return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
}

function login(loginId) {
    const res = http.post(`${BASE_URL}/api/user/login`,
        JSON.stringify({ loginId, password: PASSWORD }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'setup_login' } });

    if (res.status !== 200) {
        throw new Error(`로그인 실패 (${loginId}): HTTP ${res.status} — ${res.body}`);
    }
    return res.json('data.accessToken');
}

export function setup() {
    if (users.length < VUS) {
        throw new Error(
            `활성 유저가 ${users.length}명뿐인데 VU는 ${VUS}개다. `
            + 'extract-active-users.sh를 LIMIT을 늘려 다시 돌려라. '
            + '유저가 모자라면 같은 유저를 여러 VU가 공유하게 되어 버퍼 풀 적중률이 왜곡된다.');
    }

    // 가맹점 ID는 전 VU가 같은 값을 쓴다. 첫 유저의 토큰으로 한 번만 뽑는다.
    const probeToken = login(users[0]);
    const storeRes = http.get(
        `${BASE_URL}/api/store/search?latitude=${LAT}&longitude=${LNG}&radiusMeters=3000`,
        { headers: authHeaders(probeToken) });
    const stores = storeRes.json('data.stores') || [];
    if (stores.length === 0) {
        throw new Error('좌표 검색이 0건이다. 좌표를 바꾸거나 반경을 넓혀야 한다.');
    }
    const storeId = stores[0].storeId;

    const pool = [];
    for (let i = 0; i < VUS; i++) {
        const loginId = users[i];
        const token = i === 0 ? probeToken : login(loginId);

        /*
         * ⚠️ 활성 유저라도 보유 카드 전부에 거래가 있는 건 아니다. 월 75건이 카드 3.9장에
         * 흩어지므로 카드당 수십 건이고, cards[0]을 그냥 쓰면 카드 계열이 0행을 잰다
         * (3단계 함정 3). sort=RECENTLY_USED의 첫 카드가 가장 최근 쓴 카드이므로
         * 그 달 거래가 있을 가능성이 가장 높다.
         *
         * 카드마다 transactions를 조회해 최다를 고르는 방법이 더 정확하지만 유저당
         * 3.9호출이 추가돼 setup이 4배로 길어진다. 아래 표본 검증으로 대신한다.
         */
        const cardsRes = http.get(`${BASE_URL}/api/user-cards?sort=RECENTLY_USED`,
            { headers: authHeaders(token) });
        const cards = cardsRes.json('data') || [];
        if (cards.length === 0) {
            throw new Error(`${loginId}에 보유 카드가 없다. CSV 추출 조건을 확인해라.`);
        }

        pool.push({ loginId, token, userCardId: cards[0].userCardId, storeId });
    }

    /*
     * 표본 검증 — 앞 5명이 실제로 그 달 거래를 갖고 있는지 본다.
     *
     * 이 검사가 없으면 CSV가 잘못돼도 테스트가 초록불로 끝난다. 0행 응답도 HTTP 200이라
     * 에러율 0%가 나오고, 집계 API가 20ms에 답한 값이 표에 그대로 실린다. 표만 보면
     * "리포트는 부하에서도 빠르다"로 읽혀 완전히 틀린 결론이 나간다.
     */
    const sample = Math.min(5, pool.length);
    for (let i = 0; i < sample; i++) {
        const u = pool[i];
        const r = http.get(`${BASE_URL}/api/report/benefit/summary?yearMonth=${YEAR_MONTH}`,
            { headers: authHeaders(u.token) });
        const categories = r.json('data.categories') || [];
        const received = Number(r.json('data.totalReceivedBenefit') || 0);
        if (categories.length === 0 && received === 0) {
            throw new Error(
                `${u.loginId}는 ${YEAR_MONTH}에 거래가 없다(리포트 요약이 비어 있다). `
                + 'CSV를 뽑은 DB와 측정 대상 DB가 다르거나, YEAR_MONTH가 어긋났다. '
                + '앱 시계 고정값 clock.fixed-date의 EB 환경 속성 실제 값을 확인해라.');
        }
    }

    console.log(`[setup] BASE_URL=${BASE_URL}`);
    console.log(`[setup] 유저 풀 ${pool.length}명 (CSV ${users.length}명 중), storeId=${storeId}`);
    console.log(`[setup] yearMonth=${YEAR_MONTH}, think time=${THINK}초, VU=${VUS}`);
    console.log(`[setup] 표본 ${sample}명 리포트 검증 통과`);
    return { pool };
}
```

- [ ] **Step 2: 파싱을 확인한다**

Run: `k6 inspect scripts/perf-k6/load.js > /dev/null && echo OK`
Expected: `OK`

- [ ] **Step 3: `setup()`만 도는 스모크 실행**

> AWS 스택이 켜져 있어야 한다. VU 2개로 줄여 setup을 빠르게 끝낸다. `default()`는 아직 비어 있다.

```bash
cd scripts/perf-k6
VUS=2 RAMP=5s DURATION=5s k6 run load.js 2>&1 | grep '\[setup\]'
```

Expected: 네 줄이 모두 출력된다.
```
[setup] BASE_URL=http://fitwallet-backend-prod...
[setup] 유저 풀 2명 (CSV 500명 중), storeId=...
[setup] yearMonth=2026-07, think time=3초, VU=2
[setup] 표본 2명 리포트 검증 통과
```

- [ ] **Step 4: 가드가 실제로 동작하는지 확인한다**

Run: `cd scripts/perf-k6 && VUS=999 RAMP=5s DURATION=5s k6 run load.js 2>&1 | grep '활성 유저가'`
Expected: `활성 유저가 {CSV 줄 수}명뿐인데 VU는 999개다. ...` 메시지와 함께 중단
(Task 1을 이미 돌렸으면 500, Task 2의 더미 CSV만 있으면 2)

- [ ] **Step 5: 커밋**

```bash
git add scripts/perf-k6/load.js
git commit -m "perf: setup에서 VU 수만큼 유저 토큰과 카드를 미리 확보한다

로그인이 BCrypt라 102ms다. VU 100개가 각자 로그인하면 시작 구간에
스파이크가 생겨 판정 구간을 오염시킨다. 유저가 모자라거나 그 달 거래가
없으면 조용히 통과하지 않고 중단한다.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 4: `load.js` — 여정 실행

**Files:**
- Modify: `scripts/perf-k6/load.js` (Step 2에서 만든 `default()` 스텁을 대체)

**Interfaces:**
- Consumes: `SCREENS`, `setup()`의 `{ pool }`, `THINK`, `steadyReqs`, `serverErrors`
- Produces: 태그 `name`·`phase`가 붙은 `http_req_duration` / `http_req_failed` 메트릭

- [ ] **Step 1: `default()` 스텁을 실제 구현으로 대체한다**

```javascript
/** 에러 본문 로그 상한. 100 VU가 전부 실패하면 로그가 수만 줄이 된다. */
const ERROR_LOG_LIMIT = 20;
let errorLogged = 0;

function fire(call, u, isSteady) {
    const headers = call.auth === false
        ? {}
        : { Authorization: `Bearer ${u.token}` };

    // tags.name을 고정해야 URL에 userCardId가 박힌 요청들이 따로 집계되지 않는다.
    const res = http.get(`${BASE_URL}${call.url(u)}`, {
        headers,
        tags: { name: call.name },
        timeout: '60s',
    });

    if (isSteady) steadyReqs.add(1);

    if (res.status >= 500) {
        serverErrors.add(1, { name: call.name });
        /*
         * 본문을 남기는 이유 — 커넥션 풀(20)이 고갈되면 connectionTimeout=3000에 걸려
         * CannotGetJdbcConnection 계열 500이 난다. 응답이 느려지는 것이 아니라 에러가
         * 나는 형태라, 본문을 봐야 원인이 풀인지 다른 것인지 갈린다(설계 §5).
         */
        if (errorLogged < ERROR_LOG_LIMIT) {
            errorLogged++;
            console.warn(`[${call.name}] HTTP ${res.status} — ${String(res.body).slice(0, 300)}`);
        }
    }
}

export default function (data) {
    // __VU는 1부터. 시나리오가 둘이라 VU 번호가 겹칠 수 있어 모듈로로 감싼다.
    const u = data.pool[(__VU - 1) % data.pool.length];
    const isSteady = exec.scenario.name === 'steady';

    for (let i = 0; i < SCREENS.length; i++) {
        for (const call of SCREENS[i].calls) {
            fire(call, u, isSteady);
        }
        /*
         * 마지막 화면 뒤에는 대기하지 않는다 — 세션이 거기서 끝나기 때문이다.
         *
         * 고정 3초가 아니라 1.5~4.5초로 흩뿌린다. 고정하면 VU 100개가 같은 박자로
         * 묶여 파도처럼 몰려가고, 서버가 보는 순간 부하가 실제보다 뾰족해진다.
         * 평균은 그대로 3초라 세션 길이 19.6초 가정이 유지된다.
         */
        if (i < SCREENS.length - 1) {
            sleep(THINK * (0.5 + Math.random()));
        }
    }
}
```

- [ ] **Step 2: 파싱을 확인한다**

Run: `k6 inspect scripts/perf-k6/load.js > /dev/null && echo OK`
Expected: `OK`

- [ ] **Step 3: 스모크 실행 — 요청이 실제로 나가는지**

```bash
cd scripts/perf-k6
VUS=2 RAMP=5s DURATION=30s DURATION_SEC=30 k6 run load.js
```

Expected:
- `http_reqs` 총 개수가 0이 아니다
- 요약에 엔드포인트 19개의 `http_req_duration{name:...}` 임계값 줄이 전부 보인다
- `store_search_coords`는 ❌(SLO 300ms를 못 지킨다), `user_me`는 ✓

- [ ] **Step 4: 세션당 호출 수가 19인지 검증한다**

VU 2개 × 30초, 세션 19.6초 → 세션 2~3회 → 요청 38~57개 근처여야 한다.

Run: `cd scripts/perf-k6 && VUS=1 RAMP=1s DURATION=25s DURATION_SEC=25 k6 run load.js 2>&1 | grep -E 'http_reqs|iterations'`
Expected: `iterations` 1~2회, `http_reqs`가 그 19배 ±setup 호출 몇 개

- [ ] **Step 5: 태그가 붙었는지 확인한다**

Run: `cd scripts/perf-k6 && VUS=1 RAMP=1s DURATION=25s k6 run --out json=/tmp/k6out.json load.js > /dev/null 2>&1; grep -o '"phase":"steady"' /tmp/k6out.json | head -1`
Expected: `"phase":"steady"`

- [ ] **Step 6: 커밋**

```bash
git add scripts/perf-k6/load.js
git commit -m "perf: 유저 여정을 실행하고 화면 사이에 think time을 넣는다

think time을 1.5~4.5초로 흩뿌린다. 고정하면 VU 100개가 같은 박자로 묶여
서버가 보는 부하가 실제보다 뾰족해진다. 500 응답은 본문을 남긴다 —
커넥션 풀 고갈이 CannotGetJdbcConnection 형태로 나타나기 때문이다.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 5: `load.js` — 결과 표와 SLO 판정

**Files:**
- Modify: `scripts/perf-k6/load.js` (파일 끝에 추가)

**Interfaces:**
- Consumes: `ALL_CALLS`, `steadyReqs`, `serverErrors`, `DURATION_SEC`, `VUS`
- Produces: `load-summary.md`, `load-summary.json`

**왜 필요한가:** k6 기본 요약은 임계값 통과/실패를 줄줄이 찍지만 **1 VU baseline과 나란히 놓고 읽을 표**가 아니다. 4단계 리포트가 쓸 형태로 뽑는다.

- [ ] **Step 1: `handleSummary()`를 추가한다**

```javascript
// ── 결과 표 ────────────────────────────────────────────────────────────────

function ms(v) {
    if (v === undefined || v === null || Number.isNaN(v)) return '—';
    return v >= 1000 ? `**${(v / 1000).toFixed(2)}s**` : `${v.toFixed(0)}ms`;
}

function buildRows(data) {
    const rows = [];
    for (const c of ALL_CALLS) {
        // 판정 구간만 본다. 태그 조합 키는 k6가 서브메트릭으로 만들어 준다.
        const t = data.metrics[`http_req_duration{name:${c.name},phase:steady}`];
        if (!t) continue;
        rows.push({
            name: c.name,
            slo: c.slo,
            med: t.values.med,
            p95: t.values['p(95)'],
            max: t.values.max,
            pass: t.values['p(95)'] < c.slo,
        });
    }
    rows.sort((a, b) => b.p95 - a.p95);
    return rows;
}

export function handleSummary(data) {
    const rows = buildRows(data);
    const passed = rows.filter((r) => r.pass).length;

    /*
     * 달성 처리량을 직접 계산한다. data.metrics.http_reqs.values.rate는 램프업까지 포함한
     * 전체 구간 평균이라 판정 구간 처리량보다 낮게 나온다.
     */
    const steadyCount = data.metrics.steady_reqs ? data.metrics.steady_reqs.values.count : 0;
    const achievedTps = steadyCount / DURATION_SEC;
    const expectedTps = VUS * 0.97;   // VU 1명 ≈ 초당 1 요청 (19호출 ÷ 19.6초)
    const errCount = data.metrics.server_errors ? data.metrics.server_errors.values.count : 0;

    const lines = [
        '| # | 엔드포인트 | p50 | p95 | max | SLO | 판정 |',
        '|---:|---|---:|---:|---:|---:|:---:|',
    ];
    rows.forEach((r, i) => {
        lines.push(`| ${i + 1} | \`${r.name}\` | ${ms(r.med)} | ${ms(r.p95)} | ${ms(r.max)} `
            + `| ${r.slo}ms | ${r.pass ? '✅' : '❌'} |`);
    });

    const md = [
        `# 4단계 — 1차 부하 테스트 (동시 사용자 ${VUS}명)`,
        '',
        `- 대상: \`${BASE_URL}\``,
        `- 부하: VU ${VUS} 고정 · 램프업 ${RAMP}(집계 제외) + 판정 ${DURATION}`,
        `- think time: 평균 ${THINK}초 (${(THINK * 0.5).toFixed(1)}~${(THINK * 1.5).toFixed(1)}초 무작위)`,
        `- 측정 월: \`${YEAR_MONTH}\` · 유저: 활성 ${VUS}명을 VU마다 배정`,
        '',
        `**SLO 충족 ${passed} / ${rows.length}**`,
        '',
        `- 달성 처리량 **${achievedTps.toFixed(1)} TPS** (예상 ${expectedTps.toFixed(0)} TPS, `
            + `달성률 ${((achievedTps / expectedTps) * 100).toFixed(0)}%)`,
        `- 5xx 응답 ${errCount}건`,
        '',
        '> **달성률이 낮으면 p95를 액면 그대로 믿지 않는다.** VU 고정 방식은 서버가 느려지면',
        '> 요청을 덜 보내서 느린 응답이 분포에 적게 실린다(coordinated omission).',
        '> 달성률 저하 자체가 커넥션 풀 상한(20개)에 걸렸다는 신호이기도 하다 — 설계 §5.',
        '',
        '> 1 VU baseline에서 이미 SLO를 어기던 `store_search_coords`와 `user_cards_recent`가',
        '> 여기서도 실패하는 것은 새 정보가 아니다. **부하 때문에 새로 실패한 항목**을 본다.',
        '',
        'p95 내림차순 정렬.',
        '',
        lines.join('\n'),
        '',
    ].join('\n');

    return {
        stdout: `\n${md}\n`,
        'load-summary.md': md,
        'load-summary.json': JSON.stringify({
            baseUrl: BASE_URL, vus: VUS, ramp: RAMP, duration: DURATION,
            yearMonth: YEAR_MONTH, thinkSeconds: THINK,
            achievedTps, expectedTps, serverErrors: errCount,
            passed, total: rows.length, rows,
        }, null, 2),
    };
}
```

- [ ] **Step 2: 스모크로 표를 뽑아 본다**

```bash
cd scripts/perf-k6
VUS=2 RAMP=5s DURATION=40s DURATION_SEC=40 k6 run load.js
```

Expected:
- stdout 끝에 마크다운 표가 찍히고 행이 **19개**
- `SLO 충족 N / 19` 줄이 있다
- `달성 처리량 X TPS (예상 2 TPS, 달성률 …%)`
- `load-summary.md`와 `load-summary.json`이 현재 디렉터리에 생성된다

- [ ] **Step 3: 행 수를 정확히 센다**

Run: `cd scripts/perf-k6 && grep -c '^| [0-9]' load-summary.md`
Expected: `19`

- [ ] **Step 4: JSON이 파싱되는지 확인한다**

Run: `cd scripts/perf-k6 && python3 -c "import json;d=json.load(open('load-summary.json'));print(len(d['rows']), d['passed'], round(d['achievedTps'],1))"`
Expected: `19 <통과수> <처리량>`

- [ ] **Step 5: 산출물이 커밋 대상이 아닌지 확인한다**

Run: `cd scripts/perf-k6 && git status --short load-summary.md load-summary.json`
Expected: `?? scripts/perf-k6/load-summary.md` 와 `?? ...json` — **커밋하지 않는다.** 보관할 결과는 `results/`로 옮긴다(이미 `.gitignore` 대상)

- [ ] **Step 6: 임시 파일을 정리하고 커밋한다**

```bash
cd scripts/perf-k6 && rm -f load-summary.md load-summary.json /tmp/k6out.json
cd "$(git rev-parse --show-toplevel)"
git add scripts/perf-k6/load.js
git commit -m "perf: 부하 테스트 결과를 SLO 판정 표로 뽑는다

판정 구간(phase:steady) 서브메트릭만 읽는다. 달성 처리량은 직접 세는데,
k6의 http_reqs.rate가 램프업까지 포함한 전체 평균이라 판정 구간보다 낮게 나오기 때문이다.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 6: README 갱신

**Files:**
- Modify: `scripts/perf-k6/README.md`

**Interfaces:**
- Consumes: Task 1~5의 파일명과 환경변수

- [ ] **Step 1: 제목과 도입부를 두 스크립트 체제로 바꾼다**

기존 1~5행을 아래로 교체한다.

```markdown
# 성능 고도화 — k6 측정 스크립트

| 스크립트 | 단계 | 무엇을 답하나 |
|---|---|---|
| `baseline.js` | 3단계 | **병목이 어디고 왜인가** — 부하 없이(1 VU) 엔드포인트별 순수 처리시간 |
| `load.js` | 4단계 | **우리 규모에서 서비스 가능한가** — 동시 사용자 100명에서 SLO 통과/실패 |

계획 문서가 3단계를 4단계보다 앞에 둔 이유는, baseline 표가 있어야 부하를 얹었을 때
늘어난 시간을 **경합 탓**으로 돌릴 수 있기 때문이다.

> **수치와 해석의 정본은 노션이다.** 이 저장소에는 도구만 둔다.
> 4단계 설계: 「SLI/SLO 정의 · 1차 부하 테스트 설계 (4단계)」
```

- [ ] **Step 2: 4단계 절을 파일 끝에 추가한다**

```markdown
---

# 4단계 — 1차 부하 테스트 (`load.js`)

## 실행

```bash
# ① 활성 유저 목록을 먼저 뽑는다 (RDS에서. 재적재할 때만 다시 하면 된다)
PERF_DB_HOST=<RDS 엔드포인트> PERF_DB_PORT=3306 \
PERF_DB_USER=<유저> PERF_DB_PASSWORD=<비번> \
  scripts/perf-k6/extract-active-users.sh

# ② 부하 테스트 (약 6분)
cd scripts/perf-k6 && k6 run load.js
```

결과는 `load-summary.md` / `load-summary.json`으로 떨어진다. **같은 이름으로 덮어쓰므로**
보관할 결과는 `results/`로 옮겨 날짜를 붙인다.

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `BASE_URL` | 운영 EB 주소 | 측정 대상 |
| `VUS` | 100 | 동시 사용자. **CSV의 유저 수보다 작아야 한다** |
| `RAMP` | `1m` | 워밍업 구간. 집계에서 제외된다 |
| `DURATION` | `5m` | 판정 구간 |
| `DURATION_SEC` | 300 | 달성 처리량 계산용. `DURATION`과 같은 값을 초로 |
| `THINK` | 3 | 화면 사이 평균 대기(초). 실제로는 1.5~4.5초로 흩뿌린다 |
| `USER_FILE` | `./active-users.csv` | 활성 유저 목록 |
| `YEAR_MONTH` | `2026-07` | 앱 시계 고정값 `clock.fixed-date=2026-07-24` 기준의 "이번 달" |

`extract-active-users.sh`의 환경변수는 `PERF_DB_*`(load.sh와 같은 이름) · `LIMIT`(기본 500) ·
`MIN_TX`(기본 5) · `YEAR_MONTH`다.

## 왜 동시 사용자 100명인가

계획 문서의 "300 TPS"는 근거가 없다 — 멘토 한 마디가 전부고 자체 역산은 약 15 TPS다.
TPS로는 유도가 안 되지만 동시 사용자 수는 역산된다.

```
DAU 5,000명 × 피크 시간대 방문 20% = 피크 1시간에 1,000명
1,000명 × (세션 3분 ÷ 60분)        = 동시 사용자 약 50명
```

측정은 그 **2배인 100명**에서 한다. 50명은 커넥션 풀(20)에 여유가 있어(11개 필요)
baseline과 비슷한 수치가 나온다. 100명은 22개가 필요해 **풀 상한을 넘는다** — 부하 때문에
새로 실패하는 항목이 여기서 드러난다.

대가: 판정 문장이 *"우리 규모에서 되는가"*가 아니라 **"우리 규모의 2배에서 되는가"**가 된다.

## 밟으면 조용히 틀리는 함정

### 1. 유저 한 명으로 쏘면 측정이 무효다

3단계는 `perf000100` 한 명으로 쟀다. 부하에서 그렇게 하면 그 유저의 행만 버퍼 풀에
눌러앉는다. 데이터 9.21GB가 버퍼 풀 5.25GB의 **1.75배**인데 **적중률만 100%로 나오는**
상태가 되고, 재산정으로 확보한 유저당 거래 편차 7.8배도 반영되지 않는다.
→ VU마다 CSV에서 다른 유저를 배정한다.

### 2. `search_history`는 측정 당일에 다시 적재해야 한다

`store/keywords`가 `NOW() - INTERVAL 7 DAY`를 보는데 데이터는 고정 기준일로 만들어졌다.
**하루 지날 때마다 이 API가 저절로 빨라진다** — 기준일 +4일에 28% → 13.4%, +7일에 0%.
`verify.sql` §12가 벌어진 일수를 찍어 잡아낸다. 80만 행 41MB라 수 초면 된다.

### 3. think time이 없으면 VU 수와 사용자 수가 어긋난다

대기 없이 돌리면 VU 1명이 쉬지 않고 요청을 쏴서 실제 사용자 여러 명분이 된다.
그러면 위 역산(50명)과 연결되지 않아 "동시 사용자 N명"이라고 말할 수 없다.

### 4. VU 고정은 느린 응답을 과소 집계한다 (coordinated omission)

서버가 느려지면 VU가 응답을 기다리느라 요청을 덜 보낸다. **느린 응답이 지연시간 분포에
실제보다 적게 실린다.** 그래서 결과 표는 p95와 **달성 처리량**을 함께 찍는다 —
달성률이 낮으면 p95는 낙관적인 값이다.

### 5. 램프업을 집계에 넣으면 p95가 흐려진다

워밍업(`ramping-vus`)과 판정(`constant-vus`)을 **별도 시나리오로 나누고** `phase` 태그로
구분한다. 임계값과 결과 표는 `phase:steady`만 본다.

## 측정 전 점검

- [ ] `search_history` 재적재 (위 함정 2)
- [ ] RDS 스토리지 타입·IOPS — `gp2` 20GB면 기준 60 IOPS다. 여기 막히면 재는 것이 쿼리가 아니라 스토리지가 된다
- [ ] RDS `max_connections`
- [ ] EB 환경 속성의 `clock.fixed-date` 실제 값 (`application-prod.properties`에는 빈 값이다)
- [ ] 버퍼 풀 적중률 — 측정 직전·직후 `Innodb_buffer_pool_reads` / `..._read_requests`
- [ ] `GET /health/db` 직접 호출 — **EB 헬스 Green을 믿지 마라.** 전면 404 상태에서도 Green이 뜬다

## 범위 밖

- **WRITE 전 계열** — 부하 중 데이터가 변하면 반복 측정이 오염된다
- **동시 결제 정합성 검증** — 계획 §6이 요구하지만 WRITE 제외의 결과로 하지 않는다. 명시적 미실시
- **용량 · breakpoint 측정** — 시간 제약으로 뺐다. 계획 §3의 7단계가 갖고 있다
```

- [ ] **Step 3: 마크다운이 깨지지 않았는지 훑는다**

Run: `grep -n '^#' scripts/perf-k6/README.md`
Expected: 제목 계층이 `#` → `##` → `###` 순서로만 내려가고, `# 4단계` 절이 보인다

- [ ] **Step 4: 커밋**

```bash
git add scripts/perf-k6/README.md
git commit -m "docs: perf-k6 README에 4단계 부하 테스트를 추가한다

baseline.js와 load.js가 각각 어떤 질문에 답하는지 표로 갈랐다.
동시 사용자 100명의 역산 근거와 함정 다섯 개를 남긴다.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 7: PR 생성

**Files:** 없음 (git 작업만)

- [ ] **Step 1: 전체 파일이 의도대로 들어갔는지 확인한다**

Run: `git diff --stat origin/main -- scripts/perf-k6/`
Expected:
```
scripts/perf-k6/README.md                 | +NNN
scripts/perf-k6/extract-active-users.sh   | +NN (신규)
scripts/perf-k6/load.js                   | +NNN (신규)
```
CSV·summary 산출물이 목록에 없어야 한다.

- [ ] **Step 2: push**

```bash
git push -u origin perf/load-test-4th-stage
```

- [ ] **Step 3: 이슈를 만든다**

> 명령이 이슈 URL을 출력한다. 끝의 숫자가 이슈 번호다 — **Step 4에서 `#N` 자리에 그 숫자를 넣는다.**
> 예: `https://github.com/heartbeat-kb-town/fitwallet-backend/issues/253` → `253`

```bash
gh issue create --repo heartbeat-kb-town/fitwallet-backend \
  --title "[TASK] 4단계 1차 부하 테스트 스크립트를 만든다" \
  --label "🛠️ 작업" --label "🧰 인프라" --label "➖ 보통" \
  --body "$(cat <<'EOF'
## 작업 내용

동시 사용자 100명을 5분간 걸어 엔드포인트 19개의 SLO 통과/실패를 판정하는 k6 스크립트를 만든다.

설계 정본: 노션 「SLI/SLO 정의 · 1차 부하 테스트 설계 (4단계)」

## 작업 목표

3단계 baseline(1 VU)이 "병목이 어디고 왜인가"를 규명했다. 4단계는 "우리 규모의 2배에서 서비스 가능한가"에 답한다.

## 세부 작업 목록

- [ ] `extract-active-users.sh` — RDS에서 활성 유저 목록을 CSV로 추출
- [ ] `load.js` — 여정 정의 · 시나리오 옵션 · setup · 실행 · 결과 표
- [ ] `README.md` — 4단계 절 추가

## 완료 조건

- `k6 inspect load.js`가 임계값 19개를 만든다
- 스모크 실행(VU 2, 40초)에서 19행 결과 표가 나온다
- 유저 목록 CSV와 결과 산출물이 커밋되지 않는다
EOF
)"
```

- [ ] **Step 4: PR을 만든다 (base는 `main`)**

> **아래 두 곳의 `#N`을 Step 3에서 받은 실제 이슈 번호로 바꾼다** — 제목의 `[#N]`과 본문의 `closes #N`.
> 성능 작업이므로 **base는 `develop`이 아니라 `main`**이다(분기점과 같아야 한다).

```bash
gh pr create --repo heartbeat-kb-town/fitwallet-backend --base main \
  --title "[#N] perf: 4단계 1차 부하 테스트 스크립트를 만든다" \
  --body "$(cat <<'EOF'
## 관련 이슈

closes #N

> ⚠️ `main`은 기본 브랜치가 아니라 `closes`가 자동으로 걸리지 않는다. 머지 후 수동으로 닫는다.

## 작업 내용

동시 사용자 100명 · 5분 부하로 엔드포인트 19개의 SLO를 판정하는 k6 스크립트다.

- `extract-active-users.sh` — RDS에서 활성 유저 `login_id`를 CSV로 뽑는다
- `load.js` — 유저 여정 1개(READ 19호출 + 화면 사이 think time)를 VU 100개로 돌린다
- `README.md` — 4단계 절과 함정 5개

### 설계에서 갈린 지점

| | 결정 | 이유 |
|---|---|---|
| 목표 부하 | 동시 사용자 100명 | 300 TPS는 근거가 없다. 역산값 50명의 2배로, 커넥션 풀 포화 예측(91명)을 넘긴다 |
| 부하 모델 | VU 고정 | 도착률 고정은 목표 TPS가 있어야 의미가 있는데 그 목표를 버렸다 |
| WRITE | 전부 제외 | 부하 중 데이터가 변하면 반복 측정이 오염된다 |
| 유저 | VU마다 다르게 | 한 명으로 쏘면 버퍼 풀 적중률과 거래 편차가 둘 다 왜곡된다 |

## 변경 유형

- [ ] 새 기능
- [ ] 버그 수정
- [x] 그 외 (성능 측정 도구)

## 체크리스트

- [x] `k6 inspect`가 임계값 19개를 만든다
- [x] 스모크 실행에서 19행 결과 표가 나온다
- [x] CSV·summary 산출물이 커밋되지 않는다
- [x] 앱 코드를 건드리지 않는다 (`scripts/` 아래만)

## 리뷰어에게

**측정 전에 확인해야 할 것이 세 가지 있다** (README「측정 전 점검」).

1. `search_history` 재적재 — `store/keywords`가 `NOW() - INTERVAL 7 DAY`를 봐서 **하루 지날 때마다 저절로 빨라진다**
2. RDS 스토리지 IOPS — `gp2` 20GB면 60 IOPS다. 여기 막히면 재는 것이 쿼리가 아니라 스토리지가 된다
3. `clock.fixed-date`의 EB 환경 속성 실제 값

그리고 **측정 진행 중에는 어떤 PR도 머지하지 않는다** — `main` 머지가 곧 운영 배포다.
EOF
)"
```

---

## 실행 순서 메모 (구현과 별개)

이 계획은 **스크립트를 만드는 것**까지다. 실제 측정은 그 뒤다.

1. **3단계 baseline 재측정** — 계획 §3이 3↔4를 뒤집은 이유가 "부하 증가분을 경합 탓으로 돌리려면 부하 없는 기준선이 필요하다"인데, 지금 그 기준선이 데이터 재산정으로 무효다. `baseline.js`는 이미 있으므로 한 번 돌리면 된다
2. 측정 전 점검 (README)
3. `extract-active-users.sh` 실행
4. `load.js` 실행 (6분)
5. 1차 리포트 작성 (노션 신규 문서)
6. 부하 조건에서 `store/keywords` 재판정 → 5단계 개선 우선순위 확정
