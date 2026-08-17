-- 적재 결과 검증. load.sh 이후에 돌린다.
--
--   mysql --default-character-set=utf8mb4 -h127.0.0.1 -P3308 -ufitwallet -pfitwallet1234 \
--         fitwallet -t < scripts/perf-data/verify.sql
--
-- 각 블록의 '판정' 컬럼이 전부 OK여야 한다. 기대치의 출처는 노션 「데이터 적재 설계 (1단계)」다.
-- 540만 행 집계라 전체가 1~2분 걸린다.

SELECT '=== 1. 테이블별 행수 ===' AS ``;

SELECT t AS 테이블, n AS 실측, want AS 기대, IF(n = want, 'OK', 'MISMATCH') AS 판정
FROM (
    SELECT 'store' t, COUNT(*) n, 2725562 want FROM store
    UNION ALL SELECT 'users', COUNT(*), 50000 FROM users
    UNION ALL SELECT 'user_card', COUNT(*), 195000 FROM user_card
    -- 유저당 건수를 로그정규로 뽑으므로 총합이 시드마다 조금씩 다르다. 목표는 2,700만이고
    -- 정확히 일치할 수 없어 ±2% 범위로 본다.
    UNION ALL SELECT 'payment_transaction', COUNT(*),
        IF(ABS(COUNT(*) - 27000000) <= 540000, COUNT(*), 27000000) FROM payment_transaction
    UNION ALL SELECT 'search_history', COUNT(*), 800000 FROM search_history
) x;

SELECT '=== 2. store category 분포 (브랜드 덮어쓰기 반영 후) ===' AS ``;

SELECT c.category_id, c.category_name AS 이름, COUNT(*) AS 행수,
       ROUND(100 * COUNT(*) / (SELECT COUNT(*) FROM store), 1) AS `%`
FROM store s JOIN category c ON c.category_id = s.category_id
GROUP BY c.category_id, c.category_name ORDER BY c.category_id;

SELECT '=== 3. 브랜드 매칭률 ===' AS ``;

-- 공공데이터 상호명에서 brand를 찾아낸 비율. 별칭(V9) 28개가 이 숫자를 약 2배로 올렸다.
SELECT SUM(brand_id IS NOT NULL) AS 브랜드보유,
       COUNT(*) AS 전체,
       ROUND(100 * SUM(brand_id IS NOT NULL) / COUNT(*), 2) AS `%`,
       IF(ROUND(100 * SUM(brand_id IS NOT NULL) / COUNT(*), 2) BETWEEN 2.3 AND 2.5,
          'OK', 'CHECK — 정규화 규칙을 확인한다') AS 판정
FROM store;

SELECT '=== 4. V1 불변식: brand_id가 있으면 category가 brand와 같아야 한다 ===' AS ``;

SELECT COUNT(*) AS 위반행수, IF(COUNT(*) = 0, 'OK', 'VIOLATION') AS 판정
FROM store s JOIN brand b ON b.brand_id = s.brand_id
WHERE s.category_id <> b.category_id;

SELECT '=== 5. V2 참조데이터(store_id <= 244) 보존 ===' AS ``;

SELECT COUNT(*) AS 행수, want AS 기대, IF(COUNT(*) = want, 'OK', 'MISMATCH') AS 판정
FROM store, (SELECT 244 want) w
WHERE store_id <= 244
GROUP BY want;

-- 기존 244행은 전부 QR 토큰을 갖고 있고, 새로 넣은 272만 행은 전부 NULL이어야 한다.
SELECT SUM(store_id <= 244 AND store_qr_token IS NULL) AS `기존_토큰없음`,
       SUM(store_id > 244 AND store_qr_token IS NOT NULL) AS `신규_토큰있음`,
       IF(SUM(store_id <= 244 AND store_qr_token IS NULL) = 0
          AND SUM(store_id > 244 AND store_qr_token IS NOT NULL) = 0, 'OK', 'CHECK') AS 판정
FROM store;

SELECT '=== 6. 거래의 store 구간 분포 (브랜드 45 / 일반 45 / 기타 10) ===' AS ``;

SELECT CASE WHEN s.brand_id IS NOT NULL THEN '브랜드 보유'
            WHEN s.category_id = 7 THEN '기타 cat7'
            ELSE '브랜드 없음 cat1~6' END AS 구간,
       COUNT(*) AS 건수,
       ROUND(100 * COUNT(*) / (SELECT COUNT(*) FROM payment_transaction), 1) AS `%`
FROM payment_transaction pt JOIN store s ON s.store_id = pt.store_id
GROUP BY 구간 ORDER BY 건수 DESC;

SELECT '=== 7. 혜택 적용값 ===' AS ``;

SELECT SUM(applied_benefit_service_id IS NOT NULL) AS 혜택적용,
       SUM(applied_tier_id IS NOT NULL) AS tier적용,
       ROUND(100 * SUM(applied_benefit_service_id IS NOT NULL) / COUNT(*), 1) AS `적용%`,
       SUM(better_user_card_id IS NOT NULL) AS better카드,
       ROUND(100 * SUM(better_user_card_id IS NOT NULL) / COUNT(*), 1) AS `better%`,
       IF(ROUND(100 * SUM(better_user_card_id IS NOT NULL) / COUNT(*), 1) BETWEEN 29 AND 31,
          'OK', 'CHECK') AS 판정
FROM payment_transaction;

SELECT '=== 7-1. transaction_status는 전부 APPROVED여야 한다 (V10) ===' AS ``;

-- 현재 집계 SQL이 transaction_status를 거르지 않으므로 CANCELED가 섞이면 리포트 합계가 틀어진다.
-- 집계 SQL이 상태를 거르게 되는 후속 작업에서 이 단언을 함께 고친다 (이슈 #226).
SELECT transaction_status AS 상태, COUNT(*) AS 행수,
       IF(transaction_status = 'APPROVED', 'OK', 'CHECK — 집계 SQL이 이 상태를 거르는지 확인') AS 판정
FROM payment_transaction GROUP BY transaction_status;

SELECT '=== 8. 정합성: 적용된 혜택이 그 카드의 상품에 실제로 있는가 ===' AS ``;

-- 이 값이 0이 아니면 무작위로 채운 것이다. GROUP BY 카디널리티와 인덱스 선택도가
-- 실제와 달라져 측정이 왜곡된다.
SELECT COUNT(*) AS 불일치, IF(COUNT(*) = 0, 'OK', 'VIOLATION — 무작위 배정 의심') AS 판정
FROM payment_transaction pt
         JOIN user_card uc ON uc.user_card_id = pt.user_card_id
         JOIN benefit_service bs ON bs.service_id = pt.applied_benefit_service_id
WHERE bs.card_product_id <> uc.card_product_id;

SELECT '=== 9. 카드당 applied_benefit_service_id 그룹 수 (기대: 약 3) ===' AS ``;

-- CardMapper.xml:421의 GROUP BY가 카드 하나에서 만드는 그룹 수다.
-- 무작위로 채웠다면 165에 가까워진다.
SELECT ROUND(AVG(g), 2) AS 평균그룹수, MIN(g) AS 최소, MAX(g) AS 최대,
       IF(AVG(g) < 12, 'OK', 'CHECK — 무작위 배정 의심') AS 판정
FROM (
    SELECT user_card_id, COUNT(DISTINCT applied_benefit_service_id) g
    FROM payment_transaction
    WHERE applied_benefit_service_id IS NOT NULL
    GROUP BY user_card_id
    LIMIT 20000
) x;

SELECT '=== 10. 유저당 거래 수 (꼬리가 있는가) ===' AS ``;

-- ⚠️ 이 판정은 한때 정반대였다. `MIN = MAX`를 'OK — 균등'으로 찍었고, 그래서 활성 유저
-- 전원이 정확히 180건인 데이터가 검증을 통과했다. 그 데이터로 잰 3단계 baseline에서
-- 리포트·카드 계열이 p95 12ms로 나왔는데, 쿼리가 빨라서가 아니라 어떤 유저를 골라도
-- 훑을 행이 수십 개뿐이었기 때문이다. 측정이 쿼리가 아니라 데이터를 쟀다.
--
-- 유저 스코프 쿼리의 부하는 평균이 아니라 **꼬리**가 결정한다. 균등이 곧 실패다.
SELECT MIN(n) AS 최소, ROUND(AVG(n), 1) AS 평균, MAX(n) AS 최대, COUNT(*) AS 활성유저수,
       ROUND(MAX(n) / AVG(n), 1) AS `max/평균`,
       IF(MAX(n) >= AVG(n) * 3, 'OK — 꼬리 있음', 'MISMATCH — 분포가 평평하다') AS 판정
FROM (
    SELECT uc.user_id, COUNT(*) n
    FROM payment_transaction pt JOIN user_card uc ON uc.user_card_id = pt.user_card_id
    GROUP BY uc.user_id
) x;

SELECT '=== 11. paid_at 월별 분포 (12개월에 고른가) ===' AS ``;

SELECT DATE_FORMAT(paid_at, '%Y-%m') AS 월, COUNT(*) AS 건수
FROM payment_transaction GROUP BY 월 ORDER BY 월;

SELECT '=== 12. search_history 최근 7일 비중 (기대 약 28%) ===' AS ``;

-- ⚠️ **이 값은 날이 갈수록 저절로 줄어든다.** 생성기는 기준일(--reference-date)에서 거슬러
-- 7일 안에 25%를 놓는데, `StoreMapper.findPopularKeywords`는 DB의 NOW()를 본다. 기준일과
-- 측정일이 벌어질수록 두 창이 어긋나 대상 행이 빠진다(기준일 +3일이면 약 13%).
--
-- 인기 검색어 API가 정확히 이 행들을 집계하므로, **가만히 둬도 그 엔드포인트가 빨라진다.**
-- 다른 날 잰 두 측정을 비교하면 개선이 아닌 차이를 개선으로 읽게 된다.
-- → 측정 당일을 --reference-date로 주고 search_history만 다시 적재한다.
--    PERF_TABLES="search_history" scripts/perf-data/load.sh   (--reset 없이)
SELECT SUM(searched_at >= NOW() - INTERVAL 7 DAY) AS 최근7일,
       COUNT(*) AS 전체,
       ROUND(100 * SUM(searched_at >= NOW() - INTERVAL 7 DAY) / COUNT(*), 1) AS `%`,
       DATEDIFF(NOW(), MAX(searched_at)) AS `기준일과 벌어진 일수`,
       IF(DATEDIFF(NOW(), MAX(searched_at)) <= 1, 'OK', 'CHECK — 기준일을 갱신하고 재적재') AS 판정
FROM search_history;

SELECT '=== 13. 금액 분포 (category별 중앙값 근사) ===' AS ``;

SELECT s.category_id, c.category_name AS 이름,
       MIN(pt.amount) AS 최소, ROUND(AVG(pt.amount)) AS 평균, MAX(pt.amount) AS 최대
FROM payment_transaction pt
         JOIN store s ON s.store_id = pt.store_id
         JOIN category c ON c.category_id = s.category_id
GROUP BY s.category_id, c.category_name ORDER BY s.category_id;

SELECT '=== 14. final_amount 계산 검증 ===' AS ``;

-- final_amount = amount - 원화환산액. CASHBACK은 discount_amount 그대로,
-- ACCUMULATE는 discount_amount × krw_per_point를 뺀다 (DefaultPaymentService.java:276-278).
SELECT COUNT(*) AS 불일치, IF(COUNT(*) = 0, 'OK', 'VIOLATION') AS 판정
FROM payment_transaction pt
         LEFT JOIN benefit_service bs ON bs.service_id = pt.applied_benefit_service_id
         LEFT JOIN point_currency pc ON pc.point_currency_id = bs.point_currency_id
WHERE ABS(
          pt.final_amount
              - (pt.amount - pt.discount_amount
                  * IF(bs.benefit_type = 'ACCUMULATE', COALESCE(pc.krw_per_point, 1), 1))
      ) > 0.01;
