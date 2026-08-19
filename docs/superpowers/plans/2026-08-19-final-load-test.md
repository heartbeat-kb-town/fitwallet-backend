# 7단계 최종 부하 테스트 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** k6 부하 테스트의 고정 데이터셋(강남역·스타벅스·카페/디저트·단일 `storeId`)을 실사용 분포로 바꾸고, 개선 전 버전으로 되돌려 같은 하네스로 다시 잰 뒤 최신 버전에서 최종 측정해 5단계 개선 폭을 보고한다.

**Architecture:** 추출 스크립트가 운영 RDS에서 시나리오 CSV 두 개를 뽑는다(판정용 2,000행 실빈도 · 진단용 40행 층화). k6 두 스크립트는 하드코딩된 네 값을 CSV에서 읽고, VU가 세션마다 결정적 인덱스로 조합을 갈아 끼운다. 난수를 쓰지 않으므로 개선 전·후가 같은 조합 순서를 밟는 것이 구조적으로 보장된다. 측정은 배포 2회 사이에 4회(1 VU · 100 VU × 전/후) 돈다.

**Tech Stack:** k6 (JS, ES2015 서브셋 · `SharedArray` · `handleSummary`) · Python 3.11(추출) · MySQL 8.4(운영 RDS) · AWS CLI(EB 배포 전환) · bash

**Spec:** `docs/superpowers/specs/2026-08-19-final-load-test-design.md`

## Global Constraints

- **브랜치는 `perf/final-load-test-harness`** (`main`에서 분기). 성능 작업이므로 PR base도 `main`이다(AGENTS.md §14)
- **커밋 트레일러**: `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`
- **측정 대상은 운영 EB `fitwallet-prod`** — 별도 스테이징이 없다. 실사용자 0명이라 무해하다
- **운영 RDS 접속**: 호스트 `fitwallet-db.c1g6w2em8fdg.ap-northeast-2.rds.amazonaws.com:3306`, DB `fitwallet`. 자격증명은 **EB 환경 속성 `DB_USERNAME`/`DB_PASSWORD`**에만 있다(저장소 `load.sh` 기본값은 로컬용이라 운영에서 거절된다). 자격증명을 파일·로그·커밋에 남기지 않는다
- **`scripts/perf-k6/results/`는 gitignore다.** 측정 결과를 커밋하지 않는다. 수치의 정본은 노션
- **CSV 두 개(`scenarios-load.csv` · `scenarios-baseline.csv`)는 커밋한다** — 두 측정이 같은 표본을 봤다는 근거가 파일로 남아야 한다
- **Phase B(측정) 진행 중에는 어떤 PR도 머지하지 않는다**(4단계 설계 §8). PR #270 머지는 Task 8의 계획된 단계라 예외
- **앱 시계가 `clock.fixed-date=2026-07-24`로 고정**돼 있다. `YEAR_MONTH`는 `2026-07`을 유지한다

---

## File Structure

| 파일 | 상태 | 책임 |
|---|---|---|
| `scripts/perf-k6/extract-scenarios.py` | 생성 | 운영 RDS에서 시나리오 CSV 두 개를 만든다. 격자 집계·희소 앵커 선정·검증·층화까지 전부 여기 |
| `scripts/perf-k6/scenarios-load.csv` | 생성(커밋) | 판정용 2,000행. `lat,lng,densityTier,keyword,categoryId` |
| `scripts/perf-k6/scenarios-baseline.csv` | 생성(커밋) | 진단용 40행(예열 10 + 측정 30). 같은 열 |
| `scripts/perf-k6/keyword-selectivity.csv` | 생성(커밋) | 검색어 305개의 전국 매칭 수. 층화의 근거이자 보고서 재료 |
| `scripts/perf-k6/load.js` | 수정 | 고정값 제거 → CSV 조합 + 밀도 태그 + `storeId` 인과 |
| `scripts/perf-k6/baseline.js` | 수정 | 고정값 제거 → CSV 조합 + 밀도 태그 |
| `scripts/perf-k6/README.md` | 수정 | 새 파일과 함정을 문서에 반영 |

**`densityBucket`이 두 스크립트에 중복되는 것은 의도다.** `baseline.js`와 `load.js`는 이미
`login`·`authHeaders`·`ms`·`fire`를 각자 갖고 있다(실측). 두 스크립트가 EC2에 따로 올라가
독립 실행되므로 공용 모듈을 만들면 전송 목록에 의존이 하나 는다. 저장소의 기존 방식을 따른다.

**왜 Python인가** — 격자 집계에서 "매장이 있는 격자의 빈 이웃"을 찾고 후보를 검증하고 층화까지 하는 로직이 bash로 쓰기에 크다. 저장소는 이미 `scripts/perf-data/`에서 Python을 쓴다.

**DB 접근 방식** — Python이 `mysql` 클라이언트를 subprocess로 부른다. 드라이버 의존성을 새로 넣지 않기 위해서다. 기본 명령은 이 저장소에서 실제로 동작이 확인된 `docker exec -i fitwallet-mysql-perf mysql`이고, `MYSQL_CMD` 환경변수로 갈아끼울 수 있다(k6 EC2에서 돌릴 때는 `mysql`).

---

## Phase A — 하네스 (코드)

### Task 1: 추출 스크립트 — 접속과 검색어 선택도

**Files:**
- Create: `scripts/perf-k6/extract-scenarios.py`
- Create(출력): `scripts/perf-k6/keyword-selectivity.csv`

**Interfaces:**
- Produces: `run_sql(sql: str) -> list[list[str]]` — 탭 구분 결과를 행 리스트로. 헤더 없음
- Produces: `keyword-selectivity.csv` — `keyword,matchCount` 305행

- [ ] **Step 1: 스크립트 뼈대와 접속 계층을 쓴다**

```python
#!/usr/bin/env python3
"""운영 RDS에서 k6 시나리오 CSV를 만든다.

    scripts/perf-k6/extract-scenarios.py

⚠️ 측정 대상이 운영 RDS이므로 시나리오도 운영에서 뽑는다. 로컬 perf DB(3308)와 행 수가
   같아 보여도 재적재 이력이 달라 동일성을 보증할 수 없다.

자격증명은 EB 환경 속성에서 그때그때 꺼내 쓰고 어디에도 남기지 않는다.
"""
import csv
import json
import os
import pathlib
import random
import subprocess
import sys

HERE = pathlib.Path(__file__).parent
RDS_HOST = os.environ.get(
    "PERF_DB_HOST", "fitwallet-db.c1g6w2em8fdg.ap-northeast-2.rds.amazonaws.com")
RDS_PORT = os.environ.get("PERF_DB_PORT", "3306")
DB_NAME = os.environ.get("PERF_DB_NAME", "fitwallet")

# 드라이버 의존성을 새로 넣지 않으려고 mysql 클라이언트를 subprocess로 부른다.
# 로컬에는 mysql 바이너리가 없어 perf 컨테이너의 것을 빌려 쓴다(네트워크는 호스트 NAT로 나간다).
MYSQL_CMD = os.environ.get(
    "MYSQL_CMD", "docker exec -i fitwallet-mysql-perf mysql").split()


def eb_credentials():
    """EB 환경 속성에서 DB 접속 계정을 꺼낸다. 저장소에는 운영 자격증명이 없다."""
    out = subprocess.run(
        ["aws", "elasticbeanstalk", "describe-configuration-settings",
         "--application-name", "fitwallet-backend",
         "--environment-name", "fitwallet-prod",
         "--region", "ap-northeast-2",
         "--query", "ConfigurationSettings[0].OptionSettings"
                    "[?Namespace=='aws:elasticbeanstalk:application:environment']",
         "--output", "json"],
        check=True, capture_output=True, text=True).stdout
    props = {o["OptionName"]: o.get("Value", "") for o in json.loads(out)}
    return props["DB_USERNAME"], props["DB_PASSWORD"]


USER, PASSWORD = eb_credentials()


def run_sql(sql):
    """SQL을 실행하고 탭 구분 결과를 행 리스트로 돌려준다. 헤더는 없다(-N)."""
    env = dict(os.environ, MYSQL_PWD=PASSWORD)
    cmd = MYSQL_CMD + [
        "-h", RDS_HOST, "-P", RDS_PORT, "-u", USER,
        "--default-character-set=utf8mb4", "--connect-timeout=15",
        "-N", "-B", DB_NAME]
    # docker exec에 -e로 넘겨야 컨테이너 안에서 MYSQL_PWD가 보인다.
    if cmd[0] == "docker":
        cmd = cmd[:3] + ["-e", f"MYSQL_PWD={PASSWORD}"] + cmd[3:]
    res = subprocess.run(cmd, input=sql, env=env, capture_output=True, text=True)
    if res.returncode != 0:
        sys.exit(f"SQL 실패: {res.stderr.strip()}")
    return [line.split("\t") for line in res.stdout.splitlines() if line.strip()]
```

- [ ] **Step 2: 접속만 검증한다**

Run:
```bash
python3 - <<'PY'
import sys; sys.path.insert(0, "scripts/perf-k6")
import importlib.util
spec = importlib.util.spec_from_file_location("es", "scripts/perf-k6/extract-scenarios.py")
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
print(m.run_sql("SELECT COUNT(*) FROM store;"))
PY
```
Expected: `[['2725562']]`

- [ ] **Step 3: 검색어 선택도를 뽑는 함수를 더한다**

전국 매칭 수는 층화의 축이고 보고서 재료이기도 하다. 305개를 한 번만 재고 파일로 남긴다.

```python
def keyword_selectivity():
    """검색어별 전국 LIKE 매칭 수. 305개 × 풀스캔이라 3~4분 걸린다 — 한 번만 재고 파일로 남긴다.

    FULLTEXT(V14)가 아직 없는 시점에 재므로 매 건이 272만 행 풀스캔이다. 그래도 여기서
    재야 하는 이유는, 이 값이 #270 라우팅 임계값 13,000의 양쪽을 층화 표본에 넣는 기준이기
    때문이다. V14 배포 후에 재면 값은 같지만 개선 전 측정이 이미 끝난 뒤가 된다.
    """
    cache = HERE / "keyword-selectivity.csv"
    if cache.exists():
        with cache.open(encoding="utf-8") as f:
            return {r["keyword"]: int(r["matchCount"]) for r in csv.DictReader(f)}

    keywords = [r[0] for r in run_sql(
        "SELECT DISTINCT keyword FROM search_history "
        "WHERE CHAR_LENGTH(keyword) >= 2 ORDER BY keyword;")]
    print(f"검색어 {len(keywords)}개의 전국 매칭 수를 잰다 (풀스캔이라 3~4분)", flush=True)

    # 한 번의 접속으로 전부 센다. 305회 왕복을 피하려고 UNION ALL로 묶는다.
    #
    # ⚠️ LIKE 패턴에 사용자 입력이 들어가므로 %와 _를 이스케이프한다. 검색어에 %가 있으면
    #    "아무거나"로 해석돼 매칭 수가 폭증하고 층화가 조용히 틀어진다.
    def esc(k):
        return k.replace("\\", "\\\\").replace("'", "''").replace("%", "\\%").replace("_", "\\_")

    # 305개를 한 문장으로 묶으면 3~4분짜리 쿼리가 되어 net_write_timeout(기본 60초)에 걸린다.
    # 25개씩 끊으면 배치 하나가 20초 안쪽이다.
    sel = {}
    for i in range(0, len(keywords), 25):
        batch = keywords[i:i + 25]
        parts = [f"SELECT '{esc(k)}' k, COUNT(*) n FROM store "
                 f"WHERE store_name LIKE '%{esc(k)}%'" for k in batch]
        for r in run_sql(" UNION ALL ".join(parts) + ";"):
            sel[r[0]] = int(r[1])
        print(f"  {len(sel)}/{len(keywords)}", flush=True)

    with cache.open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["keyword", "matchCount"])
        for k in keywords:
            w.writerow([k, sel.get(k, 0)])
    print(f"저장: {cache} — {len(sel)}개")
    return sel
```

- [ ] **Step 4: 선택도를 실제로 뽑고 분포를 확인한다**

Run:
```bash
python3 scripts/perf-k6/extract-scenarios.py --only-selectivity
```
(아래 Step 5의 `main`이 이 플래그를 받는다.)

Expected: `keyword-selectivity.csv` 306줄(헤더 1 + 305), 그리고 아래가 전부 1 이상이어야 한다 —
층화의 세 구간이 비면 표본을 못 만든다.

```bash
python3 - <<'PY'
import csv
rows = list(csv.DictReader(open("scripts/perf-k6/keyword-selectivity.csv", encoding="utf-8")))
n = [int(r["matchCount"]) for r in rows]
lo = sum(1 for x in n if x < 1000)
mid = sum(1 for x in n if 1000 <= x <= 13000)
hi = sum(1 for x in n if x > 13000)
print(f"총 {len(n)}개 — 저 {lo} / 중 {mid} / 고 {hi}, 최대 {max(n)}")
assert lo and mid and hi, "세 구간 중 빈 곳이 있다 — 층화 불가"
print("OK")
PY
```

- [ ] **Step 5: `prod-sql.sh`를 만든다 — 런북 전체가 이걸 쓴다**

Phase B가 운영 RDS에 SQL을 여러 번 쏜다. 매번 자격증명 조회를 손으로 붙이면 실수가 난다.

```bash
cat > scripts/perf-k6/prod-sql.sh <<'EOF'
#!/usr/bin/env bash
#
# 운영 RDS에 SQL을 실행한다. 자격증명은 EB 환경 속성에서 그때그때 꺼내 쓰고 어디에도 남기지 않는다.
#
#   scripts/perf-k6/prod-sql.sh -e "SELECT COUNT(*) FROM store;"
#   scripts/perf-k6/prod-sql.sh < some.sql
#
# ⚠️ 저장소의 load.sh 기본 계정(fitwallet/fitwallet1234)은 로컬 전용이다. 운영은 거절한다.
# ⚠️ 로컬에 mysql 바이너리가 없어 perf 컨테이너의 것을 빌려 쓴다. MYSQL_CMD로 갈아끼울 수 있다.
set -euo pipefail
RDS="${PERF_DB_HOST:-fitwallet-db.c1g6w2em8fdg.ap-northeast-2.rds.amazonaws.com}"
CFG=$(aws elasticbeanstalk describe-configuration-settings \
        --application-name fitwallet-backend --environment-name fitwallet-prod \
        --region ap-northeast-2 \
        --query "ConfigurationSettings[0].OptionSettings[?Namespace=='aws:elasticbeanstalk:application:environment']" \
        --output json)
U=$(echo "$CFG" | python3 -c "import sys,json;d=json.load(sys.stdin);print(next(o.get('Value','') for o in d if o['OptionName']=='DB_USERNAME'))")
P=$(echo "$CFG" | python3 -c "import sys,json;d=json.load(sys.stdin);print(next(o.get('Value','') for o in d if o['OptionName']=='DB_PASSWORD'))")
docker exec -i -e MYSQL_PWD="$P" fitwallet-mysql-perf \
  mysql -h "$RDS" -P 3306 -u "$U" --default-character-set=utf8mb4 --connect-timeout=15 fitwallet "$@" \
  2>&1 | grep -v "Warning"
EOF
chmod +x scripts/perf-k6/prod-sql.sh
scripts/perf-k6/prod-sql.sh -N -e "SELECT COUNT(*) FROM store;"
```
Expected: `2725562`

- [ ] **Step 6: `main`과 인자 처리를 붙이고 커밋한다**

```python
def main():
    only = sys.argv[1] if len(sys.argv) > 1 else ""
    sel = keyword_selectivity()
    if only == "--only-selectivity":
        return
    # Task 2·3에서 이어 붙인다.
    print(f"선택도 {len(sel)}개 확보")


if __name__ == "__main__":
    main()
```

```bash
chmod +x scripts/perf-k6/extract-scenarios.py
git add scripts/perf-k6/extract-scenarios.py scripts/perf-k6/keyword-selectivity.csv scripts/perf-k6/prod-sql.sh
git commit -m "perf: 시나리오 추출 스크립트와 검색어 선택도 실측을 더한다

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: 추출 스크립트 — 격자 밀도와 희소 앵커

**Files:**
- Modify: `scripts/perf-k6/extract-scenarios.py`

**Interfaces:**
- Consumes: `run_sql`(Task 1)
- Produces: `grid_counts() -> dict[(float,float), int]` — `0.01°` 격자별 매장 수
- Produces: `density_tier(grid, lat, lng) -> int` — `1~7`(7분위수). 희소 앵커는 호출자가 `0`을 준다
- Produces: `sparse_anchors(grid, n) -> list[(float, float)]` — 1km 안 0건이 **검증된** 좌표

- [ ] **Step 1: 격자 집계와 분위수 경계를 더한다**

```python
GRID = 0.01  # 약 1.1km(위도). 밀도 단계와 앵커 탐색의 해상도다.


def grid_counts():
    """0.01° 격자별 매장 수. 매장이 0인 격자는 애초에 결과에 없다 — 앵커 탐색이 그 성질을 쓴다."""
    rows = run_sql(
        "SELECT ROUND(latitude, 2), ROUND(longitude, 2), COUNT(*) "
        "FROM store WHERE latitude IS NOT NULL GROUP BY 1, 2;")
    return {(float(a), float(b)): int(c) for a, b, c in rows}


def tier_bounds(grid):
    """격자 매장 수의 7분위수 경계. 단계 1(가장 희소)~7(가장 밀집)."""
    counts = sorted(grid.values())
    return [counts[int(len(counts) * i / 7)] for i in range(1, 7)]


def density_tier(bounds, lat, lng, grid):
    """좌표가 속한 격자의 매장 수로 1~7 부여."""
    n = grid.get((round(lat, 2), round(lng, 2)), 0)
    tier = 1
    for b in bounds:
        if n >= b:
            tier += 1
    return tier
```

- [ ] **Step 2: 희소 앵커 선정과 검증을 더한다**

```python
# 1km 사각형 반변. 37.5°N 기준 위도 0.00899° · 경도 0.01133°.
LAT_1KM, LNG_1KM = 0.00899, 0.01133

# 5단계 재측정에서 이미 1km 안 0건으로 확인된 좌표(지리산). 시드 앵커로 반드시 포함한다.
SEED_ANCHOR = (35.3370, 127.7306)


def sparse_anchors(grid, want):
    """1km 안에 매장이 0건인 좌표를 want개 찾는다.

    **매장이 있는 격자의 빈 이웃**을 후보로 삼는다. 인접 격자에 가맹점이 있다는 것은 사람이
    사는 곳이라는 뜻이라, 바다나 국토 밖으로 새지 않는 land proxy가 된다. 무작위 좌표를
    뿌리면 대부분 바다에 떨어져 "사용자가 거기 있나"라는 반론을 받는다.

    후보마다 1km 사각형 count를 **실제로 검증**한다 — 격자는 근사라 0이라고 단정할 수 없다.
    """
    populated = set(grid)
    candidates = []
    for (gla, glo) in populated:
        for dla in (-2, -1, 0, 1, 2):
            for dlo in (-2, -1, 0, 1, 2):
                cell = (round(gla + dla * GRID, 2), round(glo + dlo * GRID, 2))
                if cell not in populated:
                    candidates.append(cell)
    # 한 지역에 몰리지 않게 섞고, 검증 비용을 아끼려 넉넉히만 본다.
    rnd = random.Random(20260819)
    candidates = sorted(set(candidates))
    rnd.shuffle(candidates)

    found = [SEED_ANCHOR]
    for (la, lo) in candidates:
        if len(found) >= want:
            break
        # 이미 고른 앵커와 5km 안에 붙어 있으면 건너뛴다 — 지리적으로 흩는다.
        if any(abs(la - a) < 0.05 and abs(lo - b) < 0.05 for a, b in found):
            continue
        n = int(run_sql(
            f"SELECT COUNT(*) FROM store WHERE latitude BETWEEN {la - LAT_1KM} AND {la + LAT_1KM} "
            f"AND longitude BETWEEN {lo - LNG_1KM} AND {lo + LNG_1KM};")[0][0])
        if n == 0:
            found.append((la, lo))
    if len(found) < want:
        sys.exit(f"희소 앵커를 {want}개 못 찾았다({len(found)}개). 격자 반경을 넓혀라.")
    return found
```

- [ ] **Step 3: 시드 앵커가 정말 1km 0건인지부터 검증한다**

Run:
```bash
python3 - <<'PY'
import importlib.util
spec = importlib.util.spec_from_file_location("es", "scripts/perf-k6/extract-scenarios.py")
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
la, lo = m.SEED_ANCHOR
n = m.run_sql(f"SELECT COUNT(*) FROM store WHERE latitude BETWEEN {la-m.LAT_1KM} AND {la+m.LAT_1KM} "
              f"AND longitude BETWEEN {lo-m.LNG_1KM} AND {lo+m.LNG_1KM};")[0][0]
print("시드 앵커 1km 안 매장 수:", n)
assert n == "0", "시드 앵커가 희소하지 않다 — 스펙의 전제가 깨졌다"
print("OK")
PY
```
Expected: `시드 앵커 1km 안 매장 수: 0` 다음 `OK`

- [ ] **Step 4: 앵커 6개를 실제로 찾고 흩어져 있는지 본다**

Run:
```bash
python3 - <<'PY'
import importlib.util
spec = importlib.util.spec_from_file_location("es", "scripts/perf-k6/extract-scenarios.py")
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
g = m.grid_counts()
print("격자 수:", len(g), "· 7분위 경계:", m.tier_bounds(g))
a = m.sparse_anchors(g, 6)
for la, lo in a:
    print(f"  {la:.4f}, {lo:.4f}")
assert len(a) == 6
lat_spread = max(x for x, _ in a) - min(x for x, _ in a)
assert lat_spread > 0.5, "앵커가 한 지역에 몰렸다"
print("OK")
PY
```
Expected: 격자 6개 좌표가 출력되고 위도 폭이 0.5° 넘음, 마지막에 `OK`

- [ ] **Step 5: 커밋**

```bash
git add scripts/perf-k6/extract-scenarios.py
git commit -m "perf: 격자 밀도 단계와 희소 앵커 선정을 더한다

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: 추출 스크립트 — CSV 두 개 생성

**Files:**
- Modify: `scripts/perf-k6/extract-scenarios.py`
- Create(출력): `scripts/perf-k6/scenarios-load.csv`, `scripts/perf-k6/scenarios-baseline.csv`

**Interfaces:**
- Consumes: `run_sql` · `grid_counts` · `tier_bounds` · `density_tier` · `sparse_anchors` · `keyword_selectivity`
- Produces: CSV 두 개. 헤더 `lat,lng,densityTier,keyword,categoryId`
  - `densityTier`: `0`=희소 앵커(1km 0건) · `1~7`=격자 7분위수
  - `categoryId`: `1~6` (`7`=기타는 프론트 칩에 없어 제외)

- [ ] **Step 1: 판정용 CSV를 만드는 함수를 더한다**

```python
LOAD_ROWS = 2000
SPARSE_SHARE = 0.03   # p95(상위 5%)보다 낮춰야 주입 비율이 SLO 판정을 결정하지 않는다
JITTER = 0.0045       # 약 500m. 사용자가 가맹점 위에 서 있어 300m가 항상 차는 것만 푼다


def build_load_csv(grid, bounds, anchors):
    rnd = random.Random(20260819)

    coords = [(float(a), float(b)) for a, b in run_sql(
        f"SELECT latitude, longitude FROM store WHERE latitude IS NOT NULL "
        f"ORDER BY RAND() LIMIT {LOAD_ROWS};")]
    # 카테고리는 좌표와 독립이다 — 사용자는 자기 위치와 무관하게 칩을 누른다.
    cats = [int(r[0]) for r in run_sql(
        f"SELECT category_id FROM store WHERE category_id <> 7 "
        f"ORDER BY RAND() LIMIT {LOAD_ROWS};")]
    keywords = [r[0] for r in run_sql(
        f"SELECT keyword FROM search_history WHERE CHAR_LENGTH(keyword) >= 2 "
        f"ORDER BY RAND() LIMIT {LOAD_ROWS};")]

    n_sparse = round(LOAD_ROWS * SPARSE_SHARE)
    rows = []
    for i in range(LOAD_ROWS):
        if i < n_sparse:
            la, lo = anchors[i % len(anchors)]
            tier = 0
        else:
            la, lo = coords[i]
            tier = density_tier(bounds, la, lo, grid)
        la += rnd.uniform(-JITTER, JITTER)
        lo += rnd.uniform(-JITTER, JITTER)
        rows.append([f"{la:.6f}", f"{lo:.6f}", tier, keywords[i], cats[i]])

    # 희소 앵커가 앞 60행에 몰려 있으면 VU 1~60이 5분 내내 희소만 밟는다.
    # 결정적 인덱스 순회와 맞물려 특정 VU에 편향이 고정되므로 반드시 섞는다.
    rnd.shuffle(rows)
    write_csv(HERE / "scenarios-load.csv", rows)


def write_csv(path, rows):
    with path.open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["lat", "lng", "densityTier", "keyword", "categoryId"])
        w.writerows(rows)
    print(f"저장: {path} — {len(rows)}행")
```

- [ ] **Step 2: 진단용 층화 CSV를 만드는 함수를 더한다**

```python
BASELINE_WARMUP = 10
BASELINE_ROWS = 30


def selectivity_band(n):
    """#270 라우팅 임계값 13,000의 양쪽이 표본에 반드시 들어가게 하는 구간."""
    if n < 1000:
        return "lo"
    if n <= 13000:
        return "mid"
    return "hi"


def build_baseline_csv(grid, bounds, anchors, sel):
    """밀도 8단계(0=희소 앵커, 1~7) × 선택도 3구간에서 고르게 뽑는다.

    예열 10행과 측정 30행을 나누는 이유는, 같은 행으로 예열하면 그 조합만 버퍼 풀에
    올라간 채 측정에 들어가기 때문이다.
    """
    rnd = random.Random(20260819)
    by_band = {"lo": [], "mid": [], "hi": []}
    for k, n in sel.items():
        by_band[selectivity_band(n)].append(k)
    for v in by_band.values():
        rnd.shuffle(v)

    # ⚠️ 좌표를 store에서 뽑으면 밀집 격자에만 몰려 희소 단계(1~2)가 빈다 — store 표본 자체가
    #    밀도 가중이기 때문이다. **격자를 단계별로 나눠 셀 중심을 쓴다.** 분위수의 정의상
    #    모든 단계에 셀이 존재하는 것이 보장된다. 판정용(build_load_csv)은 실빈도가 목적이라
    #    반대로 store 표본이 맞다 — 두 CSV의 목적이 다르므로 뽑는 방법도 다르다.
    by_tier = {t: [] for t in range(1, 8)}
    for (gla, glo) in grid:
        by_tier[density_tier(bounds, gla, glo, grid)].append((gla, glo))
    for v in by_tier.values():
        rnd.shuffle(v)
    by_tier[0] = list(anchors)

    cats = [int(r[0]) for r in run_sql(
        "SELECT category_id FROM store WHERE category_id <> 7 ORDER BY RAND() LIMIT 64;")]

    cells = [(t, b) for t in range(0, 8) for b in ("lo", "mid", "hi")]  # 24칸
    rows, idx = [], 0
    while len(rows) < BASELINE_WARMUP + BASELINE_ROWS:
        tier, band = cells[idx % len(cells)]
        pool = by_tier[tier]
        if not pool:
            sys.exit(f"밀도 {tier}단계에 좌표가 없다 — 표본 수를 늘려라.")
        la, lo = pool[(idx // len(cells)) % len(pool)]
        la += rnd.uniform(-JITTER, JITTER)
        lo += rnd.uniform(-JITTER, JITTER)
        kw = by_band[band][(idx // len(cells)) % len(by_band[band])]
        rows.append([f"{la:.6f}", f"{lo:.6f}", tier, kw, cats[idx % len(cats)]])
        idx += 1
    write_csv(HERE / "scenarios-baseline.csv", rows)
```

- [ ] **Step 3: `main`을 완성한다**

```python
def main():
    only = sys.argv[1] if len(sys.argv) > 1 else ""
    sel = keyword_selectivity()
    if only == "--only-selectivity":
        return
    grid = grid_counts()
    bounds = tier_bounds(grid)
    anchors = sparse_anchors(grid, 6)
    print(f"격자 {len(grid)}개 · 7분위 경계 {bounds} · 희소 앵커 {len(anchors)}개")
    build_load_csv(grid, bounds, anchors)
    build_baseline_csv(grid, bounds, anchors, sel)
```

- [ ] **Step 4: 실제로 뽑는다**

Run: `python3 scripts/perf-k6/extract-scenarios.py`

Expected: `scenarios-load.csv` 2,001줄 · `scenarios-baseline.csv` 41줄

- [ ] **Step 5: 산출물을 검증한다 — 조용히 틀리는 것을 여기서 끊는다**

Run:
```bash
python3 - <<'PY'
import csv, collections
L = list(csv.DictReader(open("scripts/perf-k6/scenarios-load.csv", encoding="utf-8")))
B = list(csv.DictReader(open("scripts/perf-k6/scenarios-baseline.csv", encoding="utf-8")))

assert len(L) == 2000, len(L)
assert len(B) == 40, len(B)

# ① 1글자 검색어가 섞이면 #270 배포 후 400이 나와 실패율이 오염된다
assert min(len(r["keyword"]) for r in L + B) >= 2, "1글자 검색어가 있다"

# ② 기타(7)는 프론트 칩에 없다
assert all(r["categoryId"] in "123456" for r in L + B), "categoryId 7이 있다"

# ③ 희소 앵커 3%
t = collections.Counter(r["densityTier"] for r in L)
assert t["0"] == 60, t
print("load 밀도 분포:", dict(sorted(t.items())))

# ④ 판정용은 고정이 아니어야 한다 — 이 계획의 존재 이유다
assert len({(r["lat"], r["lng"]) for r in L}) > 1500, "좌표가 다양하지 않다"
assert len({r["keyword"] for r in L}) > 200, "검색어가 다양하지 않다"

# ⑤ 진단용은 8단계 × 3구간을 덮어야 한다
tb = collections.Counter(r["densityTier"] for r in B)
assert len(tb) == 8, tb
print("baseline 밀도 분포:", dict(sorted(tb.items())))
print("OK")
PY
```
Expected: 분포 두 줄 뒤에 `OK`

- [ ] **Step 6: 커밋**

```bash
git add scripts/perf-k6/extract-scenarios.py scripts/perf-k6/scenarios-load.csv scripts/perf-k6/scenarios-baseline.csv
git commit -m "perf: 실사용 분포 시나리오 CSV를 뽑는다

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: `load.js` — 조합·밀도 태그·storeId 인과

**Files:**
- Modify: `scripts/perf-k6/load.js`

**Interfaces:**
- Consumes: `scenarios-load.csv`
- Produces: `http_req_duration{name:<ep>,phase:steady}`(기존) + `http_req_duration{name:store_search_*,density:<bucket>}`(신규)
  - `density` 버킷: `empty`(tier 0) · `sparse`(1~2) · `mid`(3~5) · `dense`(6~7)

- [ ] **Step 1: CSV 로드와 결정적 조합 배정을 더한다**

`const users = new SharedArray(...)` 바로 아래에 넣는다.

```js
const SCENARIO_FILE = __ENV.SCENARIO_FILE || './scenarios-load.csv';

/**
 * 시나리오 조합. 좌표·검색어·카테고리가 여기서 온다 — 예전에는 전부 상수였다.
 * SharedArray라 VU 전체가 메모리 한 벌을 공유한다.
 */
const scenarios = new SharedArray('scenarios', function () {
    const lines = open(SCENARIO_FILE).split('\n').map((s) => s.trim()).filter(Boolean);
    return lines.slice(1).map(function (line) {
        const c = line.split(',');
        return { lat: c[0], lng: c[1], tier: Number(c[2]), keyword: c[3], categoryId: c[4] };
    });
});

/** 밀도 단계(0~7)를 태그 버킷으로 줄인다. 태그 카디널리티를 낮게 유지한다. */
function densityBucket(tier) {
    if (tier === 0) return 'empty';   // 1km 안 0건 — 사다리를 10km까지 올리는 유일한 경로
    if (tier <= 2) return 'sparse';
    if (tier <= 5) return 'mid';
    return 'dense';
}

/**
 * 세션마다 조합을 갈아 끼운다. **난수를 쓰지 않는다** — 개선 전과 후가 같은 순서를 밟는 것이
 * 비교의 전제이고, 결정적 순회면 시드를 맞췄는지 확인할 필요조차 없다.
 *
 * VU 100개가 매 반복 서로 다른 100칸씩 전진한다. 2,000행이면 20반복까지 겹침이 없고
 * 판정 구간은 약 15반복이라 한 번도 재사용되지 않는다.
 *
 * 워밍업에 N/2 오프셋을 주는 이유: 같은 조합을 밟으면 판정 구간이 자기가 쓸 페이지를
 * 미리 캐시에 올린 상태로 시작한다.
 */
function pickScenario(isSteady) {
    const offset = isSteady ? 0 : Math.floor(scenarios.length / 2);
    const i = (offset + (__VU - 1) + exec.scenario.iterationInInstance * VUS) % scenarios.length;
    return scenarios[i];
}
```

- [ ] **Step 2: `SCREENS`의 고정값을 조합으로 바꾼다**

`검색` 화면과 `결제 전` 화면만 바뀐다. 나머지는 그대로 둔다.

```js
    {
        screen: '검색',
        calls: [
            { name: 'store_keywords',        slo: SLO_SEARCH, url: () => '/api/store/keywords' },
            { name: 'store_search_coords',   slo: SLO_SEARCH, geo: true,
              url: (u, sc) => `/api/store/search?latitude=${sc.lat}&longitude=${sc.lng}&radiusMeters=3000` },
            { name: 'store_search_keyword',  slo: SLO_SEARCH, geo: true,
              url: (u, sc) => `/api/store/search?keyword=${encodeURIComponent(sc.keyword)}&latitude=${sc.lat}&longitude=${sc.lng}` },
            { name: 'store_search_category', slo: SLO_SEARCH, geo: true,
              url: (u, sc) => `/api/store/search?categoryId=${sc.categoryId}&latitude=${sc.lat}&longitude=${sc.lng}&radiusMeters=3000` },
        ],
    },
    {
        screen: '결제 전',
        calls: [
            // storeId는 같은 세션의 좌표 검색 응답에서 이어받는다(fire()가 채운다).
            // 사용자의 실제 동선(검색 → 가맹점 선택 → 결제 전 혜택 조회)과 인과가 맞는다.
            { name: 'benefit_expected', slo: SLO_AGG,
              url: (u, sc) => `/api/benefit/expected?storeId=${sc.storeId || u.storeId}&amount=15000` },
        ],
    },
```

또한 `LAT`/`LNG` 상수의 주석을 폴백 프로브 전용임이 드러나게 고친다.

```js
/** setup()의 폴백 프로브 전용 좌표(강남역). 세션 좌표는 scenarios-load.csv에서 온다. */
const LAT = 37.4979;
const LNG = 127.0276;
```

- [ ] **Step 3: 밀도 서브메트릭을 실체화하는 더미 임계값을 더한다**

기존 `for (const c of ALL_CALLS) { thresholds[...] = ... }` 루프를 **아래로 교체한다**(추가가 아니다).

```js
/*
 * ⚠️ k6는 임계값에 선언되지 않은 태그 조합의 서브메트릭을 만들지 않는다.
 * handleSummary에서 밀도별 수치를 읽으려면 통과가 보장되는 임계값이라도 걸어야 한다.
 * 빠뜨리면 에러 없이 밀도 표가 통째로 비어 나온다.
 */
const DENSITY_BUCKETS = ['empty', 'sparse', 'mid', 'dense'];
for (const c of ALL_CALLS) {
    thresholds[`http_req_duration{name:${c.name},phase:steady}`] = [`p(95)<${c.slo}`];
    if (c.geo) {
        for (const b of DENSITY_BUCKETS) {
            thresholds[`http_req_duration{name:${c.name},phase:steady,density:${b}}`] = ['p(95)<999999'];
        }
    }
}
```

- [ ] **Step 4: `fire()`가 조합을 받고 `storeId`를 이어받게 고친다**

```js
function fire(call, u, sc, isSteady) {
    const headers = call.auth === false ? {} : { Authorization: `Bearer ${u.token}` };
    const tags = { name: call.name };
    if (call.geo) tags.density = densityBucket(sc.tier);

    const res = http.get(`${BASE_URL}${call.url(u, sc)}`, { headers, tags, timeout: '60s' });

    // 좌표 검색이 성공하면 그 결과의 첫 가맹점을 세션에 담는다. 뒤따르는 benefit_expected가 쓴다.
    // 0건이면 손대지 않아 setup()의 전역 폴백이 그대로 남는다 — 희소 좌표에서는 0건이 정상이다.
    if (call.name === 'store_search_coords' && res.status === 200) {
        const stores = res.json('data.stores') || [];
        if (stores.length > 0) sc.storeId = stores[0].storeId;
    }

    if (isSteady) steadyReqs.add(1);
    // ... (기존 status 0 / 5xx 처리는 그대로)
}
```

`default()`는 세션 시작에 조합을 뽑아 넘긴다.

```js
export default function (data) {
    const u = data.pool[(__VU - 1) % data.pool.length];
    const isSteady = exec.scenario.name === 'steady';
    // 조합은 세션당 하나다. 한 세션 안에서 좌표가 바뀌면 사용자가 순간이동하고
    // storeId 인과가 깨진다. SharedArray 원소는 읽기 전용이라 복사해서 쓴다.
    const base = pickScenario(isSteady);
    const sc = { lat: base.lat, lng: base.lng, tier: base.tier,
                 keyword: base.keyword, categoryId: base.categoryId, storeId: null };

    for (let i = 0; i < SCREENS.length; i++) {
        for (const call of SCREENS[i].calls) {
            fire(call, u, sc, isSteady);
        }
        if (i < SCREENS.length - 1) {
            sleep(THINK * (0.5 + Math.random()));
        }
    }
}
```

- [ ] **Step 5: `setup()`의 좌표 검증을 폴백 프로브 전용으로 좁힌다**

기존 메시지가 "좌표를 바꾸거나 반경을 넓혀야 한다"인데, 이제 세션 좌표는 희소해도 정상이다.
프로브 좌표(강남역)만 검증한다는 것이 드러나야 한다.

```js
    if (stores.length === 0) {
        throw new Error(
            '폴백 프로브 좌표(강남역)의 검색이 0건이다. 세션 좌표는 희소해도 정상이지만 '
            + '이 프로브는 benefit_expected의 폴백 storeId를 만드는 자리라 비면 안 된다.');
    }
```

- [ ] **Step 6: 밀도별 표를 `handleSummary`에 더한다**

```js
function buildDensityRows(data) {
    const rows = [];
    for (const c of ALL_CALLS) {
        if (!c.geo) continue;
        for (const b of DENSITY_BUCKETS) {
            const t = data.metrics[`http_req_duration{name:${c.name},phase:steady,density:${b}}`];
            if (!t || !t.values.count) continue;
            rows.push({ name: c.name, density: b, n: t.values.count,
                        med: t.values.med, p95: t.values['p(95)'], max: t.values.max });
        }
    }
    return rows;
}
```

`md` 배열의 마지막 표 뒤에 붙인다.

```js
        '',
        '## 밀도별 분해 (좌표 스코프 3종)',
        '',
        '> `empty`는 1km 안에 매장이 0건인 주입 좌표다(전체의 3%). 표본이 얇아 **p95를 말하지 않고**',
        '> 중앙값과 max로 읽는다. 정밀 판정은 1 VU 층화 표본이 담당한다.',
        '',
        '| 엔드포인트 | 밀도 | N | p50 | p95 | max |',
        '|---|---|---:|---:|---:|---:|',
        ...buildDensityRows(data).map((r) =>
            `| \`${r.name}\` | ${r.density} | ${r.n} | ${ms(r.med)} | ${ms(r.p95)} | ${ms(r.max)} |`),
```

그리고 `load-summary.json`에 `densityRows: buildDensityRows(data)`를 더한다.

- [ ] **Step 7: 문법을 검사하고 커밋한다**

Run: `k6 inspect scripts/perf-k6/load.js > /dev/null && echo OK`
Expected: `OK` (스크립트가 파싱되고 옵션이 해석된다. 실행하지 않는다.)

```bash
git add scripts/perf-k6/load.js
git commit -m "perf: 부하 시나리오의 고정 좌표·검색어·카테고리를 실사용 분포로 바꾼다

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: `baseline.js` — 조합·밀도 태그

**Files:**
- Modify: `scripts/perf-k6/baseline.js`

**Interfaces:**
- Consumes: `scenarios-baseline.csv`
- Produces: `ep_<name>` Trend(기존) + `ep_<name>{density:<bucket>}` 서브메트릭(신규, `geo` 엔드포인트만)

- [ ] **Step 1: CSV 로드와 조합 선택을 더한다**

```js
import { SharedArray } from 'k6/data';

const SCENARIO_FILE = __ENV.SCENARIO_FILE || './scenarios-baseline.csv';

/**
 * 진단용 층화 표본. 앞 WARMUP행은 예열 전용, 뒤 READ_ITERATIONS행이 측정용이다.
 * 같은 행으로 예열하면 그 조합만 버퍼 풀에 올라간 채 측정에 들어간다.
 */
const scenarios = new SharedArray('scenarios', function () {
    const lines = open(SCENARIO_FILE).split('\n').map((s) => s.trim()).filter(Boolean);
    return lines.slice(1).map(function (line) {
        const c = line.split(',');
        return { lat: c[0], lng: c[1], tier: Number(c[2]), keyword: c[3], categoryId: c[4] };
    });
});

function densityBucket(tier) {
    if (tier === 0) return 'empty';
    if (tier <= 2) return 'sparse';
    if (tier <= 5) return 'mid';
    return 'dense';
}

/** i번째 호출이 쓸 조합. 예열은 앞쪽, 측정은 WARMUP 이후를 밟는다. */
function scenarioFor(i, record) {
    const base = record ? WARMUP + i : i;
    return scenarios[base % scenarios.length];
}
```

- [ ] **Step 2: `READ_ENDPOINTS`의 고정값을 조합으로 바꾼다**

```js
    { name: 'store_search_coords',    method: 'GET', geo: true,
      url: (c, sc) => `/api/store/search?latitude=${sc.lat}&longitude=${sc.lng}&radiusMeters=3000` },
    { name: 'store_search_keyword',   method: 'GET', geo: true,
      url: (c, sc) => `/api/store/search?keyword=${encodeURIComponent(sc.keyword)}&latitude=${sc.lat}&longitude=${sc.lng}` },
    { name: 'store_search_category',  method: 'GET', geo: true,
      url: (c, sc) => `/api/store/search?categoryId=${sc.categoryId}&latitude=${sc.lat}&longitude=${sc.lng}&radiusMeters=3000` },
```

`benefit_expected`는 `ctx.storeId`를 그대로 쓴다 — baseline은 세션이 아니라 엔드포인트 단위로
돌아 검색 → 선택의 인과가 없고, `storeId`를 직접 받는 API라 좌표와 무관하기 때문이다.

- [ ] **Step 3: 밀도 서브메트릭용 임계값을 더한다**

기존 `thresholds: {}`를 아래로 바꾼다. 주석의 의도(판정하지 않는다)는 유지된다 —
전부 통과하는 값이라 중단시키지 않는다.

```js
/*
 * baseline은 판정이 아니라 관측이라 원래 threshold가 비어 있었다. 그런데 k6는 임계값에
 * 선언되지 않은 태그 조합의 서브메트릭을 만들지 않아, 밀도별 분해를 읽으려면 선언이 필요하다.
 * 전부 통과하는 값(p(95)<999999)이라 느린 엔드포인트에서 중단되지 않는다.
 */
const densityThresholds = {};
for (const ep of READ_ENDPOINTS) {
    if (!ep.geo) continue;
    for (const b of ['empty', 'sparse', 'mid', 'dense']) {
        densityThresholds[`ep_${ep.name}{density:${b}}`] = ['p(95)<999999'];
    }
}
```

`options`에서 `thresholds: {}` → `thresholds: densityThresholds`.

- [ ] **Step 4: `fire()`와 루프가 조합을 넘기게 고친다**

```js
function fire(ep, ctx, record, sc) {
    if (ep.prepare) ep.prepare(ctx);

    const token = ep.useWriteToken ? ctx.writeToken : ctx.readToken;
    const headers = ep.auth === false ? { 'Content-Type': 'application/json' } : authHeaders(token);
    const url = `${BASE_URL}${ep.url(ctx, sc)}`;
    const body = ep.body ? ep.body(ctx) : null;

    const params = { headers, tags: { name: ep.name }, timeout: '120s' };
    const res = ep.method === 'GET'
        ? http.get(url, params)
        : http.request(ep.method, url, body === null ? null : JSON.stringify(body), params);

    const ok = res.status >= 200 && res.status < 300;
    if (record) {
        // 밀도 태그는 커스텀 Trend에 단다. geo가 아닌 엔드포인트는 태그가 없다.
        trends[ep.name].add(res.timings.duration, ep.geo ? { density: densityBucket(sc.tier) } : {});
        errors[ep.name].add(!ok);
    }
    if (!ok && record) {
        console.warn(`[${ep.name}] HTTP ${res.status} — ${String(res.body).slice(0, 300)}`);
    }
    return ok;
}

export default function (ctx) {
    for (const ep of READ_ENDPOINTS) {
        for (let i = 0; i < WARMUP; i++) fire(ep, ctx, false, scenarioFor(i, false));
        for (let i = 0; i < READ_ITERATIONS; i++) fire(ep, ctx, true, scenarioFor(i, true));
        console.log(`[read ] ${ep.name} 완료 (warmup ${WARMUP} + 측정 ${READ_ITERATIONS})`);
    }

    for (const ep of WRITE_ENDPOINTS) {
        for (let i = 0; i < WRITE_ITERATIONS; i++) fire(ep, ctx, true, scenarios[0]);
        console.log(`[write] ${ep.name} 완료 (측정 ${WRITE_ITERATIONS})`);
    }
}
```

- [ ] **Step 5: 밀도별 표를 `handleSummary`에 더한다**

```js
function buildDensityTable(data) {
    const lines = [
        '| 엔드포인트 | 밀도 | N | p50 | max |',
        '|---|---|---:|---:|---:|',
    ];
    for (const ep of READ_ENDPOINTS) {
        if (!ep.geo) continue;
        for (const b of ['empty', 'sparse', 'mid', 'dense']) {
            const t = data.metrics[`ep_${ep.name}{density:${b}}`];
            if (!t || !t.values.count) continue;
            lines.push(`| \`${ep.name}\` | ${b} | ${t.values.count} | ${ms(t.values.med)} | ${ms(t.values.max)} |`);
        }
    }
    return lines.join('\n');
}
```

`md` 마지막에 붙인다.

```js
    const md = `${header}${table}\n\n## 밀도별 분해\n\n`
        + '> 층화 표본이라 칸당 N이 1~2다. **분포가 아니라 개별 조합의 실측치로 읽는다.**\n\n'
        + `${buildDensityTable(data)}\n`;
```

- [ ] **Step 6: 문법을 검사하고 커밋한다**

Run: `k6 inspect scripts/perf-k6/baseline.js > /dev/null && echo OK`
Expected: `OK`

```bash
git add scripts/perf-k6/baseline.js
git commit -m "perf: baseline을 밀도 층화 표본으로 재도록 고친다

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: k6 EC2 배포 · 스모크 · README

**Files:**
- Create: `scripts/perf-k6/k6ec2.sh`
- Modify: `scripts/perf-k6/README.md`

**Interfaces:**
- Produces: `k6ec2.sh push` — 스크립트·CSV를 k6 EC2 `/opt/perf`로 옮긴다
- Produces: `k6ec2.sh run <script.js> <결과이름> [env...]` — EC2에서 k6를 돌리고 결과 파일을 로컬 `results/`로 가져온다

> **k6는 반드시 k6 EC2(`i-05eb81746a575ca47`)에서 돌린다.** 1차 부하 테스트가 거기서 돌았고,
> 로컬에서 쏘면 인터넷 왕복이 끼어 **1차와도 before/after끼리도 조건이 어긋난다.**

- [ ] **Step 1: EC2가 SSM으로 닿는지 확인한다**

Run:
```bash
aws ssm describe-instance-information --region ap-northeast-2 \
  --filters "Key=InstanceIds,Values=i-05eb81746a575ca47" \
  --query 'InstanceInformationList[].{Ping:PingStatus,Agent:AgentVersion}'
```
Expected: `Ping=Online`

- [ ] **Step 2: 전송 스크립트를 만든다**

파일을 SSM heredoc으로 밀어넣던 방식은 `scenarios-load.csv`(약 120KB)에서 한도를 넘긴다.
S3에 올리고 **presigned URL**로 내려받는다 — EC2 역할에 `AmazonSSMManagedInstanceCore`밖에
없어 S3 권한이 없는데, presigned URL은 자격증명 없이 동작하므로 IAM을 건드리지 않아도 된다.

```bash
cat > scripts/perf-k6/k6ec2.sh <<'EOF'
#!/usr/bin/env bash
#
# k6 EC2에서 측정을 돌린다. 로컬에서 쏘면 인터넷 왕복이 끼어 1차와 조건이 달라진다.
#
#   scripts/perf-k6/k6ec2.sh push
#   scripts/perf-k6/k6ec2.sh run baseline.js baseline-before-20260819
#   scripts/perf-k6/k6ec2.sh run load.js load-before-20260819 VUS=100
#
# ⚠️ EC2 역할에는 S3 권한이 없다. presigned URL로 주고받는다 — IAM을 건드리지 않는 이유다.
set -euo pipefail
INSTANCE=i-05eb81746a575ca47
REGION=ap-northeast-2
BUCKET=elasticbeanstalk-ap-northeast-2-715975222399
PREFIX=perf-k6
HERE="$(cd "$(dirname "$0")" && pwd)"

ssm_run() {  # $1=설명 $2=쉘 명령
  local cid
  cid=$(aws ssm send-command --instance-ids "$INSTANCE" --region "$REGION" \
        --document-name AWS-RunShellScript --comment "$1" \
        --parameters "commands=[\"$2\"]" --timeout-seconds 3600 \
        --query 'Command.CommandId' --output text)
  echo "  SSM $cid — $1" >&2
  while :; do
    sleep 10
    local st
    st=$(aws ssm get-command-invocation --command-id "$cid" --instance-id "$INSTANCE" \
         --region "$REGION" --query 'Status' --output text 2>/dev/null || echo Pending)
    case "$st" in
      Success) break ;;
      Failed|Cancelled|TimedOut) 
        aws ssm get-command-invocation --command-id "$cid" --instance-id "$INSTANCE" \
          --region "$REGION" --query 'StandardErrorContent' --output text >&2
        exit 1 ;;
    esac
  done
  aws ssm get-command-invocation --command-id "$cid" --instance-id "$INSTANCE" \
    --region "$REGION" --query 'StandardOutputContent' --output text
}

case "${1:-}" in
  push)
    cmds=""
    for f in baseline.js load.js active-users.csv scenarios-load.csv scenarios-baseline.csv; do
      aws s3 cp "$HERE/$f" "s3://$BUCKET/$PREFIX/$f" --region "$REGION" >/dev/null
      url=$(aws s3 presign "s3://$BUCKET/$PREFIX/$f" --expires-in 3600 --region "$REGION")
      cmds+="curl -sfS -o /opt/perf/$f '$url' && "
    done
    ssm_run "push" "mkdir -p /opt/perf && ${cmds}ls -l /opt/perf"
    ;;
  run)
    script="$2"; name="$3"; shift 3
    envs="$*"
    base="${script%.js}-summary"
    # 결과는 SSM 표준출력으로 회수한다.
    #
    # ⚠️ presigned PUT을 쓰지 않는 이유: aws-cli 2.36.16의 `s3 presign`은 --http-method를
    #    모른다(실측 ParamValidation 에러). GET용 URL로 PUT하면 403이라 결과가 조용히 안 온다.
    #    EC2 역할에는 S3 권한이 없어 aws s3 cp도 못 쓴다.
    ssm_run "run $script" "cd /opt/perf && $envs k6 run $script 2>&1 | tail -80"
    mkdir -p "$HERE/results"
    for ext in md json; do
      # 파일마다 따로 받는다. 한 번에 받으면 SSM 출력 상한(24,000자)에 걸린다.
      out=$(ssm_run "fetch $base.$ext" "cat /opt/perf/$base.$ext")
      case "$out" in
        *"Output truncated"*|"")
          echo "회수 실패: $base.$ext 가 잘렸거나 비었다" >&2; exit 1 ;;
      esac
      printf '%s\n' "$out" > "$HERE/results/$name.$ext"
      echo "  받음: results/$name.$ext ($(wc -c < "$HERE/results/$name.$ext") bytes)" >&2
    done
    ;;
  *) echo "사용법: k6ec2.sh push | run <script.js> <결과이름> [ENV=V ...]" >&2; exit 1 ;;
esac
EOF
chmod +x scripts/perf-k6/k6ec2.sh
```

- [ ] **Step 3: 배포하고 EC2에 파일이 도착했는지 본다**

Run: `scripts/perf-k6/k6ec2.sh push`
Expected: `/opt/perf`의 `ls -l`에 5개 파일. `scenarios-load.csv`가 100KB 이상이어야 한다 —
0바이트면 presigned URL이 만료됐거나 S3 업로드가 실패한 것이다

- [ ] **Step 4: baseline 스모크 (N=5)**

Run: `scripts/perf-k6/k6ec2.sh run baseline.js smoke-baseline READ_ITERATIONS=5 WARMUP=2 WRITE_ITERATIONS=0`
Expected: 출력 끝에 `밀도별 분해` 표가 있고 `store_search_*` 3종에 버킷이 둘 이상 나온다.
**표가 비어 있으면 더미 임계값 선언이 빠진 것이다**(Task 5 Step 3)

- [ ] **Step 5: 조합이 실제로 갈리는지 서버 쪽에서 확인한다**

k6 로그로는 좌표가 바뀌었는지 안 보인다. 검색어는 기록되므로 DB에서 본다.

Run:
```bash
scripts/perf-k6/prod-sql.sh -t -e "
SELECT keyword, COUNT(*) n FROM search_history
 WHERE searched_at >= NOW() - INTERVAL 15 MINUTE
 GROUP BY keyword ORDER BY n DESC LIMIT 10;"
```
Expected: 서로 다른 검색어가 여러 개. **`스타벅스` 하나만 나오면 CSV가 안 읽힌 것이다**

- [ ] **Step 6: load 스모크 (10 VU · 30초)**

Run: `scripts/perf-k6/k6ec2.sh run load.js smoke-load VUS=10 RAMP=10s DURATION=30s DURATION_SEC=30`
Expected: `밀도별 분해` 표가 비어 있지 않다. `results/smoke-load.md`가 로컬에 내려온다

- [ ] **Step 7: 스모크 결과를 지운다**

측정 결과와 섞이면 안 된다. `results/`는 gitignore라 커밋 걱정은 없다.

Run: `rm -f scripts/perf-k6/results/smoke-*`

- [ ] **Step 8: README를 갱신한다**

`## 실행` 표 아래에 더한다.

```markdown
### 어디서 돌리나

**k6는 k6 EC2(`i-05eb81746a575ca47`)에서 돌린다.** 로컬에서 쏘면 인터넷 왕복이 끼어
같은 조건이 아니다. `scripts/perf-k6/k6ec2.sh`가 전송과 실행과 결과 회수를 한다.

```bash
scripts/perf-k6/k6ec2.sh push
scripts/perf-k6/k6ec2.sh run load.js load-before-20260819 VUS=100
```

EC2 역할에 S3 권한이 없어 **presigned URL**로 주고받는다. IAM을 건드리지 않으려는 선택이다.

### 시나리오 CSV

`baseline.js`와 `load.js`는 좌표·검색어·카테고리를 CSV에서 읽는다. 예전에는 전부 상수였다 —
강남역 · `스타벅스` · 카페/디저트 · 단일 `storeId`. **한 조합만 반복 측정하고 있었다.**

| 파일 | 쓰는 곳 | 성격 |
|---|---|---|
| `scenarios-load.csv` | `load.js` | 실빈도 모사 2,000행. 희소 앵커 3% 주입 |
| `scenarios-baseline.csv` | `baseline.js` | 층화 표본 40행(예열 10 + 측정 30) |
| `keyword-selectivity.csv` | 층화 근거 | 검색어 305개의 전국 매칭 수 |

다시 뽑으려면 `python3 scripts/perf-k6/extract-scenarios.py`. **개선 전/후 비교 중에는
다시 뽑지 않는다** — 두 측정이 같은 표본을 봐야 한다.

### 함정 5. 밀도 서브메트릭은 임계값을 선언해야 생긴다

k6는 임계값에 없는 태그 조합의 서브메트릭을 만들지 않는다. 밀도별 표를 읽으려면 통과가
보장되는 임계값(`p(95)<999999`)이라도 걸어야 한다. **빠뜨리면 에러 없이 표가 통째로 비어 나온다.**

### 함정 6. 지터로는 희소 좌표를 만들 수 없다

좌표를 `store`에서 뽑으면 출발점이 항상 가맹점이라, 지터를 ±1.1km까지 늘려도 **1km 안 0건이
0.0%다**(실측 150표본). 벗어나면 다른 매장이 또 있기 때문이다. 그래서 희소 앵커를 따로 주입한다 —
`#270`의 사다리 10km 확장이 그 좌표에서만 발동한다.
```

- [ ] **Step 9: 커밋**

```bash
git add scripts/perf-k6/k6ec2.sh scripts/perf-k6/README.md
git commit -m "perf: k6 EC2 전송·실행 스크립트와 README를 더한다

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Phase B — 측정 런북

> **여기서부터는 TDD가 아니라 절차다.** 운영 배포와 DB DDL이 들어가므로 각 단계에 검증 명령과
> 기대 출력을 붙였고, 실패하면 다음으로 넘어가지 않는다.
>
> **되돌릴 수 없는 조작은 V12 인덱스 DROP 하나뿐이고, 그 재생성이 Task 8의 첫 단계다.**

### Task 7: 개선 전 만들기 + before 측정

- [ ] **Step 1: `search_history`를 재적재한다**

`store/keywords`가 `NOW() - 7 DAY`를 본다. 재적재하지 않으면 집계가 0건이 되어 전·후 둘 다
빨라지고 비교가 무의미해진다. **이후 before/after 사이에는 다시 건드리지 않는다.**

Run: `PERF_TABLES="search_history" PERF_ALLOW_DEV_PORT=1 scripts/perf-data/load.sh --reset`
Expected: 적재 완료 후 아래가 70만 이상

```bash
scripts/perf-k6/prod-sql.sh -N -e "SELECT COUNT(*) FROM search_history WHERE searched_at >= NOW() - INTERVAL 7 DAY;"
```

- [ ] **Step 2: V12 인덱스를 DROP한다**

Run: `scripts/perf-k6/prod-sql.sh -e "DROP INDEX idx_search_history_searched_at_keyword ON search_history;"`
Expected: 에러 없음. 확인:

```bash
scripts/perf-k6/prod-sql.sh -N -e "SELECT COUNT(*) FROM information_schema.statistics
 WHERE table_schema='fitwallet' AND index_name='idx_search_history_searched_at_keyword';"
```
Expected: `0`

- [ ] **Step 3: 개선 전 WAR로 되돌린다**

Run:
```bash
aws elasticbeanstalk update-environment --environment-name fitwallet-prod \
  --version-label gh-283.1-4b6f6af --region ap-northeast-2
```

- [ ] **Step 4: Flyway가 옛 WAR을 받아들이는지 확인한다 — 이 계획의 단일 최대 위험**

이력에 V11~V13이 있는데 그 WAR은 V10까지만 안다. Flyway 13의 `ignoreMigrationPatterns`
기본값이 `*:future`라 통과할 것으로 보지만, 틀리면 앱이 아예 안 뜬다.

Run:
```bash
aws elasticbeanstalk describe-environments --environment-names fitwallet-prod \
  --region ap-northeast-2 --query 'Environments[].{S:Status,H:Health,V:VersionLabel}'
curl -s -o /dev/null -w '%{http_code}\n' \
  http://fitwallet-backend-prod.ap-northeast-2.elasticbeanstalk.com/health/db
```
Expected: `Status=Ready`, `Health=Green`, `VersionLabel=gh-283.1-4b6f6af`, HTTP `200`

**실패하면 여기서 멈춘다.** 즉시 `gh-293.1-9194df4`로 되돌리고 Task 8 Step 1(V12 재생성)을
실행한 뒤, `flyway_schema_history` 조작 여부를 사람에게 묻는다.

- [ ] **Step 5: 옛 코드가 실제로 도는지 육안 확인**

Run:
```bash
curl -s -o /dev/null -w '%{time_total}s\n' -H "Authorization: Bearer $TOKEN" \
 'http://fitwallet-backend-prod.ap-northeast-2.elasticbeanstalk.com/api/store/search?latitude=37.4979&longitude=127.0276&radiusMeters=3000'
```
Expected: **1초 이상.** 40ms대면 새 WAR이 아직 도는 것이다 — 배포가 안 끝났다.

- [ ] **Step 6: before를 측정한다**

Run:
```bash
scripts/perf-k6/k6ec2.sh run baseline.js baseline-before-20260819
scripts/perf-k6/k6ec2.sh run load.js     load-before-20260819
```
Expected: 두 파일 쌍이 `results/`에 내려온다. 1차와 같은 붕괴(SLO 0/19 · 5xx 45%)는 **예상된 결과다.**

⚠️ **CSV를 다시 push하지 않는다.** before와 after가 같은 표본을 봐야 한다 — Task 6 Step 3의
`push`가 마지막이고, 그 뒤로는 `run`만 쓴다.

---

### Task 8: 복구 + 최신화 + after 측정

- [ ] **Step 1: V12 인덱스를 재생성하고 검증한다 — 잊으면 after가 부당하게 느려진다**

Run:
```bash
scripts/perf-k6/prod-sql.sh -e "CREATE INDEX idx_search_history_searched_at_keyword ON search_history (searched_at, keyword);
ANALYZE TABLE search_history;"
scripts/perf-k6/prod-sql.sh -N -e "SELECT COUNT(*) FROM information_schema.statistics
 WHERE table_schema='fitwallet' AND index_name='idx_search_history_searched_at_keyword';"
```
Expected: `1`

- [ ] **Step 2: PR #270을 머지한다**

프론트 합의(2글자 미만 400 거부)가 끝났는지 확인한 뒤 머지한다. CD가 자동 배포한다.

Run: `gh pr merge 270 --repo heartbeat-kb-town/fitwallet-backend --squash`

- [ ] **Step 3: 배포와 V14 인덱스 생성을 기다린다**

인덱스 생성이 운영에서 80초를 넘길 수 있어 그동안 앱이 늦게 올라온다.

Run:
```bash
aws elasticbeanstalk describe-environments --environment-names fitwallet-prod \
  --region ap-northeast-2 --query 'Environments[].{S:Status,H:Health,V:VersionLabel}'
curl -s -o /dev/null -w '%{http_code}\n' \
  http://fitwallet-backend-prod.ap-northeast-2.elasticbeanstalk.com/health/db
```
Expected: `Ready` / `Green` / 새 버전 라벨, HTTP `200`

- [ ] **Step 4: V14가 제대로 만들어졌는지 검증한다 — stopword가 오염되면 after가 부당하게 빨라진다**

Run:
```bash
scripts/perf-k6/prod-sql.sh -t -e "
SELECT index_name FROM information_schema.statistics
 WHERE table_schema='fitwallet' AND table_name='store'
   AND index_name IN ('ft_store_name','idx_store_lat_lng_name') GROUP BY index_name;
SELECT (SELECT COUNT(*) FROM store WHERE store_name LIKE '%bar%') like_n,
       (SELECT COUNT(*) FROM store WHERE MATCH(store_name) AGAINST('+\"bar\"' IN BOOLEAN MODE)) match_n;"
```
Expected: 인덱스 2개가 나오고 `like_n = match_n`. **다르면 기본 stopword로 만들어진 것이다** —
`ft_store_name`을 DROP하고 V14를 다시 돌린다.

- [ ] **Step 5: after를 측정한다**

Run:
```bash
scripts/perf-k6/k6ec2.sh run baseline.js baseline-after-20260819
scripts/perf-k6/k6ec2.sh run load.js     load-after-20260819
```

- [ ] **Step 6: 네 결과가 같은 표본을 봤는지 대조한다**

결정적 순회라 조합 순서가 같아야 한다. 어긋났으면 비교가 성립하지 않는다.

Run:
```bash
python3 - <<'PY'
import json
for kind in ("baseline", "load"):
    b = json.load(open(f"scripts/perf-k6/results/{kind}-before-20260819.json"))
    a = json.load(open(f"scripts/perf-k6/results/{kind}-after-20260819.json"))
    print(kind, "before rows", len(b["rows"]), "after rows", len(a["rows"]))
    assert {r["name"] for r in b["rows"]} == {r["name"] for r in a["rows"]}, "엔드포인트 집합이 다르다"
print("OK")
PY
```
Expected: `OK`

---

### Task 9: 보고서

- [ ] **Step 1: 노션에 「7. 최종 부하 테스트」를 새로 만든다**

`고도화 문서` 데이터소스(`collection://279b3fa2-b818-4153-aac9-d275e8fc0afc`)에 새 페이지.
**1차 결과 문서(4단계)는 건드리지 않는다** — 데이터셋이 달라 같은 표에 못 섞는다.

절 구성과 명시할 한계 여섯은 스펙 §4를 그대로 따른다.

- [ ] **Step 2: 서술 규칙을 지킨다**

**"N배 빨라졌다"는 1 VU 표에서만 쓴다.** 100 VU의 before는 커넥션 풀 고갈을 잰 것이라
19개가 전부 3.00초에 붙어 있고 손대지도 않은 `health_db`까지 74배 빨라진 것처럼 나온다.
100 VU는 SLO 충족 수 · 에러율 · 달성 처리량으로만 말한다.

- [ ] **Step 3: 이슈를 등록하고 PR을 올린다**

```bash
ISSUE=$(gh issue create --repo heartbeat-kb-town/fitwallet-backend \
  --title "[TASK] 부하 테스트 데이터셋을 실사용 분포로 바꾸고 최종 측정한다" \
  --label "🛠️ 작업" --label "🔼 높음" \
  --body "$(cat <<'BODY'
## 작업 내용
k6 시나리오의 좌표·검색어·카테고리·storeId가 전부 고정값이라 한 조합만 반복 측정하고 있었다.

## 작업 목표
실사용 분포 데이터셋으로 바꾸고, 개선 전 버전으로 되돌려 같은 하네스로 다시 잰 뒤
최신 버전에서 최종 측정해 5단계 개선 폭을 보고한다.

## 세부 작업 목록
- [ ] 시나리오 추출 스크립트와 CSV 두 개
- [ ] load.js / baseline.js 개조 (조합·밀도 태그·storeId 인과)
- [ ] 개선 전 되돌리기 + before 측정
- [ ] #270 머지 + after 측정
- [ ] 노션 「7. 최종 부하 테스트」 작성

## 참고 자료
설계: `docs/superpowers/specs/2026-08-19-final-load-test-design.md`

## 완료 조건
개선 전/후 1 VU · 100 VU 표 4개와 밀도별 분해가 노션에 실린다.
BODY
)" | grep -oE '[0-9]+$')
echo "이슈 #$ISSUE"

git push -u origin perf/final-load-test-harness
gh pr create --repo heartbeat-kb-town/fitwallet-backend --base main \
  --title "[#$ISSUE] perf: 부하 테스트 데이터셋을 실사용 분포로 바꾼다" \
  --body "closes #$ISSUE"
```

- [ ] **Step 4: 메모리를 갱신한다**

`perf-test-runs-on-prod`의 진행 위치를 7단계 완료로 고치고, `store-search-pr2-fulltext`를
머지 완료 상태로 고친다.
