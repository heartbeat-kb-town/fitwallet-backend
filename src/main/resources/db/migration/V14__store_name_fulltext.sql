-- V14 — store_name FULLTEXT(ngram) + 좌표·상호명 커버링 인덱스 (2026-08-19)
--
-- 목적: GET /api/store/search 의 검색어 갈래를 SLO(p95 100ms) 안으로 넣는다.
-- 운영 1 VU p50 1.11s / 100 VU p95 16.42s 로 유일하게 남은 위반 항목이다.
--
-- V11 이 좌표+반경·카테고리 갈래를 해결했지만 검색어 갈래에는 효과가 0이었다.
-- 검색어 검색은 반경이 NULL(전국)이라 그릴 사각형이 없기 때문이다. 남은 원인은 두 가지고,
-- 이 마이그레이션은 각각에 인덱스를 하나씩 대응시킨다.
--
--   ① 전국을 훑어야 할 때  → ft_store_name        (FULLTEXT ngram)
--   ② 사각형 안을 훑을 때  → idx_store_lat_lng_name (커버링)
--
--
-- ① ft_store_name — LIKE '%키워드%' 가 인덱스를 못 타는 문제
--
-- 선행 와일드카드라 B-tree 가 접두사부터 좁히는 방식과 맞지 않아 272만 행을 전부 읽는다.
-- 검색어 선택도와 무관하게 660~700ms 로 일정한 것이 그 증거다 — 3건이 남든 43,722건이
-- 남든 같다. 비용은 "무엇을 찾느냐"가 아니라 "전부 읽는다"에서 나온다.
--
-- ngram 파서는 상호명을 2글자 단위로 잘라 색인하므로 부분일치를 인덱스로 좁힐 수 있다.
-- 로컬 perf DB(272만 행) 실측: '스타벅스' 654.1ms → 4.4ms, 읽은 행 2,725,572 → 34.
--
-- ⚠️ MATCH 는 판정자가 아니라 가속기다. 매퍼가 MATCH 뒤에 기존 LIKE 를 그대로 남긴다.
-- ngram 은 부분일치의 정확한 대응물이 아니라서(아래 stopword 항목) 단독으로 쓰면 결과가
-- 조용히 바뀐다. MATCH 가 LIKE 의 상위집합이기만 하면 응답이 보존된다 — 그 보장은
-- DefaultStoreService.buildMatchExpression 이 만든다.
--
--
-- ⚠️ 빈 stopword 테이블이 반드시 먼저 있어야 한다
--
-- ngram 은 토큰이 stopword 를 "포함만 해도" 색인에서 뺀다. 기본 목록의 a / i / in / to 가
-- 라틴 키워드를 광범위하게 죽인다 — 재생성 전 실측으로 bar(263) · lab(213) · nail(305) ·
-- ca(1,412) · A1(130) 이 전부 0건이었다. 쿼리는 성공하고 결과만 조용히 비어 나오므로
-- 테스트가 없으면 절대 못 잡는다.
--
-- 빈 stopword 테이블을 가리킨 상태에서 인덱스를 만들면 전부 일치한다(재생성 후 실측:
-- bar 263=263 · lab 213=213 · nail 305=305 · ca 1,412=1,412 · in 1,952=1,952 · A1 130=130).
-- innodb_ft_user_stopword_table 은 세션 변수라 RDS 에서 SUPER 권한 없이 설정된다.
--
-- 조회 시점에는 이 설정이 필요 없다(실측 확인). 즉 커넥션 풀에 init SQL 을 걸 필요가 없고,
-- 이 파일 안에서만 세션에 걸어 두면 된다. Flyway 가 스크립트 하나를 한 커넥션에서 실행하므로
-- 아래 SET 이 CREATE FULLTEXT 까지 유효하다.
--
--
-- ② idx_store_lat_lng_name — 사각형 안에서 LIKE 를 거를 때의 되짚기
--
-- 검색어 갈래도 계단식 사각형을 쓰게 되면서(서비스 참고) 사각형 단계가 새로 생겼다.
-- 그런데 idx_store_lat_lng 에는 store_name 이 없어, 사각형 안 행을 전부 PK 로 되짚어
-- 상호명을 읽은 뒤에야 LIKE 를 걸 수 있다. 강남역 1km 사각형이 18,772행이라 그 되짚기가
-- 비용의 대부분이었다.
--
-- store_name 을 인덱스에 넣으면 LIKE 가 인덱스 안에서 평가돼(index condition pushdown)
-- 통과한 소수만 테이블로 간다. 로컬 perf DB 강남역 실측:
--
--   반경    (lat,lng)              (lat,lng,store_name)
--   300m     9.4ms /  3,881행        6.4ms /    17행
--   1km     43.4ms / 18,772행       23.7ms /    80행
--   10km   541.8ms / 349,806행     149.9ms / 2,583행
--
-- 이 인덱스가 없으면 전국 매칭 1.3만~2만 건대 검색어('미용' 16,905 · '치킨' 20,793)가
-- 밀집 좌표에서 어느 경로로도 SLO 를 못 맞춘다 — 사다리도 전국도 전부 초과한다(최선 126ms).
-- 넣으면 87~90ms 로 들어온다. 즉 이 인덱스는 최적화가 아니라 SLO 충족의 전제다.
--
-- ⚠️ idx_store_lat_lng 를 지우지 않는다. (latitude, longitude) 는 새 인덱스의 prefix 라
-- 논리적으로는 중복이고, 주변 조회를 새 인덱스로 돌려도 2~11% 느려질 뿐이다(강남 300m
-- 13.9 → 15.5ms). 그럼에도 남기는 이유는 롤백이다 — 배포 후 이전 WAR 로 되돌리면 그 코드가
-- FORCE INDEX (idx_store_lat_lng) 를 쓰는데, 인덱스가 없으면 조용히 무시되는 게 아니라
-- 에러(1176)라 가맹점 조회가 통째로 죽는다. 마이그레이션은 앱과 함께 롤백되지 않는다.
-- 롤백 창이 지난 뒤 별도 마이그레이션으로 지운다.
--
--
-- 비용
--
--   ft_store_name           FTS 테이블스페이스 101.2MB + FTS_DOC_ID_INDEX 64.6MB = 165.8MB
--   idx_store_lat_lng_name  137.9MB
--
-- ⚠️ FTS_DOC_ID_INDEX 를 빠뜨리기 쉽다. FULLTEXT 를 만들면 MySQL 이 숨은 FTS_DOC_ID 컬럼과
-- 그 인덱스를 자동으로 붙이므로 실제 비용은 101.2MB 가 아니라 165.8MB 다.
--
-- store 에는 앱 쓰기가 전혀 없다(Flyway 참조 데이터로만 채워진다). 그래서 인덱스를 늘리는
-- 대가가 쓰기 지연이 아니라 저장공간뿐이다 — V11 과 같은 조건이다.
--
-- ⚠️ 생성 시간이 길다. 로컬 272만 행에서 FULLTEXT 43.6초 + 커버링 5.0초다. 운영은 로컬보다
-- 1.67배 느리므로 70초를 넘길 수 있다. Flyway 가 sqlSessionFactory 보다 먼저 돌도록
-- depends-on 이 걸려 있어(§11) 그만큼 앱 기동이 늦어진다 — 배포 후 EB 상태와 GET /health/db 를
-- 반드시 확인한다.
--
--
-- 멱등하다. MySQL 에 CREATE INDEX IF NOT EXISTS 가 없어 그냥 만들면 재실행 시
-- "Duplicate key name" 으로 마이그레이션이 실패하고 앱이 뜨지 않는다.
-- information_schema 로 존재를 확인한 뒤 PREPARE 로 실행한다(V5·V11 과 같은 방식).

-- 빈 stopword 테이블. 컬럼명이 반드시 value 여야 하고 엔진이 InnoDB 여야 한다(MySQL 규약).
-- 행을 넣지 않는 것이 핵심이다 — "stopword 가 하나도 없다"는 뜻이 된다.
CREATE TABLE IF NOT EXISTS ft_empty_stopword (
    value VARCHAR(18) NOT NULL,
    PRIMARY KEY (value)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- DATABASE() 로 스키마명을 채운다. 리터럴로 박으면 스키마명이 다른 환경에서 조용히 어긋나
-- 기본 stopword 목록으로 인덱스가 만들어진다 — 그 경우 실패가 에러가 아니라 "영어 검색어가
-- 0건"으로 나타나므로 배포 후에도 눈치채기 어렵다.
SET @stmt := CONCAT('SET SESSION innodb_ft_user_stopword_table = ''',
                    DATABASE(), '/ft_empty_stopword''');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt := IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE()
        AND table_name = 'store'
        AND index_name = 'ft_store_name') = 0,
    'CREATE FULLTEXT INDEX ft_store_name ON store (store_name) WITH PARSER ngram',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt := IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE()
        AND table_name = 'store'
        AND index_name = 'idx_store_lat_lng_name') = 0,
    'CREATE INDEX idx_store_lat_lng_name ON store (latitude, longitude, store_name)',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 인덱스를 만들어도 통계가 낡아 있으면 옵티마이저가 새 인덱스를 안 고를 수 있다.
ANALYZE TABLE store;

-- 검증
-- 1) 인덱스 2개가 생겨야 한다
-- SHOW INDEX FROM store WHERE Key_name IN ('ft_store_name', 'idx_store_lat_lng_name');
--
-- 2) stopword 가 비어 있는지 — 아래 둘이 같아야 한다. 다르면 인덱스를 지우고 이 파일을
--    처음부터 다시 돌려야 한다(세션 변수를 건 상태에서 만들어야 하므로 재생성이 유일한 방법이다).
-- SELECT COUNT(*) FROM store WHERE store_name LIKE '%bar%';
-- SELECT COUNT(*) FROM store WHERE MATCH(store_name) AGAINST('+"bar"' IN BOOLEAN MODE);
