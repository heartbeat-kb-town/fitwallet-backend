-- V11 — store 위경도 인덱스 2개 (2026-08-18)
--
-- 목적: GET /api/store/search 의 좌표+반경 갈래와 카테고리 갈래가 인덱스를 타게 한다.
--
-- 왜 지금까지 인덱스를 못 탔나 — 위경도 인덱스를 "만들 수 없어서"가 아니라 "쓸 조건이
-- 없어서"였다. 거리 조건이 HAVING 의 계산식(ACOS(...))에만 있었기 때문이다. 인덱스는
-- 컬럼 원본값의 순서로 만들어지는데 조건이 그 값을 함수에 넣은 결과를 보고 있으면
-- 옵티마이저가 범위를 좁힐 근거가 없다. 그래서 272만 행 전체를 훑고 거리를 다 계산한 뒤
-- 5건을 뽑았다(운영 2.15s).
--
-- 이 마이그레이션과 짝을 이루는 StoreMapper.xml 변경이 원의 외접 사각형을
-- WHERE s.latitude BETWEEN ... AND s.longitude BETWEEN ... 으로 먼저 걸어 준다.
-- 그 조건이 바로 아래 두 인덱스가 읽는 조건이다. 둘 중 하나만 있으면 효과가 없다.
--
--
-- 인덱스가 왜 2개인가 — leftmost prefix.
--
--   idx_store_lat_lng      (latitude, longitude)               약 77MB
--   idx_store_cat_lat_lng  (category_id, latitude, longitude)  약 90MB
--
-- 위치만 오는 요청(categoryId 없음)에는 category_id 조건이 없어 복합 인덱스의 첫 컬럼을
-- 채울 수 없다. B-tree 는 앞 컬럼부터 순서대로 채워야 범위를 좁힐 수 있으므로, 첫 컬럼을
-- 건너뛴 채 latitude 로 들어가는 것은 불가능하다. 반대로 카테고리 갈래에 (latitude, longitude)
-- 만 쓰면 사각형 안 8.4만 행을 전부 읽은 뒤 category_id 를 필터해야 해서 느리다.
-- 로컬 perf DB(272만 행, 강남역) 실측:
--
--   반경    (lat,lng)만    인덱스 2개
--   1km        0.18s          0.00s
--   3km        0.18s          0.01s
--
-- 0.18s 는 운영 환산 약 300ms 로 목표(100ms)를 3배 넘긴다. 그래서 둘 다 만든다.
--
-- 기존 idx_store_category_id (약 67MB) 는 idx_store_cat_lat_lng 의 prefix 라 논리적으로
-- 중복이지만 이번에 지우지 않는다. store 를 참조하는 매퍼가 8개라 영향 범위 확인이 별건이다.
--
--
-- 비용 — store 테이블에는 앱 쓰기가 전혀 없다(Flyway 참조 데이터로만 채워진다).
-- 그래서 인덱스를 늘리는 대가가 쓰기 지연이 아니라 저장공간뿐이다.
--
-- 생성 시간 — 로컬 272만 행에서 2.5초 + 3초. InnoDB 온라인 DDL 이라 읽기·쓰기가 막히지는
-- 않지만, Flyway 가 앱 기동 시점에 돌리므로 배포 후 첫 기동이 그만큼 늘어난다.
--
--
-- 멱등하다. MySQL 에 CREATE INDEX IF NOT EXISTS 가 없어(V8 주석 참고) 그냥 CREATE INDEX 를
-- 쓰면 재실행 시 "Duplicate key name" 으로 마이그레이션이 실패하고, 그러면 앱이 뜨지 않는다.
-- information_schema 로 존재를 확인한 뒤 PREPARE 로 실행한다(V5 와 같은 방식).

SET @stmt := IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE()
        AND table_name = 'store'
        AND index_name = 'idx_store_lat_lng') = 0,
    'CREATE INDEX idx_store_lat_lng ON store (latitude, longitude)',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt := IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE()
        AND table_name = 'store'
        AND index_name = 'idx_store_cat_lat_lng') = 0,
    'CREATE INDEX idx_store_cat_lat_lng ON store (category_id, latitude, longitude)',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 인덱스를 만들어도 통계가 낡아 있으면 옵티마이저가 새 인덱스를 안 고를 수 있다.
-- ANALYZE 는 통계만 다시 읽어 금방 끝난다.
ANALYZE TABLE store;

-- 검증
-- 1) 인덱스 2개가 있어야 한다
-- SHOW INDEX FROM store WHERE Key_name IN ('idx_store_lat_lng', 'idx_store_cat_lat_lng');
--
-- 2) 좌표+반경 갈래가 idx_store_lat_lng 를, 카테고리 갈래가 idx_store_cat_lat_lng 를
--    타야 한다. type=range 가 아니면(= ALL 이면) 매퍼 쪽 bbox 조건이 안 걸린 것이다.
-- EXPLAIN SELECT ... FROM store s WHERE s.latitude BETWEEN ... ;
