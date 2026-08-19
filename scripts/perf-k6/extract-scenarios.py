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
    # ⚠️ 되받는 키는 검색어 문자열이 아니라 배치 안의 순번(인덱스)이다. esc()를 거친 리터럴을
    #    MySQL이 그대로 돌려주지 않을 수 있어(예: %/_가 든 값은 \%처럼 백슬래시째 온다),
    #    문자열로 되받으면 원래 검색어와 키가 안 맞아 그 검색어가 조용히 0건으로 기록된다.
    sel = {}
    for i in range(0, len(keywords), 25):
        batch = keywords[i:i + 25]
        parts = [f"SELECT {j} i, COUNT(*) n FROM store "
                 f"WHERE store_name LIKE '%{esc(k)}%'" for j, k in enumerate(batch)]
        for r in run_sql(" UNION ALL ".join(parts) + ";"):
            sel[batch[int(r[0])]] = int(r[1])
        print(f"  {len(sel)}/{len(keywords)}", flush=True)

    with cache.open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["keyword", "matchCount"])
        for k in keywords:
            w.writerow([k, sel.get(k, 0)])
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


if __name__ == "__main__":
    main()
