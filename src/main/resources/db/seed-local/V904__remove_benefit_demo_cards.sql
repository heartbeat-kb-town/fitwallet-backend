-- 시연 계정(user_id = 1)의 보유 카드를 V900 기준 5장으로 되돌린다.
-- V901 이 더한 4장과 그 카드로 찍힌 결제 30건을 뺀다 — 6: 신한 Deep Store,
-- 7: 신한 O2O, 8: 신한 Deep Dream, 9: KB국민 굿데이 플래티늄.
--
-- V901 을 직접 고치지 않는 이유는 Flyway 체크섬이다. 이미 적재된 로컬 DB 는 그 파일을
-- 다시 읽지 않으므로 효과가 없을 뿐 아니라, 체크섬이 어긋나 앱이 아예 뜨지 않는다.
--
-- ⚠️ 컴포즈커피 세종대학교점(store_id = 1)의 혜택 추천 라인업이 V901 이전으로 돌아간다.
-- AVAILABLE 3건과 PREV_SPEND_NOT_MET 1건이 빠지고 LIMIT_EXHAUSTED(신한 Pick E 체크,
-- user_card 3) 한 갈래만 남는다.

-- ---------------------------------------------------------
-- payment_transaction — 네 카드의 결제 30건을 물리 삭제한다.
--
-- 카드만 소프트 삭제하면 이 30건이 리포트에 그대로 남는다. report 도메인의 유저 단위
-- 집계(BenefitReportMapper.getReceivedBenefitSummary·getMonthlyCategorySpends,
-- MissedBenefitMapper.getMissedSummary)가 user_card 를 조인하면서 is_deleted 를 거르지
-- 않기 때문이다. 실제로 Deep Store 의 이번 달 혜택 적용 거래 3건 때문에 "받은 혜택"이
-- 7,200원 부풀어 보였다.
--
-- 소프트 삭제 규칙(§10)의 예외가 아니다. 그 규칙이 물리 DELETE 를 막는 이유는 결제 이력
-- 보존인데, 여기서 지우는 것은 시연용으로 만들어낸 행이라 보존할 이력이 아니다.
--
-- 다른 행이 이 30건이나 네 카드를 참조하지 않는다 — better_user_card_id 로 이 카드들을
-- 가리키는 거래 0건(V901 이 의도적으로 NULL 로 넣었다), payment_session 0건.
-- ---------------------------------------------------------
DELETE FROM payment_transaction
WHERE user_card_id IN (6, 7, 8, 9);

-- ---------------------------------------------------------
-- user_card — 네 행은 소프트 삭제로 남긴다(§10).
--
-- 위에서 결제를 지웠으니 물리 DELETE 도 가능해졌지만, 카드 삭제는 앱에서도 소프트 삭제라
-- 시드가 같은 모양을 유지하는 편이 낫다. UNIQUE (user_id, card_product_id) 가 살아 있어
-- 시연 중 같은 카드를 다시 등록하면 CardMapper 가 이 행을 재활성화한다.
--
-- ⚠️ 되돌리려고 is_deleted 를 0 으로 바꿔도 위 라인업은 돌아오지 않는다. 전월실적을
-- 만들던 결제가 없어 네 장 모두 PREV_SPEND_NOT_MET 으로 떨어진다.
-- ---------------------------------------------------------
UPDATE user_card
SET is_deleted = 1
WHERE user_id = 1
  AND user_card_id IN (6, 7, 8, 9)
  AND is_deleted = 0;
