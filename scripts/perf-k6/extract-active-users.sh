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
