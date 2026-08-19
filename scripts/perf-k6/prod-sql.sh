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
