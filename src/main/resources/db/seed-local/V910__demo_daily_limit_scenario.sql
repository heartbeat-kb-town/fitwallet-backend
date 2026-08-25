-- =========================================================
-- V910 — 스타벅스 5,000원 결제 카드 추천 시연 시나리오
--
-- store 21(스타벅스 화양삼거리점, brand 11 / category 1)에서 5,000원을 조회했을 때
-- 아래 화면이 나오도록 데이터를 고정한다.
--
--   결제 전                                                    결제 후
--   1위 KB국민 청춘대로 톡톡  50% 할인   2,500원              1위 KB국민 노리 체크    1,000원
--   2위 KB국민 노리 체크      20% 할인   1,000원              2위 신한 Pick E 체크      500원
--   3위 신한 Pick E 체크      10% 적립     500원              3위 현대 Z everyday       250원
--   4위 현대 Z everyday       5% 할인      250원               -  신한 All Pass       1만원 이상 조건
--    -  신한 All Pass    혜택 받을 수 없음                      -  KB국민 청춘대로 톡톡 일 한도 소진
--
-- 톡톡의 2,500원은 정가가 아니라 "일 할인한도 10,000원 중 7,500원을 이미 썼기 때문에 남은 잔여"다
-- (스타벅스 50% × 5,000원 = 2,500원과 우연히 값이 같다). 5,000원을 결제하면 그 2,500원이 쌓여
-- 일 한도가 정확히 전액 소진되고, 톡톡이 CONDITION_NOT_MET("오늘 받을 수 있는 할인 한도를 모두
-- 사용했어요")으로 내려가면서 1위가 노리 체크로 바뀐다.
--
-- ⚠️ 노리의 스타벅스 혜택(service 125)은 건당 최소 이용금액이 5,000원이다. 4,900원으로 시연하면
-- 노리가 후보에서 빠지고 "5,000원 이상 결제해야 받을 수 있는 혜택이에요"가 나온다.
--
-- ---------------------------------------------------------
-- 이 파일은 고정 id를 하나도 쓰지 않는다
-- ---------------------------------------------------------
-- 첫 버전은 user_id를 2·3·4로, user_card_id를 10~24로, payment_transaction_id를 1000번대로
-- 박아 두었다가 fitwallet-demo 배포에서 죽었다 — 그 DB에는 이미 user_id 2·3을 쓰는 실제
-- 가입자가 있었고 user_card도 19번까지 차 있었다(Duplicate entry '2' for key 'users.PRIMARY').
-- 마이그레이션이 실패하면 앱이 뜨지 않으므로 데모 환경이 통째로 내려갔다.
--
-- 그래서 모든 행을 **자연키로 조회해서** 넣는다. 계정은 login_id, 보유 카드는
-- (user_id, card_product_id), 한도는 (tier_id, limit_basis, limit_period) — 셋 다 UNIQUE라
-- INSERT IGNORE 한 번으로 멱등해진다. AGENTS §11의 "마이그레이션은 멱등하게 쓴다"가
-- 로컬에서만 검증하면 지켜지지 않는다는 것을 이 파일이 실패로 배웠다.
--
-- ---------------------------------------------------------
-- 삭제 범위는 시연 계정으로 좁힌다
-- ---------------------------------------------------------
-- 첫 버전은 리셋에서 payment_transaction_id >= 1000과 payment_session_id > 4를 통째로
-- 지웠다. 로컬에서는 맞지만 공용 DB에서는 **다른 사람의 결제 내역을 지운다** —
-- fitwallet-demo에는 실제 세션이 66건 있었다. 지금은 이렇게 나눈다.
--
--   demo1~3      합성 계정이라 거래를 통째로 지우고 다시 심는다
--   fitwallet123 공용 계정이라 **오늘 것만**, 그중에서도 이 시나리오가 만든 행(tier 162)과
--                시연 중 앱으로 만든 결제(payment_session_id IS NOT NULL)만 지운다
--   그 밖의 계정  건드리지 않는다
--
-- ---------------------------------------------------------
-- 왜 프로시저를 두는가
-- ---------------------------------------------------------
-- 이 시나리오는 "오늘"과 "지난달"에 매달려 있는데, Flyway는 한 번만 돈다. 마이그레이션이 심은
-- "오늘"은 적용되던 날의 오늘이라 하루만 지나도 일 한도가 리셋되고, "지난달" 실적도 달이 바뀌면
-- 전월 창 밖으로 나간다. 게다가 한 번 결제한 계정은 소진 상태로 남아 다시 시연할 수 없다.
--
-- 그래서 날짜에 매달린 부분을 demo_reset_scenario() 하나에 두고, 이 파일과
-- scripts/demo-reset.sql이 둘 다 CALL 한다. 양쪽에 복제하면 반드시 어긋난다.
--
--   시연 직전에 실행:
--   docker compose exec -T mysql mysql -ufitwallet -pfitwallet1234 fitwallet < scripts/demo-reset.sql
-- =========================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------
-- ① 톡톡 스타벅스 혜택에 일 할인한도를 붙인다
--
-- tier 162(service 133, KB국민 청춘대로 톡톡 - 스타벅스 50%)에는 지금 월 한도 30,000원만 있다.
-- 일 한도가 없으면 하루에 몇 번을 결제해도 순위가 바뀌지 않는다.
--
-- limit_id를 주지 않는다 — AUTO_INCREMENT가 채우고, UNIQUE (tier_id, limit_basis, limit_period)가
-- 재실행을 막는다.
-- ---------------------------------------------------------
INSERT IGNORE INTO `benefit_limit` (`tier_id`, `limit_basis`, `limit_period`, `limit_value`)
VALUES (162, 'AMOUNT', 'DAY', 10000.00);

-- ---------------------------------------------------------
-- ② 카페 혜택 두 건에 "1만원 이상 결제 시" 조건을 건다 (UPDATE라 그 자체로 멱등)
--
--   service 901 (톡톡 - Enjoy 서비스 - 카페 20%)
--     톡톡의 예비 혜택이다. 이걸 두면 결제 후 스타벅스 50%(133)가 일 한도로 죽어도 901이
--     살아남아 1,000원을 낸다. 노리와 동점이 되는데 display_order가 1번으로 5번인 노리보다
--     앞서서, 정렬 3단(안정 정렬 = 기존 표시 순서)에 걸려 **톡톡이 그대로 1위로 남는다.**
--
--   service 54 (All Pass - 일반 할인 - 커피전문점 20%)
--     "혜택 받을 수 없음" 카드를 만드는 장치다. 그냥 두면 5,000원에서 1,000원이라 노리와
--     동점인데 display_order가 2번이라 노리의 2위를 빼앗는다. 같은 카드의 카페·디저트
--     혜택(902)은 이미 min_tx_amount가 10,000이라 함께 빠지고, 결과적으로
--     "10,000원 이상 결제해야 받을 수 있는 혜택이에요"가 나온다.
--
-- ⚠️ All Pass를 "오늘 혜택 횟수 소진"으로 떨어뜨리려던 설계는 동작하지 않는다.
-- tier 58(service 54, COUNT DAY 1)은 BenefitMapper.findLimits가 도달할 수 없는 행이다 —
-- service 54는 plan_group_id가 10이라 findLimits가 plan_group 쪽 tier(21·22·23, 월 통합
-- 한도)만 고르고, service_id로 묶인 tier 58은 영영 선택되지 않는다. tier 57도 같은 이유로
-- 죽어 있다. 이 시드가 만든 문제가 아니라 기존 참조 데이터의 상태다.
-- ---------------------------------------------------------
UPDATE `benefit_service` SET `min_tx_amount` = 10000.00 WHERE `service_id` IN (54, 901);

-- ---------------------------------------------------------
-- ③ 시연 계정 3개 — login_id가 UNIQUE라 INSERT IGNORE로 멱등하다
--
-- 비밀번호(11112222)와 결제 PIN(123456) 해시는 fitwallet123의 것을 그대로 재사용한다 —
-- 네 계정의 자격증명이 같아야 시연자가 헷갈리지 않는다.
-- ---------------------------------------------------------
INSERT IGNORE INTO `users` (`login_id`, `name`, `phone`, `password_hash`, `payment_pin_hash`,
                            `is_location_agreed`, `is_marketing_agreed`)
VALUES ('demo1', '김데모', '010-1234-5679',
        '$2a$10$fVOyYs72w2Bos1Yqg9kAzO5R7muqmDB65Q.ZnXFRIpnZ2LWyj8Fou',
        '$2a$10$VFxQaZFMVYRlbzkFiDmDuuMyKrT58iJNqNPlMzchKkOiMh4y7DlGq', 1, 0),
       ('demo2', '이데모', '010-1234-5680',
        '$2a$10$fVOyYs72w2Bos1Yqg9kAzO5R7muqmDB65Q.ZnXFRIpnZ2LWyj8Fou',
        '$2a$10$VFxQaZFMVYRlbzkFiDmDuuMyKrT58iJNqNPlMzchKkOiMh4y7DlGq', 1, 0),
       ('demo3', '박데모', '010-1234-5681',
        '$2a$10$fVOyYs72w2Bos1Yqg9kAzO5R7muqmDB65Q.ZnXFRIpnZ2LWyj8Fou',
        '$2a$10$VFxQaZFMVYRlbzkFiDmDuuMyKrT58iJNqNPlMzchKkOiMh4y7DlGq', 1, 0);

-- ---------------------------------------------------------
-- ④ 보유 카드 — fitwallet123과 같은 5장, 같은 display_order
--
-- display_order가 곧 정렬 3단(안정 정렬)의 재료라 순서를 바꾸면 동점 카드의 순위가 달라진다.
--   1 청춘대로 톡톡(47) · 2 All Pass(15) · 3 Pick E 체크(20) · 4 Z everyday(21) · 5 노리 체크(43)
--
-- user_id를 조회해서 넣고, UNIQUE (user_id, card_product_id)가 재실행을 막는다.
-- UNION ALL 첫 행의 NULL은 CAST로 타입을 못박는다 — 안 하면 뒤 행의 문자열이 잘릴 수 있다.
-- ---------------------------------------------------------
INSERT IGNORE INTO `user_card` (`user_id`, `card_product_id`, `first4`, `last4`, `expiry_date`,
                                `display_order`, `bank_name`, `balance`, `credit_limit`,
                                `scheduled_payment_amount`)
SELECT u.user_id, c.card_product_id, c.first4, c.last4, c.expiry_date,
       c.display_order, c.bank_name, c.balance, c.credit_limit, c.scheduled_payment_amount
FROM `users` u
         JOIN (SELECT 47 AS card_product_id, '5327' AS first4, '8115' AS last4,
                      DATE '2028-09-30' AS expiry_date, 1 AS display_order,
                      CAST(NULL AS CHAR(50)) AS bank_name,
                      CAST(NULL AS DECIMAL(15, 2)) AS balance,
                      3000000.00 AS credit_limit, 89800.00 AS scheduled_payment_amount
               UNION ALL SELECT 15, '4092', '3477', DATE '2027-11-30', 2, NULL, NULL, 2000000.00, 259200.00
               UNION ALL SELECT 20, '4092', '1258', DATE '2029-03-31', 3, '신한은행', 340000.00, NULL, NULL
               UNION ALL SELECT 21, '4155', '6721', DATE '2028-05-31', 4, NULL, NULL, 1500000.00, 202000.00
               UNION ALL SELECT 43, '5327', '4584', DATE '2027-07-31', 5, 'KB국민은행', 1150000.00, NULL, NULL) c
WHERE u.login_id IN ('demo1', 'demo2', 'demo3');

-- ---------------------------------------------------------
-- ⑤ demo_reset_scenario() — 날짜에 매달린 부분의 정본
-- ---------------------------------------------------------
DROP PROCEDURE IF EXISTS `demo_reset_scenario`;

DELIMITER //
CREATE PROCEDURE `demo_reset_scenario`()
BEGIN
    DECLARE pm_a DATETIME;
    DECLARE pm_b DATETIME;
    DECLARE pm_c DATETIME;
    DECLARE today_a DATETIME;

    -- 전월 실적 창은 BenefitMapper.findPrevMonthSpends 기준으로
    -- "지난달 1일 00:00 이상, 이번 달 1일 00:00 미만"이다. 5·13·22일은 어느 달에나 있다.
    SET pm_a = DATE_FORMAT(NOW() - INTERVAL 1 MONTH, '%Y-%m-05 12:30:00');
    SET pm_b = DATE_FORMAT(NOW() - INTERVAL 1 MONTH, '%Y-%m-13 18:40:00');
    SET pm_c = DATE_FORMAT(NOW() - INTERVAL 1 MONTH, '%Y-%m-22 15:10:00');

    -- 일 한도 창은 DefaultBenefitService.resolvePeriodStart 기준으로 오늘 00:00 이상이다.
    SET today_a = CONCAT(CURDATE(), ' 09:10:00');

    -- demo1~3은 합성 계정이라 거래를 통째로 지운다.
    DELETE pt FROM payment_transaction pt
        JOIN user_card uc ON uc.user_card_id = pt.user_card_id
        JOIN users u ON u.user_id = uc.user_id
    WHERE u.login_id IN ('demo1', 'demo2', 'demo3');

    -- fitwallet123은 공용 계정이라 오늘 것만, 그중에서도 이 시나리오가 만든 행과
    -- 시연 중 앱으로 만든 결제만 지운다. V900~V909의 과거 거래는 건드리지 않는다.
    DELETE pt FROM payment_transaction pt
        JOIN user_card uc ON uc.user_card_id = pt.user_card_id
        JOIN users u ON u.user_id = uc.user_id
    WHERE u.login_id = 'fitwallet123'
      AND pt.paid_at >= CURDATE()
      AND (pt.applied_tier_id = 162 OR pt.payment_session_id IS NOT NULL);

    -- 전월 실적(demo1~3). 카드별 합계는 fitwallet123과 같은 값으로 맞췄다.
    --   톡톡 384,900 · All Pass 822,300 · Pick E 808,400 · Z everyday 900,900 · 노리 705,900
    -- 각 카드의 스타벅스/카페 혜택 문턱(min_payment_amount)을 전부 넘긴다.
    -- applied_tier_id는 NULL이다 — 한도 집계에 끼어들면 안 된다.
    INSERT INTO payment_transaction
        (user_card_id, store_id, amount, discount_amount, final_amount, paid_at,
         is_used_app, is_eligible, applied_benefit_service_id, applied_tier_id)
    SELECT uc.user_card_id, h.store_id, h.amount, 0.00, h.amount, h.paid_at, 0, 1, NULL, NULL
    FROM user_card uc
             JOIN users u ON u.user_id = uc.user_id
             JOIN (
                  SELECT 47 AS card_product_id, 187 AS store_id, 152000.00 AS amount, pm_a AS paid_at
                  UNION ALL SELECT 47, 195, 128900.00, pm_b
                  UNION ALL SELECT 47, 91, 104000.00, pm_c
                  UNION ALL SELECT 15, 187, 312000.00, pm_a
                  UNION ALL SELECT 15, 195, 268300.00, pm_b
                  UNION ALL SELECT 15, 91, 242000.00, pm_c
                  UNION ALL SELECT 20, 187, 305000.00, pm_a
                  UNION ALL SELECT 20, 195, 264400.00, pm_b
                  UNION ALL SELECT 20, 91, 239000.00, pm_c
                  UNION ALL SELECT 21, 187, 348000.00, pm_a
                  UNION ALL SELECT 21, 195, 296900.00, pm_b
                  UNION ALL SELECT 21, 91, 256000.00, pm_c
                  UNION ALL SELECT 43, 187, 268000.00, pm_a
                  UNION ALL SELECT 43, 195, 229900.00, pm_b
                  UNION ALL SELECT 43, 91, 208000.00, pm_c) h ON h.card_product_id = uc.card_product_id
    WHERE u.login_id IN ('demo1', 'demo2', 'demo3')
      AND uc.is_deleted = 0;

    -- 오늘 소진분. 네 계정 모두 같은 1건이다.
    -- 톡톡(tier 162, 일 한도 10,000원): 스타벅스 화양삼거리점 15,000원 결제 → 50% = 7,500원 할인.
    -- 남은 일 한도가 2,500원이 되어, 5,000원 조회 시 정가 2,500원과 잔여가 같아진다.
    -- 여기서 5,000원을 더 결제하면 2,500원이 쌓여 10,000원이 전액 소진된다.
    INSERT INTO payment_transaction
        (user_card_id, store_id, amount, discount_amount, final_amount, paid_at,
         is_used_app, is_eligible, applied_benefit_service_id, applied_tier_id)
    SELECT uc.user_card_id, 21, 15000.00, 7500.00, 7500.00, today_a, 0, 1, 133, 162
    FROM user_card uc
             JOIN users u ON u.user_id = uc.user_id
    WHERE u.login_id IN ('fitwallet123', 'demo1', 'demo2', 'demo3')
      AND uc.card_product_id = 47
      AND uc.is_deleted = 0;
END //
DELIMITER ;

CALL `demo_reset_scenario`();
