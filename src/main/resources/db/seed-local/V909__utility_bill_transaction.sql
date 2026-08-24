-- =========================================================
-- V909 — 시연 계정(user_id = 1)에 실적 미인정 거래를 하나 넣는다. (#325)
--
-- 시드 전체에 is_eligible = 0 거래가 한 건도 없어서, 카드 상세의 "실적 미인정" 표시와
-- 이용 실적의 excludedAmount가 한 번도 화면에 나온 적이 없다. 공과금 22,000원을 넣어 두 축을
-- 모두 드러낸다 — 공과금은 실제 카드사에서도 전월실적 산정에서 빠지는 대표 업종이다.
--
-- 붙는 카드는 user_card 1 = KB국민 청춘대로 톡톡카드(card_product 47), 가맹점은 V13이 넣은
-- store 245 '한국전력공사'다.
--
-- 이 거래가 화면 세 곳에서 어떻게 보이는지:
--   · 최근 이용 내역   findTransactions 가 performance_included = false 로 내려준다
--                      (is_eligible = 1 AND transaction_status = 'APPROVED' 이므로)
--   · 이용 실적        findUsageAmounts 의 excluded_amount 에 22,000 이 잡힌다.
--                      recognized_amount 에는 들어가지 않는다
--   · 결제예정금액     sumScheduledPaymentAmount 는 승인 건을 모두 더하므로 22,000 포함된다
--
-- ⚠️ transaction_status 는 DB DEFAULT('APPROVED')에 맡긴다. 취소 건이 아니라 **승인된
-- 실적 미인정** 건이어야 한다 — 둘은 화면에서 다르게 취급된다(취소는 합계에서도 빠진다).
--
-- ⚠️ 혜택 판정 재료가 아니다. applied_benefit_service_id / applied_tier_id 가 NULL 이라
-- V905·V906 이 금액과 건수로 맞춰 둔 시연 라인업(한도 소진·3등 컷 등)에 영향이 없다.
-- =========================================================

SET NAMES utf8mb4;

-- 날짜는 NOW() 상대값이다(V901·V906 과 같은 이유). 이용 실적은 "이번 달"을 보므로 고정
-- 날짜로 넣으면 적재한 달에만 맞는다.
--
-- 3일 전에 두되 이번 달을 벗어나지 않게 묶는다. 달 초(1~3일)에 적재하면 그만큼 당겨져
-- 오늘에 가까워지는데, 지난달로 넘어가 excludedAmount 가 이번 달에서 사라지는 것보다 낫다.
SET @month_start := DATE_FORMAT(NOW(), '%Y-%m-01');
SET @elapsed     := GREATEST(DAY(NOW()) - 1, 0);
SET @days_ago    := LEAST(3, @elapsed);

-- payment_transaction_id 는 AUTO_INCREMENT 에 맡긴다 — 이 파일 안에서 아무도 참조하지 않는다.
-- final_amount 는 amount 와 같다: 혜택이 붙지 않은 거래라 할인이 없다(discount_amount = 0).
-- is_used_app = 0 — 공과금은 앱을 거치지 않은 외부 승인 내역이다.
INSERT INTO `payment_transaction`
    (`user_card_id`, `store_id`, `amount`, `discount_amount`, `final_amount`, `paid_at`,
     `is_used_app`, `is_eligible`, `applied_benefit_service_id`, `applied_tier_id`,
     `better_user_card_id`, `alternative_discount_amount`, `missed_amount`)
VALUES
    (1, 245, 22000.00, 0.00, 22000.00,
     @month_start + INTERVAL (@elapsed - @days_ago) DAY + INTERVAL 10 HOUR + INTERVAL 12 MINUTE,
     0, 0, NULL, NULL, NULL, NULL, NULL);

-- 검증
-- 1) 이번 달 user_card 1 의 실적 미인정 합계가 22,000 이어야 한다.
-- SELECT COALESCE(SUM(CASE WHEN is_eligible = 0 THEN amount ELSE 0 END), 0) AS excluded_amount
--   FROM payment_transaction
--  WHERE user_card_id = 1 AND transaction_status = 'APPROVED'
--    AND paid_at >= DATE_FORMAT(NOW(), '%Y-%m-01');
--
-- 2) 가맹점명과 업종이 함께 나와야 한다(store 245 가 있어야 한다).
-- SELECT s.store_name, c.category_name, pt.amount, pt.is_eligible, pt.paid_at
--   FROM payment_transaction pt
--   JOIN store s ON s.store_id = pt.store_id
--   JOIN category c ON c.category_id = s.category_id
--  WHERE pt.user_card_id = 1 AND pt.is_eligible = 0;
