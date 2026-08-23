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


def keyword_selectivity():
    """검색어별 전국 LIKE 매칭 수와 **최빈 카테고리**. 풀스캔이라 3~4분 — 한 번 재고 파일로 남긴다.

    FULLTEXT(V14)가 아직 없는 시점에 재므로 매 건이 272만 행 풀스캔이다. 그래도 여기서
    재야 하는 이유는, 이 값이 #270 라우팅 임계값 13,000의 양쪽을 층화 표본에 넣는 기준이기
    때문이다. V14 배포 후에 재면 값은 같지만 개선 전 측정이 이미 끝난 뒤가 된다.

    **카테고리를 같은 스캔에서 함께 뽑는다.** 검색어→카테고리 매핑표를 손으로 유지하면
    검색어 풀이 바뀔 때마다 낡는다. 매칭된 매장의 최빈 카테고리를 쓰면 저절로 따라온다 —
    실측으로 카페→1 · 약국→5 · 버거→4 · 서점→3이 전부 맞고 2위와 격차도 크다.

    반환값은 {검색어: (매칭 수, 카테고리 ID)}다.
    """
    cache = HERE / "keyword-selectivity.csv"
    if cache.exists():
        with cache.open(encoding="utf-8") as f:
            reader = csv.DictReader(f)
            if "categoryId" in (reader.fieldnames or []):
                return {r["keyword"]: (int(r["matchCount"]), int(r["categoryId"]))
                        for r in reader}
        # 카테고리 열이 없는 옛 캐시(2열)는 버리고 다시 잰다.
        print(f"{cache.name}에 categoryId 열이 없다 — 다시 잰다", flush=True)

    # ⚠️ 오염 검색어를 후보에서 뺀다. 과거 k6 버전이 URL을 이중 인코딩해 넣은 행이 운영에
    #    남아 있다(`%25EC%258A%25A4...` = 스타벅스). 빼지 않으면 후보 풀에 섞여 들어간다.
    keywords = [r[0] for r in run_sql(
        "SELECT DISTINCT keyword FROM search_history "
        r"WHERE CHAR_LENGTH(keyword) >= 2 AND keyword NOT LIKE '%\%%' ORDER BY keyword;")]
    print(f"검색어 {len(keywords)}개의 전국 매칭 수·최빈 카테고리를 잰다 (풀스캔이라 3~4분)",
          flush=True)

    # 한 번의 접속으로 전부 센다. 305회 왕복을 피하려고 UNION ALL로 묶는다.
    #
    # ⚠️ LIKE 패턴에 사용자 입력이 들어가므로 %와 _를 이스케이프한다. 검색어에 %가 있으면
    #    "아무거나"로 해석돼 매칭 수가 폭증하고 층화가 조용히 틀어진다.
    def esc(k):
        return k.replace("\\", "\\\\").replace("'", "''").replace("%", "\\%").replace("_", "\\_")

    # 305개를 한 문장으로 묶으면 3~4분짜리 쿼리가 되어 net_write_timeout(기본 60초)에 걸린다.
    # 25개씩 끊으면 배치 하나가 20초 안쪽이다.
    # ⚠️ 되받는 키는 검색어 문자열이 아니라 배치 안의 순번(인덱스)이다. esc()를 거친 리터럴을
    #    MySQL이 그대로 돌려주지 않을 수 있어(예: %/_가 든 값은 \%처럼 백슬래시째 온다),
    #    문자열로 되받으면 원래 검색어와 키가 안 맞아 그 검색어가 조용히 0건으로 기록된다.
    # 카테고리별로 쪼개 받아 파이썬에서 합계와 최빈값을 낸다. 스캔 횟수는 그대로다.
    per_cat = {}
    for i in range(0, len(keywords), 25):
        batch = keywords[i:i + 25]
        parts = [f"SELECT {j} i, category_id c, COUNT(*) n FROM store "
                 f"WHERE store_name LIKE '%{esc(k)}%' GROUP BY category_id"
                 for j, k in enumerate(batch)]
        for r in run_sql(" UNION ALL ".join(parts) + ";"):
            per_cat.setdefault(batch[int(r[0])], {})[int(r[1])] = int(r[2])
        print(f"  {len(per_cat)}/{len(keywords)}", flush=True)

    # 최빈 카테고리는 매칭이 적은 검색어에서 흔들린다 — `AK몰`은 매칭 1건이고 그 한 곳이
    # 카페라 카페/디저트로 잡힌다. 브랜드명과 정확히 일치하면 brand 테이블이 정답이다.
    # (build_synthetic.py의 main()도 같은 보정을 한다 — 둘의 분류가 같아야 한다.)
    brand_categories = {r[1].strip(): int(r[0])
                        for r in run_sql("SELECT category_id, brand_name FROM brand;") if r[1].strip()}

    sel = {}
    for k in keywords:
        cats = per_cat.get(k, {})
        total = sum(cats.values())
        # 동점이면 카테고리 ID가 작은 쪽 — 결정적이어야 재실행 결과가 같다.
        modal = min(cats, key=lambda c: (-cats[c], c)) if cats else 7
        sel[k] = (total, brand_categories.get(k, modal))

    with cache.open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["keyword", "matchCount", "categoryId"])
        for k in keywords:
            w.writerow([k, sel[k][0], sel[k][1]])
    print(f"저장: {cache} — {len(sel)}개")
    return sel


GRID = 0.01  # 약 1.1km(위도). 밀도 단계와 앵커 탐색의 해상도다.


def grid_counts():
    """0.01° 격자별 매장 수. 매장이 0인 격자는 애초에 결과에 없다 — 앵커 탐색이 그 성질을 쓴다."""
    rows = run_sql(
        "SELECT ROUND(latitude, 2), ROUND(longitude, 2), COUNT(*) "
        "FROM store WHERE latitude IS NOT NULL GROUP BY 1, 2;")
    return {(float(a), float(b)): int(c) for a, b, c in rows}


def tier_bounds(grid):
    """격자 매장 수의 **매장 가중** 7분위수 경계. 단계 1(가장 희소)~7(가장 밀집).

    ⚠️ 격자 **수**로 나누면 안 된다. 실측하면 경계가 [1, 2, 3, 5, 11, 40]이 나오고
    매장의 91.3%가 최상위 단계 하나에 몰린다 — 격자 대부분이 시골이기 때문이다.
    판정용 좌표는 store에서 뽑혀 밀도 가중이므로, 그 경계를 쓰면 좌표의 93%가 같은
    버킷으로 태깅돼 밀도별 분해가 아무것도 구분하지 못한다. 태그를 다는 이유가 사라진다.

    매장 수로 가중하면 각 단계가 매장의 1/7(14.3%)씩 담는다 — 각 단계가 실제 트래픽의
    1/7을 대표한다는 뜻이라, 밀도별 표가 "어느 구간의 사용자가 어떤 응답을 받나"를 답한다.
    """
    counts = sorted(grid.values())
    total = sum(counts)
    target = total / 7
    bounds, cum, t = [], 0, 1
    for n in counts:
        cum += n
        while t < 7 and cum >= target * t:
            bounds.append(n)
            t += 1
    return bounds


def density_tier(bounds, lat, lng, grid):
    """좌표가 속한 격자의 매장 수로 1~7 부여."""
    n = grid.get((round(lat, 2), round(lng, 2)), 0)
    tier = 1
    for b in bounds:
        if n >= b:
            tier += 1
    return tier


LOAD_ROWS = 2000
JITTER = 0.0045       # 약 500m. 사용자가 가맹점 위에 서 있어 300m가 항상 차는 것만 푼다

# 사용자가 검색어를 치고 칩을 누르는 업종 분포. **공개 통계가 아니라 우리가 정한 가정치다** —
# 근거와 대가는 docs/superpowers/specs/2026-08-22-k6-scenario-realism-design.md §2·§3에 있다.
#
# ⚠️ 같은 벡터가 build_synthetic.py의 write_search_history()에도 있다. 검색어 분포(DB)와
#    카테고리 칩 분포(CSV)가 같은 벡터를 써야 한다는 것이 설계 전제이므로 **둘은 함께 고친다.**
CATEGORY_WEIGHTS = {
    1: 0.26,  # 카페/디저트
    4: 0.26,  # 푸드
    2: 0.20,  # 편의점/마트
    3: 0.16,  # 쇼핑
    6: 0.08,  # 주유
    5: 0.04,  # 병원
}
assert abs(sum(CATEGORY_WEIGHTS.values()) - 1.0) < 1e-9, "CATEGORY_WEIGHTS의 합이 1이 아니다"


def build_load_csv(grid, bounds):
    """실빈도 모사 2,000행. 한 행 = 사용자 한 세션.

    좌표와 선택 매장은 **같은 매장 한 곳**에서 나온다 — 사용자가 그 매장 근처에 서서 그
    매장의 혜택을 조회하는 동선이다. 지터가 ±500m라 반경 3km 검색 결과에 반드시 들어간다.

    기타(7)를 좌표 추출에서 뺀다. 매장의 44.9%가 기타인데 기타는 혜택이 0건이라,
    빼지 않으면 benefit_expected 측정의 절반이 "혜택 없음"으로 끝난다(설계 §4).
    """
    rnd = random.Random(20260819)

    stores = [(int(sid), float(a), float(b)) for sid, a, b in run_sql(
        f"SELECT store_id, latitude, longitude FROM store "
        f"WHERE latitude IS NOT NULL AND category_id <> 7 "
        f"ORDER BY RAND() LIMIT {LOAD_ROWS};")]
    if len(stores) < LOAD_ROWS:
        sys.exit(f"좌표를 {LOAD_ROWS}개 못 뽑았다({len(stores)}개).")

    # 검색어는 search_history 행 균등 추출이다. DB가 이미 업종 벡터를 담고 있으므로
    # (build_synthetic.write_search_history) 여기서 가중치를 또 걸면 이중 적용이 된다.
    keywords = [r[0] for r in run_sql(
        f"SELECT keyword FROM search_history WHERE CHAR_LENGTH(keyword) >= 2 "
        rf"AND keyword NOT LIKE '%\%%' ORDER BY RAND() LIMIT {LOAD_ROWS};")]
    if len(keywords) < LOAD_ROWS:
        sys.exit(f"검색어를 {LOAD_ROWS}개 못 뽑았다({len(keywords)}개). "
                 f"search_history가 비었거나 재적재 중이다.")

    # 카테고리 칩은 좌표와 독립이다 — 사용자는 자기 위치와 무관하게 누른다.
    cats = weighted_categories(rnd, LOAD_ROWS)

    rows = []
    for i in range(LOAD_ROWS):
        sid, la, lo = stores[i]
        tier = density_tier(bounds, la, lo, grid)
        la += rnd.uniform(-JITTER, JITTER)
        lo += rnd.uniform(-JITTER, JITTER)
        rows.append([f"{la:.6f}", f"{lo:.6f}", tier, keywords[i], cats[i], sid])

    rnd.shuffle(rows)
    write_csv(HERE / "scenarios-load.csv", rows)


def weighted_categories(rnd, n):
    """CATEGORY_WEIGHTS 비율대로 n개를 만든다.

    무작위 추출이 아니라 **정확한 개수를 배분한 뒤 섞는다.** 2,000행에서 주유(8%)는
    160개인데, 추출로 뽑으면 표본오차로 ±25개가 흔들려 "왜 이 비율인가"를 설명할 때
    CSV의 실제 비율이 스펙의 숫자와 어긋난다.
    """
    cats = []
    for cid, w in sorted(CATEGORY_WEIGHTS.items()):
        cats.extend([cid] * round(n * w))
    # 반올림 오차는 가장 비중이 큰 카테고리에서 메운다.
    top = max(CATEGORY_WEIGHTS, key=lambda c: (CATEGORY_WEIGHTS[c], -c))
    while len(cats) < n:
        cats.append(top)
    del cats[n:]
    rnd.shuffle(cats)
    return cats


def write_csv(path, rows):
    with path.open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["lat", "lng", "densityTier", "keyword", "categoryId", "storeId"])
        w.writerows(rows)
    print(f"저장: {path} — {len(rows)}행")


BASELINE_WARMUP = 10
BASELINE_ROWS = 30


def selectivity_band(n):
    """#270 라우팅 임계값 13,000의 양쪽이 표본에 반드시 들어가게 하는 구간."""
    if n < 1000:
        return "lo"
    if n <= 13000:
        return "mid"
    return "hi"


def build_baseline_csv(grid, bounds, sel):
    """밀도 7단계 × 선택도 3구간 = 21칸에서 고르게 뽑는다.

    예열 10행과 측정 30행을 나누는 이유는, 같은 행으로 예열하면 그 조합만 버퍼 풀에
    올라간 채 측정에 들어가기 때문이다.
    """
    rnd = random.Random(20260819)
    by_band = {"lo": [], "mid": [], "hi": []}
    for k, (n, _cat) in sel.items():
        by_band[selectivity_band(n)].append(k)
    for v in by_band.values():
        rnd.shuffle(v)
    empty_bands = [b for b, v in by_band.items() if not v]
    if empty_bands:
        sys.exit(f"선택도 구간에 검색어가 없다: {empty_bands}. "
                 f"#270 임계값 양쪽을 표본에 넣을 수 없으므로 중단한다.")

    # ⚠️ 좌표를 store에서 바로 뽑으면 밀집 격자에만 몰려 희소 단계(1~2)가 빈다 — store 표본
    #    자체가 밀도 가중이기 때문이다. **격자를 단계별로 나눠 고른 뒤, 그 격자 안의 매장을
    #    하나 집는다.** 분위수의 정의상 모든 단계에 셀이 존재하는 것이 보장되고, 좌표가 실제
    #    매장이라 load CSV와 같은 규칙으로 storeId를 얻는다. 판정용(build_load_csv)은 실빈도가
    #    목적이라 반대로 store 표본이 맞다 — 두 CSV의 목적이 다르므로 뽑는 방법도 다르다.
    by_tier = {t: [] for t in range(1, 8)}
    for (gla, glo) in grid:
        by_tier[density_tier(bounds, gla, glo, grid)].append((gla, glo))
    for v in by_tier.values():
        rnd.shuffle(v)

    cats = weighted_categories(rnd, BASELINE_WARMUP + BASELINE_ROWS)

    cells = [(t, b) for t in range(1, 8) for b in ("lo", "mid", "hi")]  # 21칸
    # ⚠️ 격자를 `idx // len(cells)`로 고르면 같은 단계의 세 선택도 구간이 **같은 격자**를
    #    쓴다(셋 다 같은 몫). 그러면 40행이 14개 매장만 반복해 benefit_expected가 데워진
    #    버퍼 풀을 재게 된다. 단계마다 쓴 횟수를 따로 세어 행마다 다른 격자를 쓴다.
    used = {t: 0 for t in range(1, 8)}
    rows, idx = [], 0
    while len(rows) < BASELINE_WARMUP + BASELINE_ROWS:
        tier, band = cells[idx % len(cells)]
        pool = by_tier[tier]
        if not pool:
            sys.exit(f"밀도 {tier}단계에 격자가 없다 — 표본 수를 늘려라.")
        picked = None
        # 격자에 기타(7)뿐이면 매장이 안 잡힌다. 같은 단계의 다음 격자로 넘어간다.
        for step in range(len(pool)):
            gla, glo = pool[(used[tier] + step) % len(pool)]
            picked = store_in_cell(gla, glo)
            if picked:
                used[tier] += step + 1
                break
        if not picked:
            sys.exit(f"밀도 {tier}단계에서 기타가 아닌 매장을 못 찾았다.")
        sid, la, lo = picked
        la += rnd.uniform(-JITTER, JITTER)
        lo += rnd.uniform(-JITTER, JITTER)
        kw = by_band[band][(idx // len(cells)) % len(by_band[band])]
        rows.append([f"{la:.6f}", f"{lo:.6f}", tier, kw, cats[len(rows)], sid])
        idx += 1
    write_csv(HERE / "scenarios-baseline.csv", rows)


def store_in_cell(gla, glo):
    """0.01° 격자 안에서 기타(7)가 아닌 매장 하나. 없으면 None."""
    rows = run_sql(
        f"SELECT store_id, latitude, longitude FROM store "
        f"WHERE category_id <> 7 AND latitude IS NOT NULL "
        f"AND latitude >= {gla - GRID / 2} AND latitude < {gla + GRID / 2} "
        f"AND longitude >= {glo - GRID / 2} AND longitude < {glo + GRID / 2} "
        f"ORDER BY store_id LIMIT 1;")
    return (int(rows[0][0]), float(rows[0][1]), float(rows[0][2])) if rows else None


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else ""
    sel = keyword_selectivity()
    if only == "--only-selectivity":
        return
    grid = grid_counts()
    bounds = tier_bounds(grid)
    print(f"격자 {len(grid)}개 · 7분위 경계 {bounds}")
    build_load_csv(grid, bounds)
    build_baseline_csv(grid, bounds, sel)


if __name__ == "__main__":
    main()
