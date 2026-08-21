-- =========================================================
-- V908 — 카드 추천 2장을 미보유 KB국민카드로 고정한다
--
-- CardRecommendationEngine 은 미보유 카드 중 "증분 혜택(marginal)" 상위 2장을 뽑는다.
--   marginal = Σ max(0, 후보 카드가 그 카테고리에서 주는 혜택 − 보유 카드 최선(baseline))
-- 아무리 좋은 카드라도 내 보유 카드가 이미 커버하는 카테고리면 0으로 묻힌다.
--
-- 시연 계정의 최근 3개월 카테고리별 예상 지출(중앙값)과 baseline 은 이랬다.
--   카페/디저트 294,000 / 40,000   편의점·마트 454,600 / 45,460   쇼핑 267,800 / 13,390
--   푸드 424,500 / 42,450          병원 276,500 / 41,475          주유 772,800 / 60,000
-- 이 baseline 을 넉넉히 넘는 혜택을 미보유 KB국민카드 두 장에 붙여 1·2위를 잡는다.
--
--   54: KB국민 톡톡M 카드          — 카페/디저트 20% · 쇼핑 15% · 푸드 15%
--   44: KB국민 국민행복체크카드     — 편의점·마트 15% · 쇼핑 12% · 푸드 12%
--
-- ⚠️ 카드를 이 둘로 고른 이유는 "추천 이유" 문구 때문이다. 엔진은 카테고리를 훑다가 그 카드에서
-- 처음 만난 행을 문구 재료로 잡아 둔다(cardInfoMap.putIfAbsent) — 증분이 가장 큰 행이 아니라
-- 먼저 만난 행이다. 그래서 이미 전 카테고리를 덮는 혜택을 가진 카드에 붙이면 문구가 그쪽으로
-- 샌다. 실제로 50(WE:SH Daily)의 "기본 할인 - 전 가맹점"과 51(올라운드)의 "전 가맹점 적립"에
-- 밀려, 증분은 5만원대인데 문구는 "카페/디저트 0.5% 할인"·"카페/디저트 0.8% 적립"이 나왔다.
--
-- 54 는 편의점·마트(2)만, 44 는 병원(5)만 덮고 있어서 아래에서 더하는 혜택이 그 카드에서
-- 유일한 공급자가 된다. 덕분에 기존 행을 지우지 않고 더하기만으로 문구가 이 파일이 넣은
-- 혜택에서 나온다. 실측 결과는 이렇다.
--   54 → "카페/디저트 20% 할인, 전월 실적 300,000원 이상, 월 최대 80,000원 한도"
--   44 → "푸드 12% 할인, 전월 실적 400,000원 이상, 월 최대 70,000원 한도"
--
-- 어느 카테고리가 뽑힐지는 고정이 아니다. 순회 순서의 출처인 getMonthlyCategorySpends 에
-- ORDER BY 가 없어 카테고리 순서가 보장되지 않기 때문이다. 여기서 통제하는 것은 "이 카드의
-- 문구가 내가 넣은 혜택 중 하나에서 나온다"까지이고, 셋 중 어느 것인지는 아니다 —
-- 셋 다 그럴듯한 문구가 되도록 값을 잡아 둔 이유가 이것이다.
--
-- ⚠️ 두 장 모두 이 계정이 보유하지 않은 카드다. getRecommendedCards 는 user_card 에 있는
-- card_product_id 를 통째로 제외하므로(소프트 삭제 여부와 무관) 보유 카드에 붙이면 추천에
-- 아예 나오지 않는다. 미보유라 세 시연 가맹점 판정(findCandidates 는 보유 카드만 본다)에도
-- 영향이 없다.
-- =========================================================

SET NAMES utf8mb4;

INSERT INTO `benefit_service`
    (`service_id`, `card_product_id`, `plan_group_id`, `benefit_name`, `benefit_type`, `value_type`,
     `value_number`, `scope_type`, `min_payment_amount`, `max_payment_amount`,
     `min_tx_amount`, `per_tx_limit_amount`, `point_currency_id`)
VALUES
    -- 54: KB국민 톡톡M 카드 (기존 커버: 편의점·마트)
    (920, 54, NULL, '카페·디저트 할인', 'CASHBACK', 'RATE', 20.00, 'INDUSTRY', 300000.00, NULL, 0.00, NULL, NULL),
    (921, 54, NULL, '온라인쇼핑 할인',   'CASHBACK', 'RATE', 15.00, 'INDUSTRY', 300000.00, NULL, 0.00, NULL, NULL),
    (922, 54, NULL, '외식 할인',        'CASHBACK', 'RATE', 15.00, 'INDUSTRY', 300000.00, NULL, 0.00, NULL, NULL),
    -- 44: KB국민 국민행복체크카드 (기존 커버: 병원)
    (923, 44, NULL, '편의점·마트 할인',  'CASHBACK', 'RATE', 15.00, 'INDUSTRY', 400000.00, NULL, 0.00, NULL, NULL),
    (924, 44, NULL, '온라인쇼핑 할인',   'CASHBACK', 'RATE', 12.00, 'INDUSTRY', 400000.00, NULL, 0.00, NULL, NULL),
    (925, 44, NULL, '외식 할인',        'CASHBACK', 'RATE', 12.00, 'INDUSTRY', 400000.00, NULL, 0.00, NULL, NULL);

INSERT INTO `service_category` (`service_id`, `category_id`) VALUES
    (920, 1), (921, 3), (922, 4),
    (923, 2), (924, 3), (925, 4);

-- getRecommendedCards 는 서비스당 tier 를 min_prev_month_spend 오름차순으로 하나만 고르고,
-- 한도는 limit_period='MONTH' AND limit_basis <> 'COUNT' 인 것 하나만 고른다.
-- 그래서 서비스마다 tier 하나 + AMOUNT MONTH 한도 하나만 둔다.
INSERT INTO `benefit_tier`
    (`tier_id`, `plan_group_id`, `service_id`, `tier_order`, `min_prev_month_spend`, `max_prev_month_spend`)
VALUES
    (920, NULL, 920, 1, 300000.00, NULL),
    (921, NULL, 921, 1, 300000.00, NULL),
    (922, NULL, 922, 1, 300000.00, NULL),
    (923, NULL, 923, 1, 400000.00, NULL),
    (924, NULL, 924, 1, 400000.00, NULL),
    (925, NULL, 925, 1, 400000.00, NULL);

INSERT INTO `benefit_limit` (`limit_id`, `tier_id`, `limit_basis`, `limit_period`, `limit_value`) VALUES
    (920, 920, 'AMOUNT', 'MONTH', 80000.00),
    (921, 921, 'AMOUNT', 'MONTH', 80000.00),
    (922, 922, 'AMOUNT', 'MONTH', 80000.00),
    (923, 923, 'AMOUNT', 'MONTH', 70000.00),
    (924, 924, 'AMOUNT', 'MONTH', 70000.00),
    (925, 925, 'AMOUNT', 'MONTH', 70000.00);
