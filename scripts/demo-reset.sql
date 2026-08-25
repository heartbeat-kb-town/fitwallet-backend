-- =========================================================
-- 스타벅스 5,000원 결제 카드 추천 시연 상태를 되돌린다.
--
--   docker compose exec -T mysql mysql -ufitwallet -pfitwallet1234 fitwallet < scripts/demo-reset.sql
--
-- 시연 직전에 한 번 실행한다. 실행할 때마다 아래 상태가 다시 만들어진다.
--
--   fitwallet123 / demo1 / demo2 / demo3   (비밀번호 11112222, 결제 PIN 123456)
--   store 21 스타벅스 화양삼거리점 · 5,000원 조회
--     1위 KB국민 청춘대로 톡톡  2,500원   ← 일 한도 10,000원 중 7,500원 소진, 잔여 2,500원
--     2위 KB국민 노리 체크      1,000원
--     3위 신한 Pick E 체크        500원
--     4위 현대 Z everyday         250원
--      -  신한 All Pass         "오늘 받을 수 있는 혜택 횟수를 모두 사용했어요."
--   결제하면 톡톡의 일 한도가 전액 소진되어 1위가 노리 체크로 바뀐다.
--
-- ---------------------------------------------------------
-- 왜 필요한가
-- ---------------------------------------------------------
-- ① Flyway는 한 번만 돈다. V910이 심은 "오늘"은 마이그레이션이 적용되던 날의 오늘이라,
--    하루만 지나도 일 한도가 리셋되어 시나리오가 죽는다. 전월 실적도 달이 바뀌면 창 밖으로 나간다.
-- ② 한 번 결제한 계정은 소진 상태로 남는다. 같은 계정으로 다시 시연하려면 되돌려야 한다.
--
-- ---------------------------------------------------------
-- 무엇을 지우는가
-- ---------------------------------------------------------
-- payment_transaction 1000번 이상(= 시나리오 행 + 시연 중 앱으로 만든 결제)과
-- payment_session 5번 이상을 지우고 다시 심는다.
-- **V900~V909가 심은 fitwallet123의 과거 거래 355건과 세션 1~4는 건드리지 않는다.**
--
-- 시나리오를 만드는 SQL은 여기에 없다 — 정본은
-- db/seed-local/V910__demo_daily_limit_scenario.sql의 demo_reset_scenario() 프로시저다.
-- 양쪽에 복제하면 반드시 어긋난다.
-- =========================================================

CALL `demo_reset_scenario`();

SELECT '--- 시연 상태 확인 (스타벅스 화양삼거리점 5,000원) ---' AS ``;

SELECT u.login_id                                          AS 계정,
       cp.card_name                                        AS 카드,
       FORMAT(COALESCE(pm.prev_month_spend, 0), 0)         AS 전월실적,
       FORMAT(COALESCE(d.used_today, 0), 0)                AS 오늘_톡톡_할인소진
FROM user_card uc
         JOIN users u ON u.user_id = uc.user_id
         JOIN card_product cp ON cp.card_product_id = uc.card_product_id
         LEFT JOIN (SELECT user_card_id, SUM(amount) AS prev_month_spend
                    FROM payment_transaction
                    WHERE is_eligible = 1
                      AND transaction_status = 'APPROVED'
                      AND paid_at >= DATE_FORMAT(NOW() - INTERVAL 1 MONTH, '%Y-%m-01')
                      AND paid_at < DATE_FORMAT(NOW(), '%Y-%m-01')
                    GROUP BY user_card_id) pm ON pm.user_card_id = uc.user_card_id
         LEFT JOIN (SELECT user_card_id, SUM(discount_amount) AS used_today
                    FROM payment_transaction
                    WHERE applied_tier_id = 162
                      AND transaction_status = 'APPROVED'
                      AND paid_at >= CURDATE()
                    GROUP BY user_card_id) d ON d.user_card_id = uc.user_card_id
WHERE uc.user_id IN (1, 2, 3, 4)
  AND uc.is_deleted = 0
ORDER BY uc.user_id, uc.display_order;
