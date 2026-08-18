-- =========================================================
-- V903 — 결제 내역 기간 확장 (로컬 데모)
--
-- V900 의 데모 거래는 2026-03-04 ~ 07-29 다섯 달뿐이라, 월 선택기가 다섯 칸에서 끝난다.
-- 이용 실적·결제 내역·리포트 모두 "지난달로 더 넘어가면 어떻게 보이는지"를 확인할 수 없다.
--
-- V900 블록(payment_transaction_id 1~355)을 통째로 두 번 과거로 복사해 2025-04-30 ~
-- 2026-07-29 로 늘린다. 없던 데이터를 새로 지어내지 않고 복사하는 이유는, V900 의 각 행이
-- 실제 혜택 규칙·전월실적·한도를 시간순으로 적용해 계산한 결과이기 때문이다
-- (discount_amount · applied_benefit_service_id · applied_tier_id · missed_amount).
-- 손으로 채우면 규칙과 어긋난 값이 리포트에 그대로 나간다.
--
-- ⚠️ 옮기는 폭은 달이 아니라 **날 수**다. 154일 = 22주, 308일 = 44주라 요일이 보존된다.
-- V900 은 평일 355건인데 `INTERVAL 5 MONTH` 로 옮기면 주말에 결제가 생긴다.
--   154일 : 2026-03-04(수) → 2025-10-01(수),  2026-07-29 → 2026-02-25
--   308일 : 2026-03-04(수) → 2025-04-30(수),  2026-07-29 → 2025-09-25
--
-- id 는 원본 + 100000 / + 200000 이다. 앱이 만드는 결제(auto_increment)와 겹치지 않게
-- 크게 띄웠다.
--
-- payment_session_id 는 전부 NULL 로 둔다. UNIQUE(1:1) 라 원본 352 의 세션을 같이 복사하면
-- 두 번째 복사에서 적재가 깨진다. 과거 거래에 앱 결제 세션이 없는 것이 자연스럽기도 하다.
--
-- transaction_status 는 원본을 따른다. V902 가 먼저 돌아 348 번이 CANCELED 이므로
-- 복사본에도 취소 거래가 한 건씩 섞인다 — 승인취소가 과거 달에서도 집계에서 빠지는지
-- 같이 확인된다.
--
-- ⚠️ 2026-03 의 전월실적이 달라진다. 지금까지 2026-02 는 비어 있어 실적이 0 이었는데
-- 이제 데이터가 생긴다. 실적·구간은 조회 시점에 거래에서 다시 계산하므로
-- (CardUsageTierStateCalculator) 화면 값은 맞게 나오지만, 2026-03 각 행에 **기록된**
-- applied_tier_id 는 그 시절 계산이라 새 전월실적과 어긋날 수 있다. 로컬 데모 한정으로
-- 감수한다 — 바로잡으려면 V900 을 통째로 다시 만들어야 한다.
--
-- V901 이 만든 혜택 추천 라인업은 건드리지 않는다. 그쪽은 NOW() 기준 이번 달·직전 달을
-- 보는데, 여기서 더하는 것은 2026-02 이전뿐이다.
-- =========================================================

SET NAMES utf8mb4;

INSERT INTO `payment_transaction` (
    `payment_transaction_id`, `user_card_id`, `store_id`, `payment_session_id`,
    `amount`, `discount_amount`, `final_amount`, `paid_at`, `transaction_status`,
    `is_used_app`, `is_eligible`, `applied_benefit_service_id`, `applied_tier_id`,
    `better_user_card_id`, `alternative_discount_amount`, `missed_amount`,
    `created_at`, `updated_at`
)
SELECT
    src.`payment_transaction_id` + shift.`id_offset`,
    src.`user_card_id`,
    src.`store_id`,
    NULL,
    src.`amount`,
    src.`discount_amount`,
    src.`final_amount`,
    DATE_SUB(src.`paid_at`, INTERVAL shift.`days` DAY),
    src.`transaction_status`,
    src.`is_used_app`,
    src.`is_eligible`,
    src.`applied_benefit_service_id`,
    src.`applied_tier_id`,
    src.`better_user_card_id`,
    src.`alternative_discount_amount`,
    src.`missed_amount`,
    DATE_SUB(src.`created_at`, INTERVAL shift.`days` DAY),
    DATE_SUB(src.`updated_at`, INTERVAL shift.`days` DAY)
FROM (
    SELECT * FROM `payment_transaction` WHERE `payment_transaction_id` BETWEEN 1 AND 355
) src
CROSS JOIN (
    SELECT 154 AS `days`, 100000 AS `id_offset`
    UNION ALL
    SELECT 308 AS `days`, 200000 AS `id_offset`
) shift;
