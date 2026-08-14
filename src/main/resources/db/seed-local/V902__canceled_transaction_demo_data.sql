-- 승인취소 화면과 집계 제외를 검증하기 위한 로컬 데모 데이터다.
-- 348: 신용카드의 놓친 혜택 거래, 535: 신용카드의 실제 혜택 적용 거래.
-- 원거래의 금액·혜택 귀속·결제시각은 보존하고 상태만 취소로 바꾼다.
UPDATE payment_transaction
SET transaction_status = 'CANCELED'
WHERE payment_transaction_id IN (348, 535)
  AND transaction_status = 'APPROVED';
