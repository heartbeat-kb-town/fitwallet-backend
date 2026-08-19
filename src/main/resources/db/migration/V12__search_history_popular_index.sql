-- V12 — search_history 인기 검색어 집계용 커버링 인덱스 (2026-08-19)
--
-- 목적: GET /api/store/keywords 의 popular 갈래(StoreMapper.findPopularKeywords)가
-- 풀스캔 대신 인덱스를 타게 한다.
--
-- 왜 지금까지 풀스캔이었나 — search_history 에 걸린 인덱스가 PRIMARY 와
-- uk_search_history_user_id_keyword (user_id, keyword) 둘뿐인데, 이 쿼리의 WHERE 는
-- searched_at 하나만 본다. InnoDB 는 테이블을 PK(= 입력 순서) 로 저장하므로 searched_at
-- 은 그 안에서 아무 순서도 아니고, "N일 이후인 행" 을 찾을 방법이 전건 확인밖에 없다.
-- 그래서 305 개짜리 결과를 만들려고 80만 행을 읽었다.
--
--
-- 왜 (searched_at, keyword) 두 컬럼인가 — range + covering 을 동시에 만족해야 한다.
--
--   ① range   선두가 searched_at 이라 인덱스가 날짜순으로 정렬된다. B-tree 로 시작점을
--             찾아 거기서부터 끝까지만 읽고 멈춘다. 창 밖의 행은 아예 안 본다.
--   ② covering 이 쿼리가 보는 컬럼은 searched_at(WHERE·MAX) 과 keyword(GROUP BY·SELECT)
--             뿐이다. 둘 다 인덱스에 있으니 인덱스만 읽고 테이블로 돌아가지 않는다.
--
-- 둘 중 하나만 만족하면 효과가 없거나 오히려 손해다. 로컬 perf DB(80만 행, 고유 키워드
-- 305개, 7일 창 223,041행 = 27.9%) 실측:
--
--   후보                       실측     접근 경로                        읽은 행
--   현행(인덱스 없음)          190ms    Table scan                       800,000
--   (searched_at) 단일         220ms    옵티마이저가 안 쓴다              800,000
--     └ FORCE INDEX 로 강제    317ms    range scan + PK 룩업 22.3만 회   223,041
--   (searched_at, keyword)     106ms    Covering index range scan        223,041
--   (keyword, searched_at)     210ms    Covering index scan(범위 못 좁힘) 800,000
--
-- 단일 인덱스가 더 느린 이유는 keyword 를 인덱스에서 못 구해 22.3만 번 PK 로 테이블을
-- 되짚기 때문이다. 순차 풀스캔 80만 행보다 랜덤 접근 22.3만 회가 비싸다 — 옵티마이저가
-- 이걸 미리 계산해서 인덱스를 버린 것이고, 그 판단이 맞았다.
--
-- (keyword, searched_at) 은 선두가 keyword 라 원하는 행이 305개 구간에 흩어진다.
-- B-tree 는 앞 컬럼부터 채워야 범위를 좁히므로 인덱스 전체를 훑게 된다.
--
--
-- 이 마이그레이션은 짝을 이루는 매퍼 변경이 없다. SQL 을 한 글자도 고치지 않는다 —
-- 옵티마이저가 힌트 없이 이 인덱스를 고르기 때문이다(V11 은 인덱스를 만들어도 버려서
-- FORCE INDEX 가 필요했다. 여기는 커버링이라 비용 모델이 스스로 인정한다).
-- 따라서 응답이 달라질 여지가 없고, 기존 StoreMapperIntegrationTest 의 popular 검증
-- 5건이 그대로 회귀 가드가 된다.
--
--
-- 비용 — store 와 달리 search_history 는 앱이 쓰는 테이블이다. 검색할 때마다
-- upsertSearchHistory 가 돌고, ON DUPLICATE KEY UPDATE searched_at = NOW() 가
-- 이 인덱스의 선두 컬럼을 바꾸므로 엔트리 재배치(삭제+삽입)가 일어난다.
-- 업서트 3,000건 × 2쌍 실측은 인덱스 있음 0.600 / 0.634 ms/건, 없음 0.587 / 0.634 ms/건
-- 으로 노이즈 수준이었다(로컬 단일 세션 기준이라 RDS 동시 쓰기 경합을 재현한 것은 아니다).
--
-- 저장공간 34.6MB (데이터 49.6MB, 기존 인덱스 110.3MB). RDS 는 gp3 20GB 라 여유가 있다.
-- 생성 시간은 로컬 80만 행에서 1초 남짓이고, Flyway 가 앱 기동 시점에 돌리므로 배포 후
-- 첫 기동이 그만큼 늘어난다.
--
--
-- 한계 — 이 변경은 1.8배(190ms → 106ms)에 그친다. 남은 106ms 중 약 70ms 는 22.3만 행을
-- GROUP BY 하는 임시 테이블 비용인데, 이건 "요청마다 전량 재집계" 라는 구조 자체가
-- 원인이라 어떤 인덱스로도 없앨 수 없다(인덱스가 줄인 것은 읽기 구간 88.5ms → 25.8ms
-- 뿐이고, 집계 구간 70ms 는 전후가 같다). 부하 p95 가 목표 안으로 들어오지 않으면
-- 다음 카드는 요약 테이블(사전 집계)이다 — 읽기가 0.025ms 가 되는 대신 갱신 주체와
-- 정합성 관측이라는 새 논점이 붙는다.
--
-- 또 하나, 7일 창이 테이블의 27.9% 라는 것은 성능 테스트 시드의 특성이다. 실제 운영
-- 분포는 다를 수 있고, 창이 더 넓어지면 옵티마이저가 커버링 인덱스를 버리고 풀스캔으로
-- 돌아갈 수 있다. 배포 후 EXPLAIN 으로 접근 경로를 확인해야 하는 이유다.
--
--
-- 멱등하다. MySQL 에 CREATE INDEX IF NOT EXISTS 가 없어(V8 주석 참고) 그냥 CREATE INDEX 를
-- 쓰면 재실행 시 "Duplicate key name" 으로 마이그레이션이 실패하고, 그러면 앱이 뜨지 않는다.
-- information_schema 로 존재를 확인한 뒤 PREPARE 로 실행한다(V5·V11 과 같은 방식).

SET @stmt := IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE()
        AND table_name = 'search_history'
        AND index_name = 'idx_search_history_searched_at_keyword') = 0,
    'CREATE INDEX idx_search_history_searched_at_keyword ON search_history (searched_at, keyword)',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 인덱스를 만들어도 통계가 낡아 있으면 옵티마이저가 새 인덱스를 안 고를 수 있다.
-- ANALYZE 는 통계만 다시 읽어 금방 끝난다.
ANALYZE TABLE search_history;

-- 검증
-- 1) 인덱스가 있어야 한다
-- SHOW INDEX FROM search_history WHERE Key_name = 'idx_search_history_searched_at_keyword';
--
-- 2) popular 갈래가 이 인덱스를 타야 한다. Extra 에 "Using index" 가 있고 type=range 여야
--    하며, type=ALL 이면 풀스캔으로 돌아간 것이다(위 "한계" 의 두 번째 문단 참고).
-- EXPLAIN SELECT ROW_NUMBER() OVER (ORDER BY COUNT(*) DESC, MAX(searched_at) DESC) AS `rank`,
--                keyword, COUNT(*) AS search_count
--           FROM search_history
--          WHERE searched_at >= (NOW() - INTERVAL 7 DAY)
--          GROUP BY keyword
--          ORDER BY search_count DESC, MAX(searched_at) DESC
--          LIMIT 5;
