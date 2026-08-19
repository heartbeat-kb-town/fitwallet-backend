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


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else ""
    sel = keyword_selectivity()
    if only == "--only-selectivity":
        return
    # Task 2·3에서 이어 붙인다.
    print(f"선택도 {len(sel)}개 확보")


if __name__ == "__main__":
    main()
