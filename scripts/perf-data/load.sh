#!/usr/bin/env bash
#
# 생성된 TSV를 성능 DB에 적재한다.
#
#   scripts/perf-data/load.sh
#
# 전제 — docker compose -f docker-compose.perf.yml up -d 로 DB가 떠 있고,
#        ./gradlew appRun -Denv=perf 로 스키마(V1~V9)가 적용돼 있어야 한다.
#
# 이 데이터는 Flyway 밖이라 flyway_schema_history에 기록되지 않는다.
set -euo pipefail

PERF_DATA_DIR="${PERF_DATA_DIR:-$HOME/fitwallet-perf-data}"
OUT_DIR="$PERF_DATA_DIR/out"

DB_HOST="${PERF_DB_HOST:-127.0.0.1}"
DB_PORT="${PERF_DB_PORT:-3308}"
DB_USER="${PERF_DB_USER:-fitwallet}"
DB_PASSWORD="${PERF_DB_PASSWORD:-fitwallet1234}"
DB_NAME="${PERF_DB_NAME:-fitwallet}"

# FK 순서대로 적재한다. FOREIGN_KEY_CHECKS를 꺼도 순서를 지켜 두면, 켠 뒤 검증할 때
# 무엇이 깨졌는지 바로 드러난다.
TABLES=(users user_card store payment_transaction search_history)

# 3308이 아닌 곳에 붙는 사고를 막는다. 개발 DB에 540만 행이 들어가면 되돌리기 어렵다.
if [[ "$DB_PORT" == "3306" || "$DB_PORT" == "3307" ]]; then
    echo "중단: PERF_DB_PORT=$DB_PORT 는 개발 DB에서 흔히 쓰는 포트다." >&2
    echo "      성능 DB는 3308이다. 정말 이 포트에 넣으려면 PERF_ALLOW_DEV_PORT=1 을 준다." >&2
    [[ "${PERF_ALLOW_DEV_PORT:-0}" == "1" ]] || exit 1
fi

for table in "${TABLES[@]}"; do
    if [[ ! -f "$OUT_DIR/$table.tsv" ]]; then
        echo "중단: $OUT_DIR/$table.tsv 가 없다." >&2
        echo "      build_store.py / build_synthetic.py 를 먼저 돌린다." >&2
        exit 1
    fi
done

mysql_run() {
    # --local-infile=1 은 클라이언트 쪽 스위치다. 서버 쪽은 docker-compose.perf.yml의
    # --local-infile=1 이 담당한다 — 둘 다 있어야 LOAD DATA LOCAL INFILE이 통한다.
    #
    # 비밀번호는 MYSQL_PWD로 넘긴다. --password로 주면 mysql이 매번 경고를 stderr에 뱉는데,
    # 아래 진행 표시가 개행 없이 출력되는 탓에 경고와 한 줄에 뒤섞여 테이블명이 가려진다.
    # ps에 비밀번호가 노출되지 않는 부수 효과도 있다.
    MYSQL_PWD="$DB_PASSWORD" mysql \
        --default-character-set=utf8mb4 \
        --local-infile=1 \
        --host="$DB_HOST" --port="$DB_PORT" \
        --user="$DB_USER" \
        "$DB_NAME" "$@"
}

echo "적재 대상 $DB_HOST:$DB_PORT/$DB_NAME"
echo "입력 $OUT_DIR"
echo

# store는 V2 참조데이터 244행이 이미 있고 그 뒤(245~)에 덧붙인다. 나머지 넷은 비어 있어야 한다.
# 두 번 돌리면 PK 중복으로 실패하므로, 되돌리려면 TRUNCATE 후 다시 돌린다.
echo "== 적재 전 행수 =="
mysql_run -t -e "
SELECT 'users' t, COUNT(*) n FROM users
UNION ALL SELECT 'user_card', COUNT(*) FROM user_card
UNION ALL SELECT 'store', COUNT(*) FROM store
UNION ALL SELECT 'payment_transaction', COUNT(*) FROM payment_transaction
UNION ALL SELECT 'search_history', COUNT(*) FROM search_history;"
echo

for table in "${TABLES[@]}"; do
    file="$OUT_DIR/$table.tsv"
    rows=$(wc -l < "$file" | tr -d ' ')
    printf '%-22s %10s행 적재 중...' "$table" "$rows"
    started=$(date +%s)

    # 컬럼 목록을 명시한다. created_at/updated_at은 DB DEFAULT가 채우므로 넣지 않는다
    # (AGENTS.md §10). NULL은 \N 표기로 들어온다.
    columns=$(head -1 "$OUT_DIR/$table.columns")

    mysql_run --execute="
        SET FOREIGN_KEY_CHECKS = 0;
        SET UNIQUE_CHECKS = 0;
        SET autocommit = 0;
        LOAD DATA LOCAL INFILE '$file'
             INTO TABLE \`$table\`
             CHARACTER SET utf8mb4
             FIELDS TERMINATED BY '\t'
             LINES TERMINATED BY '\n'
             ($columns);
        COMMIT;
        SET UNIQUE_CHECKS = 1;
        SET FOREIGN_KEY_CHECKS = 1;"

    printf ' %d초\n' "$(( $(date +%s) - started ))"
done

echo
echo "== 적재 후 행수 =="
mysql_run -t -e "
SELECT 'users' t, COUNT(*) n FROM users
UNION ALL SELECT 'user_card', COUNT(*) FROM user_card
UNION ALL SELECT 'store', COUNT(*) FROM store
UNION ALL SELECT 'payment_transaction', COUNT(*) FROM payment_transaction
UNION ALL SELECT 'search_history', COUNT(*) FROM search_history;"

echo
echo "다음: mysql ... < scripts/perf-data/verify.sql"
