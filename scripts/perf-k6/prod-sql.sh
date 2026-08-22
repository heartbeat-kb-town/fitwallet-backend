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
# ⚠️ grep을 `|| true`로 감싼다. 빈 입력에서 grep은 1을 반환하는데, set -o pipefail이 그걸
#    스크립트 종료코드로 올린다. DDL은 성공해도 아무것도 출력하지 않으므로
#    (`DROP INDEX ...` 등) **성공한 문장이 조용히 실패로 보인다** — 놀라서 재시도하면
#    그때는 진짜 에러가 난다. 이렇게 두면 pipefail이 mysql의 종료코드를 그대로 전한다.
#    (`CREATE INDEX ...; ANALYZE TABLE ...`은 ANALYZE가 결과 표를 뱉어 안 걸린다 — 그래서 더 헷갈렸다.)
docker exec -i -e MYSQL_PWD="$P" fitwallet-mysql-perf \
  mysql -h "$RDS" -P 3306 -u "$U" --default-character-set=utf8mb4 --connect-timeout=15 fitwallet "$@" \
  2>&1 | { grep -v "Warning" || true; }
