-- V15 — store 2차원 좌표 인덱스: lat_cell 생성 컬럼 + (lat_cell, longitude, latitude, store_name) (2026-08-23)
--
-- 목적: GET /api/store/search 의 검색어 갈래에서 사각형 스캔을 2차원으로 좁힌다.
--
--
-- 문제 — B-tree 복합 인덱스는 선행 컬럼이 범위면 후행 컬럼으로 범위를 더 좁히지 못한다.
--
-- V11 의 idx_store_lat_lng_name (latitude, longitude, store_name) 은 사각형 조건
-- (latitude BETWEEN ... AND longitude BETWEEN ...) 을 받는데, 실행계획을 보면 인덱스 범위가
-- **위도에만** 걸린다.
--
--   Index range scan on s using idx_store_lat_lng_name over (37.47091 < latitude < 37.52489),
--     with index condition: (latitude BETWEEN ... AND longitude BETWEEN ... AND store_name LIKE ...)
--
-- 경도와 store_name 은 ICP 필터로만 작동한다. 즉 스캔 대상이 사각형이 아니라 **전국을
-- 가로지르는 위도 띠**다. 운영 RDS 강남역 실측:
--
--   반경     위도 띠      진짜 사각형     낭비
--   300m      32,329        3,871       8.4배
--   3km      309,163       84,778       3.6배
--   10km     183,642       13,169      13.9배
--
--
-- 해결 — 선행 컬럼을 **등치 조건**으로 만든다.
--
-- 위도를 0.01°(약 1.11km) 격자로 접은 lat_cell 을 만들고, 사각형이 걸치는 셀들을
-- lat_cell IN (...) 으로 준다. 선행이 등치가 되면 그 다음 컬럼인 longitude 의 범위가
-- 인덱스에 먹는다 — 실행계획이 이렇게 바뀐다.
--
--   over (lat_cell = 3747 AND 126.99358 < longitude < 127.06162)
--     OR (lat_cell = 3748 AND 126.99358 < longitude < 127.06162) OR (4 more)
--
-- 셀 크기 0.01° 는 V11 이 밀도 단계를 나눌 때 쓴 격자와 같다(extract-scenarios.py 의 GRID).
-- 3km 사각형이면 6~7개 셀이라 IN 목록이 짧고, 세로 과다 읽기는 1.3배 안쪽이다.
--
--
-- ⚠️ STORED 여야 한다. VIRTUAL 로 만들면 오히려 3배 느려진다.
--
-- MySQL 은 **가상 생성 컬럼 위의 보조 인덱스에서 ICP 를 지원하지 않는다.** VIRTUAL 로 만들면
-- 범위는 2차원으로 잘 좁혀지는데(스캔 90,053행 < 위도 띠 309,163행) 조건이 전부 서버 레이어
-- Filter 로 올라와, 걸러질 행까지 전부 꺼내 올린다. 로컬 272만 행 강남역 3km 실측:
--
--   검색어        위도 띠     2차원 VIRTUAL    2차원 STORED
--   주유소        63.9ms        149ms          16.3ms
--   카페          52.1ms        132ms          18.1ms
--   파리바게뜨     41.1ms        130ms          16.4ms
--
-- 범위를 좁히는 것보다 ICP 를 유지하는 것이 크다. 둘 다 얻으려면 STORED 뿐이다.
--
--
-- ⚠️ latitude 를 인덱스에 넣는 이유 — 거리식이 위경도를 둘 다 읽는다.
--
-- lat_cell 은 FLOOR 로 접은 값이라 원본 위도를 복원하지 못한다. (lat_cell, longitude, store_name)
-- 만 만들면 ACOS 를 계산할 latitude 가 없어 후보 행마다 PK 로 되짚어야 한다. latitude 를 넣으면
-- 판정과 정렬이 인덱스 안에서 끝나고, 테이블로는 최종 5건만 나간다.
--
--
-- 비용 — store 테이블에는 앱 쓰기가 전혀 없다(Flyway 참조 데이터로만 채워진다). 그래서
-- 인덱스를 늘리는 대가가 쓰기 지연이 아니라 저장공간뿐이다. 인덱스 약 150MB 예상.
--
-- ⚠️ **생성 시간이 V11 과 자릿수가 다르다.** STORED 생성 컬럼 추가는 온라인 DDL 이 아니라
-- **테이블 리빌드**다. 로컬 272만 행에서 컬럼 2분 4초 + 인덱스 6.9초였다(V11 은 2.5초 + 3초).
-- Flyway 가 앱 기동 시점에 돌리므로 **배포 후 첫 기동이 그만큼 늘어난다.**
--
-- 그래서 운영에서는 **배포 전에 아래 DDL 을 직접 실행해 두는 것을 권한다.** 이 마이그레이션은
-- 멱등하므로, 미리 만들어 두면 Flyway 는 존재를 확인하고 넘어간다(기동 지연 0).
--
--   scripts/perf-k6/prod-sql.sh -e "ALTER TABLE store ADD COLUMN lat_cell SMALLINT
--       GENERATED ALWAYS AS (FLOOR(latitude * 100)) STORED;"
--   scripts/perf-k6/prod-sql.sh -e "CREATE INDEX idx_store_cell_lng_lat_name
--       ON store (lat_cell, longitude, latitude, store_name);"
--
--
-- ⚠️ 이 마이그레이션만으로는 아무것도 빨라지지 않는다. StoreMapper.xml 이 lat_cell IN (...) 을
-- 걸고 FORCE INDEX 를 이 인덱스로 바꿔야 효과가 난다 — V11 과 같은 구조다. 짝이 되는 코드
-- 변경이 없으면 인덱스만 150MB 늘어난다.
--
-- 기존 idx_store_lat_lng_name 은 이번에 지우지 않는다. 매퍼가 아직 FORCE INDEX 로 이름을
-- 박아 쓰고 있어, 먼저 지우면 에러 1176 으로 검색이 통째로 죽는다. 새 경로가 검증된 뒤
-- 별도로 정리한다.
--
--
-- 멱등하다. MySQL 에 ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS 가 없어
-- information_schema 로 존재를 확인한 뒤 PREPARE 로 실행한다(V5·V11 과 같은 방식).

SET @stmt := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'store'
        AND column_name = 'lat_cell') = 0,
    'ALTER TABLE store ADD COLUMN lat_cell SMALLINT
         GENERATED ALWAYS AS (FLOOR(latitude * 100)) STORED
         COMMENT ''위도를 0.01도 격자로 접은 값. 사각형 검색의 선행 등치 조건 전용(V15)''',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt := IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE()
        AND table_name = 'store'
        AND index_name = 'idx_store_cell_lng_lat_name') = 0,
    'CREATE INDEX idx_store_cell_lng_lat_name ON store (lat_cell, longitude, latitude, store_name)',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 인덱스를 만들어도 통계가 낡아 있으면 옵티마이저가 새 인덱스를 안 고를 수 있다.
ANALYZE TABLE store;

-- 검증
-- 1) 컬럼과 인덱스가 있어야 한다. lat_cell 은 위도 × 100 의 내림이다.
-- SELECT latitude, lat_cell FROM store WHERE latitude IS NOT NULL LIMIT 3;
-- SHOW INDEX FROM store WHERE Key_name = 'idx_store_cell_lng_lat_name';
--
-- 2) **실행계획에 'with index condition' 이 있어야 한다.** 없으면 ICP 가 안 붙은 것이고,
--    그 상태로는 이 인덱스가 위도 띠보다 느리다(위 표 참고). STORED 가 아닌지 의심한다.
-- EXPLAIN ANALYZE SELECT ... WHERE s.lat_cell IN (...) AND s.longitude BETWEEN ... ;
--
-- 3) 범위가 2차원이어야 한다 — over (lat_cell = N AND ... < longitude < ...) 형태.
--    over (lat_cell = N) 만 나오면 longitude 가 인덱스에 안 먹은 것이다.
