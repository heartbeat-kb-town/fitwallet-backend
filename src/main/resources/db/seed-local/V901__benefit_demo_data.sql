-- =========================================================
-- V901 — 혜택 추천 데모 데이터 (컴포즈커피 세종대학교점 기준)
--
-- V900 만으로는 컴포즈커피 세종대학교점(store_id = 1)에서 추천 가능한 카드가 0장이다.
-- 보유 카드 5장 중 4장이 NO_BENEFIT, 1장이 전월실적 미달이라 화면이 비어 보인다.
-- 원인은 두 가지다.
--   1. 컴포즈커피는 store.brand_id 가 NULL 이라 BRAND 스코프 커피 혜택(스타벅스·투썸·
--      이디야 등)이 전부 비켜간다
--   2. 카테고리(카페/디저트) INDUSTRY 스코프 혜택을 가진 카드가 신한 Pick E 체크 하나뿐이고,
--      그마저 전월실적 20만원 조건에 178,400원으로 미달이다
--
-- 이 파일은 판정 결과의 모든 갈래가 한 화면에 보이도록 보유 카드와 결제 내역만 더한다.
-- 5,000원 결제 기준 목표 라인업:
--
--   AVAILABLE 1위          신한 Deep Store        커피/제과점 10% 할인          →   500원
--   AVAILABLE 2위          신한 O2O               오프라인 Pay 5% 할인          →   250원
--   AVAILABLE 3위          신한 Deep Dream        모두드림 0.7% 적립            →    35 마이신한포인트
--   LIMIT_EXHAUSTED        신한 Pick E 체크       일 1회 적립 한도를 오늘 소진
--   PREV_SPEND_NOT_MET     KB국민 굿데이 플래티늄 전월실적 30만원 필요 / 12만원
--   NO_BENEFIT             기존 4장               커피 혜택이 브랜드 한정
--
-- ⚠️ 참조 데이터(benefit_service·benefit_tier·benefit_limit)는 손대지 않는다. 실제 카드사
-- 혜택이므로 데모를 위해 값을 바꾸면 모든 환경(운영 포함)의 판정이 함께 틀어진다.
-- 여기서 만드는 것은 전부 "그 규칙을 어떤 상태로 만나는가"뿐이다 — 전월실적과 한도 사용량.
--
-- ⚠️ 날짜는 NOW() 기준 상대값이다. V900 은 고정 날짜(2026-03~07)를 쓰지만 여기서는 쓸 수 없다 —
-- LIMIT_EXHAUSTED 는 "오늘"과 "이번 달"의 한도 사용량을 봐야 하고(DefaultBenefitService
-- #resolvePeriodStart), 전월실적은 "직전 달"을 본다. 고정 날짜로 넣으면 적재한 달에만 맞고
-- 다음 달이 되면 전월실적이 0으로 떨어져 라인업이 통째로 무너진다.
--
-- ⚠️ 그래도 Flyway 는 이 파일을 한 번만 실행하므로, 적재 시점의 NOW() 로 값이 굳는다.
-- 오래된 볼륨에서 라인업이 어긋나 보이면 docker compose down -v 로 다시 적재한다.
--
-- 범위 밖: MIN_TX_AMOUNT_NOT_MET(건당 최소금액 미달). 카페/디저트 INDUSTRY 혜택 중
-- min_tx_amount > 0 인 것이 하나도 없어 컴포즈커피에서는 참조 데이터를 왜곡해야만 만들 수 있다.
-- 스타벅스 세종대점(store_id = 20)에서 KB국민 노리 체크카드의 "5,000원 이상" 조건으로 이미 나온다.
-- =========================================================

SET NAMES utf8mb4;

SET @prev_start := DATE_FORMAT(NOW() - INTERVAL 1 MONTH, '%Y-%m-01');
SET @month_start := DATE_FORMAT(NOW(), '%Y-%m-01');
SET @today := DATE(NOW());

-- ---------------------------------------------------------
-- user_card — 보유 카드 4장 추가
--
-- 기존 5장(user_card 1~5)은 건드리지 않는다. 결제 내역 355건이 FK 로 매달려 있고
-- 놓친 혜택 리포트·이용실적 데모가 그 위에 서 있어, 지우거나 바꾸면 다른 화면이 함께 깨진다.
--
-- 넷 다 신용카드라 bank_name·balance 는 NULL 이고 credit_limit 만 채운다(V900 과 같은 규칙).
-- scheduled_payment_amount 는 아래에서 만들 전월 사용액과 맞췄다.
-- ---------------------------------------------------------
INSERT INTO `user_card` (`user_card_id`, `user_id`, `card_product_id`, `first4`, `last4`, `expiry_date`, `display_order`, `bank_name`, `balance`, `credit_limit`, `scheduled_payment_amount`, `created_at`, `updated_at`, `is_deleted`) VALUES
    (6, 1, 11, '4092', '7215', '2029-04-30', 6, NULL, NULL, 2500000.00, 380000.00, @prev_start, @prev_start, 0),
    (7, 1,  4, '4092', '3048', '2028-11-30', 7, NULL, NULL, 1800000.00, 150000.00, @prev_start, @prev_start, 0),
    (8, 1,  2, '4092', '9663', '2030-02-28', 8, NULL, NULL, 2000000.00,  90000.00, @prev_start, @prev_start, 0),
    (9, 1, 49, '5327', '5170', '2029-08-31', 9, NULL, NULL, 3000000.00, 120000.00, @prev_start, @prev_start, 0);

-- ---------------------------------------------------------
-- payment_transaction — 전월실적을 만드는 결제 내역
--
-- BenefitMapper.findPrevMonthSpends 는 is_eligible = 1 인 직전 달 결제의 amount 합이다.
-- 그 합이 benefit_service.min_payment_amount / benefit_tier.min_prev_month_spend 구간에
-- 어디로 떨어지느냐가 카드의 판정을 통째로 결정한다. 카드마다 목표 금액이 다른 이유다.
--
-- better_user_card_id 를 NULL 로 두는 것은 의도다 — MissedBenefitMapper 의 두 쿼리가
-- better_user_card_id IS NOT NULL 로 거르므로, 이 36건이 들어가도 놓친 혜택 리포트의
-- 기존 수치가 한 원도 변하지 않는다.
--
-- 혜택은 채우지 않는다(applied_* NULL). 여기 필요한 것은 "얼마를 썼는가"뿐이고,
-- 한도 사용량은 아래 블록에서 따로 만든다.
-- ---------------------------------------------------------

-- user_card 6 (신한 Deep Store) — 380,000원.
-- plan_group 9 의 tier 17(30만 이상 60만 미만)에 들어가야 월 한도가 15,000원이 된다.
-- 60만을 넘기면 tier 18(25,000원)로 올라가 아래에서 만드는 소진율이 달라진다.
INSERT INTO `payment_transaction` (`payment_transaction_id`, `user_card_id`, `store_id`, `amount`, `discount_amount`, `final_amount`, `paid_at`, `is_used_app`, `is_eligible`) VALUES
    (501, 6,  92, 46000.00, 0.00, 46000.00, @prev_start + INTERVAL  2 DAY + INTERVAL 19 HOUR, 0, 1),
    (502, 6,  97, 38000.00, 0.00, 38000.00, @prev_start + INTERVAL  4 DAY + INTERVAL 12 HOUR, 0, 1),
    (503, 6, 100, 31000.00, 0.00, 31000.00, @prev_start + INTERVAL  6 DAY + INTERVAL 18 HOUR, 0, 1),
    (504, 6, 103, 24500.00, 0.00, 24500.00, @prev_start + INTERVAL  8 DAY + INTERVAL 13 HOUR, 0, 1),
    (505, 6,  98, 19000.00, 0.00, 19000.00, @prev_start + INTERVAL 11 DAY + INTERVAL 20 HOUR, 0, 1),
    (506, 6,  53, 39000.00, 0.00, 39000.00, @prev_start + INTERVAL 13 DAY + INTERVAL 11 HOUR, 0, 1),
    (507, 6,  46, 37000.00, 0.00, 37000.00, @prev_start + INTERVAL 15 DAY + INTERVAL 21 HOUR, 0, 1),
    (508, 6,  50, 33000.00, 0.00, 33000.00, @prev_start + INTERVAL 17 DAY + INTERVAL  9 HOUR, 0, 1),
    (509, 6,  49, 28500.00, 0.00, 28500.00, @prev_start + INTERVAL 19 DAY + INTERVAL 22 HOUR, 0, 1),
    (510, 6,  19, 28000.00, 0.00, 28000.00, @prev_start + INTERVAL 21 DAY + INTERVAL 15 HOUR, 0, 1),
    (511, 6,   1, 22000.00, 0.00, 22000.00, @prev_start + INTERVAL 23 DAY + INTERVAL  8 HOUR, 0, 1),
    (512, 6,   3, 18000.00, 0.00, 18000.00, @prev_start + INTERVAL 25 DAY + INTERVAL 10 HOUR, 0, 1),
    (513, 6,  15, 16000.00, 0.00, 16000.00, @prev_start + INTERVAL 26 DAY + INTERVAL 16 HOUR, 0, 1);

-- user_card 7 (신한 O2O) — 150,000원.
-- service 12 는 min_payment_amount 가 0이라 실적 조건이 없다. 금액은 카드가 살아 있어
-- 보이도록 채우는 것뿐이고, 이 카드가 AVAILABLE 이 되는 데 실적은 관여하지 않는다.
INSERT INTO `payment_transaction` (`payment_transaction_id`, `user_card_id`, `store_id`, `amount`, `discount_amount`, `final_amount`, `paid_at`, `is_used_app`, `is_eligible`) VALUES
    (514, 7,  98, 38000.00, 0.00, 38000.00, @prev_start + INTERVAL  3 DAY + INTERVAL 20 HOUR, 0, 1),
    (515, 7, 103, 32000.00, 0.00, 32000.00, @prev_start + INTERVAL  9 DAY + INTERVAL 12 HOUR, 0, 1),
    (516, 7,  50, 35000.00, 0.00, 35000.00, @prev_start + INTERVAL 14 DAY + INTERVAL 21 HOUR, 0, 1),
    (517, 7,  46, 27000.00, 0.00, 27000.00, @prev_start + INTERVAL 20 DAY + INTERVAL 18 HOUR, 0, 1),
    (518, 7,  19, 18000.00, 0.00, 18000.00, @prev_start + INTERVAL 24 DAY + INTERVAL 14 HOUR, 0, 1);

-- user_card 8 (신한 Deep Dream) — 90,000원.
-- 30만원 미만으로 눌러 둔 것이 핵심이다. 30만원을 넘기면 같은 카드의 더해드림(2.1%)·
-- 챙겨드림(3.5%)이 tier_ok 가 되면서 0.7% 기본적립을 밀어내고, 3위 자리가 2위로 올라가
-- 순위 시연이 흐트러진다.
INSERT INTO `payment_transaction` (`payment_transaction_id`, `user_card_id`, `store_id`, `amount`, `discount_amount`, `final_amount`, `paid_at`, `is_used_app`, `is_eligible`) VALUES
    (519, 8,  97, 33000.00, 0.00, 33000.00, @prev_start + INTERVAL  5 DAY + INTERVAL 13 HOUR, 0, 1),
    (520, 8,  49, 28000.00, 0.00, 28000.00, @prev_start + INTERVAL 12 DAY + INTERVAL 19 HOUR, 0, 1),
    (521, 8,  15, 17500.00, 0.00, 17500.00, @prev_start + INTERVAL 18 DAY + INTERVAL 10 HOUR, 0, 1),
    (522, 8, 100, 11500.00, 0.00, 11500.00, @prev_start + INTERVAL 22 DAY + INTERVAL 18 HOUR, 0, 1);

-- user_card 9 (KB국민 굿데이 플래티늄) — 120,000원.
-- service 139 의 min_payment_amount 는 300,000원이다. 일부러 미달로 두어
-- PREV_SPEND_NOT_MET 을 만든다 — 이 카드가 이 파일에서 유일하게 "실적이 모자란" 사례다.
INSERT INTO `payment_transaction` (`payment_transaction_id`, `user_card_id`, `store_id`, `amount`, `discount_amount`, `final_amount`, `paid_at`, `is_used_app`, `is_eligible`) VALUES
    (523, 9,  92, 45000.00, 0.00, 45000.00, @prev_start + INTERVAL  7 DAY + INTERVAL 19 HOUR, 0, 1),
    (524, 9,  53, 31000.00, 0.00, 31000.00, @prev_start + INTERVAL 16 DAY + INTERVAL 11 HOUR, 0, 1),
    (525, 9,   3, 26000.00, 0.00, 26000.00, @prev_start + INTERVAL 21 DAY + INTERVAL  9 HOUR, 0, 1),
    (526, 9,  46, 18000.00, 0.00, 18000.00, @prev_start + INTERVAL 25 DAY + INTERVAL 20 HOUR, 0, 1);

-- user_card 3 (신한 Pick E 체크) — 210,000원 추가.
-- 이 카드만 V900 에 이미 결제 내역이 있다. 그런데도 210,000원을 통째로 채우는 것은,
-- V900 의 7월 거래가 고정 날짜라 "직전 달"에서 언젠가 빠져나가기 때문이다.
-- 이 다섯 건만으로 service 73 의 20만원 조건을 넘겨야 언제 적재해도 라인업이 유지된다.
INSERT INTO `payment_transaction` (`payment_transaction_id`, `user_card_id`, `store_id`, `amount`, `discount_amount`, `final_amount`, `paid_at`, `is_used_app`, `is_eligible`) VALUES
    (527, 3,  92, 62000.00, 0.00, 62000.00, @prev_start + INTERVAL  1 DAY + INTERVAL 19 HOUR, 0, 1),
    (528, 3,  50, 48000.00, 0.00, 48000.00, @prev_start + INTERVAL  8 DAY + INTERVAL 21 HOUR, 0, 1),
    (529, 3,  97, 39000.00, 0.00, 39000.00, @prev_start + INTERVAL 15 DAY + INTERVAL 12 HOUR, 0, 1),
    (530, 3,  53, 33000.00, 0.00, 33000.00, @prev_start + INTERVAL 20 DAY + INTERVAL 17 HOUR, 0, 1),
    (531, 3,  49, 28000.00, 0.00, 28000.00, @prev_start + INTERVAL 24 DAY + INTERVAL 22 HOUR, 0, 1);

-- ---------------------------------------------------------
-- payment_transaction — 한도 사용량을 만드는 결제 내역
--
-- BenefitMapper.findUsage 는 (user_card_id, applied_tier_id, paid_at >= 기간시작) 으로
-- 집계한다. 그래서 여기서는 applied_tier_id 를 반드시 채워야 하고, paid_at 이 기간 안에
-- 들어와야 한다 — 위 블록의 전월실적용 행과 성격이 정반대다.
-- ---------------------------------------------------------

-- user_card 6 (신한 Deep Store) — tier 17 의 월 한도 15,000원 중 8,000원 소진.
-- 소진이 아니라 "부분 사용"이 목적이다. 잔여 7,000원은 5,000원 결제의 10%(500원)를
-- 자르지 않으므로 이 카드는 AVAILABLE 1위로 남는다. 한도 잔여가 산출액을 깎는 경로
-- (BenefitAmountCalculator 4단계)가 살아 있다는 것만 보여준다.
--
-- paid_at 은 GREATEST 로 이번 달 1일 아래로 내려가지 않게 막는다. 월초에 적재하면
-- NOW() - INTERVAL n DAY 가 지난달로 넘어가 한도 집계에서 빠지고, 그 순간 이 카드의
-- 사용량이 0이 되어 시연이 달라진다.
INSERT INTO `payment_transaction` (`payment_transaction_id`, `user_card_id`, `store_id`, `amount`, `discount_amount`, `final_amount`, `paid_at`, `is_used_app`, `is_eligible`, `applied_benefit_service_id`, `applied_tier_id`) VALUES
    (532, 6,  3, 36000.00, 3600.00, 32400.00, GREATEST(@month_start + INTERVAL  9 HOUR, NOW() - INTERVAL 9 DAY), 0, 1, 46, 17),
    (533, 6, 19, 24000.00, 2400.00, 21600.00, GREATEST(@month_start + INTERVAL 14 HOUR, NOW() - INTERVAL 6 DAY), 0, 1, 46, 17),
    (534, 6, 15, 12000.00, 1200.00, 10800.00, GREATEST(@month_start + INTERVAL 10 HOUR, NOW() - INTERVAL 4 DAY), 0, 1, 46, 17),
    (535, 6,  1,  8000.00,  800.00,  7200.00, GREATEST(@month_start + INTERVAL 16 HOUR, NOW() - INTERVAL 2 DAY), 0, 1, 46, 17);

-- user_card 3 (신한 Pick E 체크) — tier 69 의 일 1회 COUNT 한도를 오늘 소진.
--
-- 이 한 건이 LIMIT_EXHAUSTED 의 전부다. tier 69 에는 COUNT/DAY 1회와 COUNT/MONTH 3회가
-- 함께 걸려 있는데, 하루 1회가 먼저 막히고 EXHAUSTED_PRIORITY 가 DAY 를 우선하므로
-- "오늘 받을 수 있는 혜택 횟수를 모두 사용했어요."가 나온다.
--
-- 같은 컴포즈커피에서 오늘 아침에 이미 한 잔 마신 상황이다 — 시연자가 다시 이 가맹점을
-- 조회했을 때 왜 이 카드가 빠졌는지 화면만 보고 납득할 수 있게 store_id 를 맞췄다.
-- 적립은 10%(500 마이신한포인트)이고 건당 캡 1,000 포인트에 걸리지 않는다.
INSERT INTO `payment_transaction` (`payment_transaction_id`, `user_card_id`, `store_id`, `amount`, `discount_amount`, `final_amount`, `paid_at`, `is_used_app`, `is_eligible`, `applied_benefit_service_id`, `applied_tier_id`) VALUES
    (536, 3, 1, 5000.00, 500.00, 4500.00, @today + INTERVAL 8 HOUR + INTERVAL 12 MINUTE, 1, 1, 73, 69);

-- 감사 컬럼을 결제 시각에 맞춘다. V900 이 created_at = paid_at 으로 넣어 둔 것과 같은 규칙인데,
-- 위 INSERT 들이 상대 날짜 식이라 컬럼마다 같은 식을 세 번 반복하는 대신 한 번에 정리한다.
-- updated_at 은 ON UPDATE CURRENT_TIMESTAMP 이지만 명시 대입이 우선하므로 그대로 들어간다.
UPDATE `payment_transaction`
SET `created_at` = `paid_at`, `updated_at` = `paid_at`
WHERE `payment_transaction_id` BETWEEN 501 AND 536;
