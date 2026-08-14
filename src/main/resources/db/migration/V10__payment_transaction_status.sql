-- V10 — payment_transaction에 승인·취소 상태를 추가한다. (#226)
--
-- 전체 취소만 지원한다. 승인번호와 취소 시각은 저장하지 않고, 기존 결제 행의 현재 상태를
-- APPROVED/CANCELED 중 하나로 보관한다. 기존 거래는 모두 승인 건이므로 DEFAULT APPROVED로
-- 채운다. 이후 집계·표시 로직은 별도 작업에서 이 상태를 기준으로 처리한다.
--
-- 운영 RDS에 컬럼이나 제약이 이미 존재할 가능성을 고려해 각각 존재 여부를 확인한다.
-- 멱등하다. 여러 번 실행해도 결과가 같다.

SET @stmt := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'payment_transaction'
        AND column_name = 'transaction_status') = 0,
    'ALTER TABLE payment_transaction
        ADD COLUMN transaction_status VARCHAR(20) NOT NULL DEFAULT ''APPROVED'' AFTER paid_at',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt := IF(
    (SELECT COUNT(*) FROM information_schema.check_constraints
      WHERE constraint_schema = DATABASE()
        AND constraint_name = 'ck_payment_transaction_status') = 0,
    'ALTER TABLE payment_transaction
        ADD CONSTRAINT ck_payment_transaction_status
        CHECK (transaction_status IN (''APPROVED'', ''CANCELED''))',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 검증
-- 1) 컬럼이 paid_at 다음에 있고 NOT NULL DEFAULT APPROVED여야 한다.
-- SELECT ordinal_position, column_name, column_type, is_nullable, column_default
--   FROM information_schema.columns
--  WHERE table_schema = DATABASE() AND table_name = 'payment_transaction'
--    AND column_name IN ('paid_at', 'transaction_status');
--
-- 2) 기존 거래는 모두 APPROVED여야 한다.
-- SELECT transaction_status, COUNT(*)
--   FROM payment_transaction
--  GROUP BY transaction_status;
--
-- 3) CHECK 제약은 APPROVED/CANCELED만 허용해야 한다.
-- SELECT check_clause
--   FROM information_schema.check_constraints
--  WHERE constraint_schema = DATABASE()
--    AND constraint_name = 'ck_payment_transaction_status';
