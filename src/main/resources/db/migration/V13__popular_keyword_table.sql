-- V13 — 인기 검색어 사전 집계 테이블 (2026-08-19)
--
-- 목적: GET /api/store/keywords 의 popular 갈래에서 집계를 요청 경로 밖으로 뺀다.
--
-- 왜 V12(커버링 인덱스)로 부족했나 — V12 는 읽는 행을 80만에서 7일 창 크기로 줄여
-- 운영 p50 297ms → 127ms 를 만들었지만 SLO 100ms 를 1.27배로 넘겼다. 쿼리를 구간별로
-- 쪼개 보면 인덱스가 손댈 수 없는 자리가 어디인지 드러난다.
--
--   구간                          V12 이전   V12 이후
--   읽기(스캔)                     88.5ms     25.8ms   ← 인덱스가 고친 구간
--   집계(GROUP BY 임시 테이블)      70ms       69ms    ← 전후가 같다
--
-- 집계 70ms 는 "요청마다 22만 행을 전량 재집계한다" 는 구조 자체가 원인이라 어떤 인덱스로도
-- 없어지지 않는다. 그래서 미리 집계해 두고 조회는 그 결과만 읽는다.
--
--   V12 이전             190ms
--   V12 커버링 인덱스     106ms
--   요약 테이블 읽기      0.025ms   (Index scan on PRIMARY, 5행)
--
-- 집계 1회 비용은 운영에서 135ms 다(V12 인덱스가 이미 있으므로). DefaultStoreService 의
-- 스케줄러가 5분마다 돌리므로 DB 부하는 135ms / 300초 = 0.045% 다.
--
--
-- 왜 rank 가 PK 인가 — 이 테이블의 행은 언제나 1..5 뿐이다. 대리키를 두면 조회할 때
-- ORDER BY 를 위해 정렬이 한 번 더 붙는데, rank 를 PK 로 두면 클러스터드 인덱스가 곧
-- 정렬 순서라 조회가 Index scan on PRIMARY 5행으로 끝난다.
--
-- rank 는 MySQL 8.0.2+ 예약어라 백틱이 필요하다(StoreMapper.xml 의 기존 쿼리와 같은 이유).
--
--
-- aggregated_at 은 응답에 나가지 않는다. PopularKeywordsResponse 를 바꾸면 API 계약이
-- 바뀌므로 넣지 않았다. 이 컬럼의 용도는 하나다 — 갱신이 멈췄을 때 "지금 나가는 값이 언제
-- 만들어졌나" 를 사람이 확인하는 것. 요약 테이블은 느려지는 대신 조용히 틀릴 수 있게 되는
-- 거래이고, 이 컬럼이 그 대가를 관측 가능하게 만든다.
--
--   SELECT * FROM popular_keyword;   -- aggregated_at 이 5분 이상 낡았으면 스케줄러를 의심한다
--
-- period_days 컬럼은 두지 않는다. 집계 기간은 서비스 정책값이고 이미
-- DefaultStoreService.POPULAR_PERIOD_DAYS 가 소유한다 — 여기 복사하면 정본이 둘이 된다.
--
--
-- 감사 컬럼(created_at/updated_at)을 두지 않는다. 이 테이블의 행은 갱신될 때마다 통째로
-- 지워지고 다시 들어오므로 "언제 만들어졌나" 는 aggregated_at 하나로 충분하고, 행 단위
-- 이력에는 의미가 없다. searched_at 이 감사 컬럼이 아니라 도메인 값인 것과 같은 이유다.
--
--
-- 초기 데이터를 넣지 않는다. 앱이 뜨면 스케줄러가 initialDelay=0 으로 즉시 한 번 돌아
-- 채운다(DefaultStoreService.refreshPopularKeywords). Flyway 는 sqlSessionFactory 보다
-- 먼저 끝나므로(root-context.xml 의 depends-on) 그때 이 테이블은 이미 존재한다.
--
-- 만약 그 사이에 요청이 들어오면 popular.keywords 가 빈 배열로 나가는데, 이건 이미
-- 계약에 있는 상태다 — StoreKeywordsResponse Javadoc 이 "서비스 초기에는 popular.keywords 만
-- 빌 수 있다" 를 명시한다. 그래서 폴백 쿼리를 두지 않는다(두면 없애려던 느린 쿼리가
-- 되살아난다).
--
--
-- 멱등하다. MySQL 은 CREATE TABLE IF NOT EXISTS 를 지원하므로 V11·V12 처럼
-- information_schema + PREPARE 를 쓸 필요가 없다.

CREATE TABLE IF NOT EXISTS popular_keyword (
    `rank`        TINYINT      NOT NULL COMMENT '1..5. 예약어라 백틱 필요',
    keyword       VARCHAR(100) NOT NULL,
    search_count  INT          NOT NULL COMMENT '집계 기간 내 그 키워드를 마지막으로 검색한 사용자 수',
    aggregated_at DATETIME     NOT NULL COMMENT '이 행이 집계된 시각. 응답에 나가지 않는 관측용',
    PRIMARY KEY (`rank`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 검증
-- 1) 앱 기동 후 5행이 채워져야 한다. 비어 있으면 스케줄러가 안 돈 것이다.
-- SELECT * FROM popular_keyword ORDER BY `rank`;
--
-- 2) aggregated_at 이 5분 이내여야 한다.
-- SELECT TIMESTAMPDIFF(SECOND, MAX(aggregated_at), NOW()) AS 낡은초 FROM popular_keyword;
