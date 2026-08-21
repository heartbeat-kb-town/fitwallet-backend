-- 시연 계정(user_id = 1)의 보유 카드를 V900 기준 5장으로 되돌린다.
-- V901 이 더한 4장을 뺀다 — 6: 신한 Deep Store, 7: 신한 O2O, 8: 신한 Deep Dream,
-- 9: KB국민 굿데이 플래티늄.
--
-- V901 을 직접 고치지 않는 이유는 Flyway 체크섬이다. 이미 적재된 로컬 DB 는 그 파일을
-- 다시 읽지 않으므로 효과가 없을 뿐 아니라, 체크섬이 어긋나 앱이 아예 뜨지 않는다.
--
-- 소프트 삭제로 처리한다(§10). payment_transaction 30 건이 이 네 행을 FK 로 참조하고 있어
-- 물리 DELETE 가 불가능하고, 조회 매퍼가 전부 is_deleted = 0 으로 거르므로 보유 카드
-- 목록·혜택 판정·이용실적에서 함께 사라진다.
--
-- ⚠️ 컴포즈커피 세종대학교점(store_id = 1)의 혜택 추천 라인업이 V901 이전으로 돌아간다.
-- AVAILABLE 3건과 PREV_SPEND_NOT_MET 1건이 빠지고 LIMIT_EXHAUSTED(신한 Pick E 체크,
-- user_card 3) 한 갈래만 남는다. 그 라인업이 다시 필요해지면 is_deleted 를 0 으로 되돌린다.
UPDATE user_card
SET is_deleted = 1
WHERE user_id = 1
  AND user_card_id IN (6, 7, 8, 9)
  AND is_deleted = 0;
