-- =========================================================
-- V905 — 시연용 혜택 규칙 (컴포즈커피 · 알고 · 동부플란트치과의원)
--
-- 시연 계정(user_id = 1)이 보유한 카드 5장으로 세 가맹점 각각에서
-- AVAILABLE 3장(1·2·3등) + CONDITION_NOT_MET 1장 + NO_BENEFIT 1장이
-- 나오도록 혜택 규칙을 얹는다. 결제내역과 한도 사용량은 V906 이 만든다.
--
-- ⚠️ V901 은 "참조 데이터(benefit_service·benefit_tier·benefit_limit)를 손대지 않는다"고
-- 못박았고 그 원칙은 여전히 옳다 — 단, 그것은 db/migration 이야기다. 실제 카드사 혜택이
-- 아니라 시연을 위해 지어낸 규칙이므로 db/seed-local 에 둔다. 운영은 flyway.locations 에
-- 이 디렉터리를 넣지 않으므로(§13) 운영 판정은 이 파일의 영향을 받지 않는다.
--
-- ⚠️ 세 가맹점 모두 store.brand_id 가 NULL 이다. BenefitMapper.findCandidates 의 BRAND
-- 스코프 분기가 `#{storeBrandId} IS NOT NULL` 을 요구하므로 브랜드 한정 혜택은 전부 비켜간다.
-- 그래서 여기서 만드는 시연용 혜택은 전부 INDUSTRY 스코프다.
--
-- ID 는 900번대를 명시적으로 쓴다. AUTO_INCREMENT 에 맡기면 V2 참조 데이터가 늘어날 때마다
-- 값이 밀려, 아래 tier·limit·service_category 가 서로를 가리키지 못한다.
-- =========================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------
-- 1. 시연용 혜택 서비스
--
-- 카테고리: 1 = 카페/디저트, 4 = 푸드, 5 = 병원, 3 = 쇼핑, 2 = 편의점/마트
-- plan_group_id 가 채워진 행은 그 그룹의 통합 한도를 쓴다(findLimits 가 plan_group 우선).
-- NULL 인 행은 아래 2번에서 자기 tier 를 따로 받는다.
--
-- 910·911 은 시연 가맹점과 무관하다. "받은 혜택"의 총 포인트를 채우려면 ACCUMULATE 거래가
-- 필요한데 보유 카드 중 적립형이 신한 Pick E 체크 한 장뿐이라, 시연 카테고리(1·4·5)를
-- 건드리지 않는 쇼핑·편의점 업종에 적립 혜택을 두 건 더한다.
-- ---------------------------------------------------------
INSERT INTO `benefit_service`
    (`service_id`, `card_product_id`, `plan_group_id`, `benefit_name`, `benefit_type`, `value_type`,
     `value_number`, `scope_type`, `min_payment_amount`, `max_payment_amount`,
     `min_tx_amount`, `per_tx_limit_amount`, `point_currency_id`)
VALUES
    -- 컴포즈커피(카페/디저트) 라인업
    (901, 47, NULL, '카페 업종 할인',            'CASHBACK',   'RATE', 20.00, 'INDUSTRY', 300000.00, NULL,     0.00, NULL,    NULL),
    (902, 15,   10, '카페 업종 할인',            'CASHBACK',   'RATE', 10.00, 'INDUSTRY', 300000.00, NULL, 10000.00, NULL,    NULL),
    (903, 21,   13, '기본혜택 - 카페 업종',       'CASHBACK',   'RATE',  5.00, 'INDUSTRY', 500000.00, NULL,     0.00, NULL,    NULL),
    -- 알고(푸드) 라인업
    (904, 43, NULL, '푸드 업종 할인',            'CASHBACK',   'RATE',  5.00, 'INDUSTRY', 200000.00, NULL, 10000.00, 2000.00, NULL),
    (905, 20, NULL, '푸드 업종 적립',            'ACCUMULATE', 'RATE',  5.00, 'INDUSTRY', 200000.00, NULL,     0.00, 2000.00,    1),
    -- 동부플란트치과의원(병원) 라인업
    (906, 15,   10, '병원 업종 할인',            'CASHBACK',   'RATE', 10.00, 'INDUSTRY', 300000.00, NULL,     0.00, NULL,    NULL),
    (907, 43,   15, '병원 업종 할인',            'CASHBACK',   'RATE',  7.00, 'INDUSTRY', 200000.00, NULL,     0.00, NULL,    NULL),
    (908, 20, NULL, '병원 업종 적립',            'ACCUMULATE', 'RATE',  5.00, 'INDUSTRY', 200000.00, NULL,     0.00, 2000.00,    1),
    (909, 47, NULL, '병원 업종 프리미엄 할인',     'CASHBACK',   'RATE', 15.00, 'INDUSTRY', 500000.00, NULL,     0.00, NULL,    NULL),
    -- 시연 가맹점과 무관 — "받은 혜택"의 총 포인트 물량용
    (910, 43, NULL, '쇼핑 업종 적립',            'ACCUMULATE', 'RATE',  3.00, 'INDUSTRY', 200000.00, NULL,     0.00, 3000.00,    5),
    (911, 21, NULL, '편의점·마트 업종 적립',      'ACCUMULATE', 'RATE',  3.00, 'INDUSTRY', 300000.00, NULL,     0.00, 3000.00,    3);

INSERT INTO `service_category` (`service_id`, `category_id`) VALUES
    (901, 1), (902, 1), (903, 1),
    (904, 4), (905, 4),
    (906, 5), (907, 5), (908, 5), (909, 5),
    (910, 3), (911, 2);

-- ---------------------------------------------------------
-- 2. plan_group 이 없는 서비스의 tier + 한도
--
-- ck_benefit_tier_xor 때문에 plan_group_id 와 service_id 중 하나만 채운다.
-- plan_group 소속(902·903·906·907)은 그룹 tier 를 쓰므로 여기에 없다.
--
-- tier 905 의 COUNT MONTH 2 는 의도된 함정이다 — V906 이 당월 2건을 미리 찍어
-- 알고에서 신한 Pick E 체크가 LIMIT_EXHAUSTED 로 떨어지게 만든다.
--
-- tier 904 의 AMOUNT MONTH 5,000 도 의도된 값이다. V906 이 당월 4,100원을 써 두어
-- 잔여 900원이 남고, 알고에서 1,500원짜리 혜택이 900원으로 잘리는 화면이 나온다.
-- 3등 카드에 둔 이유는 반복 시연 때문이다 — 시연에서 누르는 것은 1등 카드라
-- 이 tier 의 잔여가 줄지 않아 몇 번을 돌려도 잘린 금액이 그대로 남는다.
-- ---------------------------------------------------------
INSERT INTO `benefit_tier`
    (`tier_id`, `plan_group_id`, `service_id`, `tier_order`, `min_prev_month_spend`, `max_prev_month_spend`)
VALUES
    (901, NULL, 901, 1, 0.00, NULL),
    (904, NULL, 904, 1, 0.00, NULL),
    (905, NULL, 905, 1, 0.00, NULL),
    (908, NULL, 908, 1, 0.00, NULL),
    (909, NULL, 909, 1, 0.00, NULL),
    (910, NULL, 910, 1, 0.00, NULL),
    (911, NULL, 911, 1, 0.00, NULL);

INSERT INTO `benefit_limit` (`limit_id`, `tier_id`, `limit_basis`, `limit_period`, `limit_value`) VALUES
    (901, 901, 'AMOUNT', 'MONTH', 40000.00),
    (904, 904, 'AMOUNT', 'MONTH',  5000.00),   -- 의도적으로 빡빡하다(위 주석)
    (905, 905, 'COUNT',  'MONTH',     2.00),   -- 의도적으로 소진시킨다(위 주석)
    (908, 908, 'COUNT',  'MONTH',    12.00),
    (909, 909, 'AMOUNT', 'MONTH', 40000.00),
    (910, 910, 'POINT',  'MONTH', 20000.00),
    (911, 911, 'POINT',  'MONTH', 20000.00);

-- ---------------------------------------------------------
-- 3. 기존 한도 상향 — 반복 시연을 버티게 한다
--
-- 실제 카드사 한도는 시연 한 번에 소진될 만큼 빡빡하다. 예컨대 KB국민 청춘대로 톡톡의
-- 버거/패스트푸드 20% 는 월 5,000원이라, 알고에서 30,000원을 한 번 결제하면 그대로 바닥나
-- 다음 판정에서 1등 카드가 LIMIT_EXHAUSTED 로 사라진다.
--
-- limit_id 가 아니라 (tier_id, limit_basis, limit_period) 로 찾는다 — 유니크 키라 한 행만
-- 걸리고, 참조 데이터의 AUTO_INCREMENT 가 밀려도 안전하다.
-- ---------------------------------------------------------
UPDATE `benefit_limit` SET `limit_value` =  30000.00 WHERE `tier_id` =  21 AND `limit_basis` = 'AMOUNT' AND `limit_period` = 'MONTH';  -- 신한 All Pass 통합(30~50만)
UPDATE `benefit_limit` SET `limit_value` =  40000.00 WHERE `tier_id` =  22 AND `limit_basis` = 'AMOUNT' AND `limit_period` = 'MONTH';  -- 신한 All Pass 통합(50~100만)
UPDATE `benefit_limit` SET `limit_value` =  50000.00 WHERE `tier_id` =  23 AND `limit_basis` = 'AMOUNT' AND `limit_period` = 'MONTH';  -- 신한 All Pass 통합(100만~)
UPDATE `benefit_limit` SET `limit_value` =  60000.00 WHERE `tier_id` =  70 AND `limit_basis` = 'AMOUNT' AND `limit_period` = 'MONTH';  -- 현대 Z everyday 통합(50~100만)
UPDATE `benefit_limit` SET `limit_value` =  90000.00 WHERE `tier_id` =  71 AND `limit_basis` = 'AMOUNT' AND `limit_period` = 'MONTH';  -- 현대 Z everyday 통합(100만~)
UPDATE `benefit_limit` SET `limit_value` =  30000.00 WHERE `tier_id` = 131 AND `limit_basis` = 'AMOUNT' AND `limit_period` = 'MONTH';  -- KB 노리 통합(20~30만)
UPDATE `benefit_limit` SET `limit_value` =  50000.00 WHERE `tier_id` = 132 AND `limit_basis` = 'AMOUNT' AND `limit_period` = 'MONTH';  -- KB 노리 통합(30~50만)
UPDATE `benefit_limit` SET `limit_value` =  60000.00 WHERE `tier_id` = 133 AND `limit_basis` = 'AMOUNT' AND `limit_period` = 'MONTH';  -- KB 노리 통합(50~100만)
UPDATE `benefit_limit` SET `limit_value` =  80000.00 WHERE `tier_id` = 134 AND `limit_basis` = 'AMOUNT' AND `limit_period` = 'MONTH';  -- KB 노리 통합(100만~)
UPDATE `benefit_limit` SET `limit_value` =  30000.00 WHERE `tier_id` = 162 AND `limit_basis` = 'AMOUNT' AND `limit_period` = 'MONTH';  -- KB 청춘대로 스타벅스
UPDATE `benefit_limit` SET `limit_value` =  40000.00 WHERE `tier_id` = 163 AND `limit_basis` = 'AMOUNT' AND `limit_period` = 'MONTH';  -- KB 청춘대로 버거/패스트푸드

-- 신한 Pick E 체크의 적립 횟수 한도. 월 3회는 V906 이 채우는 당월 적립 거래만으로도
-- 차 버려서 컴포즈커피 라인업이 무너진다. 12회로 올린다.
UPDATE `benefit_limit` SET `limit_value` = 12.00 WHERE `tier_id` IN (68, 69) AND `limit_basis` = 'COUNT' AND `limit_period` = 'MONTH';

-- 일 1회 제한은 제거한다 — 남겨 두면 컴포즈커피에서 시연 결제를 한 번 한 뒤
-- 같은 날 재시연이 LIMIT_EXHAUSTED 로 막힌다(V901 이 일부러 만들었던 장면이다).
DELETE FROM `benefit_limit` WHERE `tier_id` IN (68, 69) AND `limit_basis` = 'COUNT' AND `limit_period` = 'DAY';
