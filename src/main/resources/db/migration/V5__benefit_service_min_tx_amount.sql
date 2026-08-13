-- V5 — benefit_service 스키마 v27: 건당(1회 결제) 최소 이용금액 컬럼 추가 (2026-08-11)
--
-- 새로 만드는 DB에서는 V1(기준 스키마)에 이미 이 컬럼이 있어 아무 일도 하지 않는다.
-- 이 파일이 실제로 일하는 곳은 V1 이전 상태로 굳어 있는 DB(운영 RDS)다.
--
-- 변경 내용:
--   추가  min_tx_amount  건당 최소 이용금액. NOT NULL DEFAULT 0 이라 기존 165행은 자동으로 0 이 된다
--
-- 컬럼 추가뿐이라 기존 쿼리는 전부 그대로 동작한다.
--
-- 멱등하다. 여러 번 실행해도 결과가 같다.

SET @stmt := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'benefit_service'
        AND column_name = 'min_tx_amount') = 0,
    'ALTER TABLE benefit_service
        ADD COLUMN min_tx_amount DECIMAL(15,2) NOT NULL DEFAULT 0 AFTER max_payment_amount',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 검증
-- 1) 컬럼이 max_payment_amount 다음에 있어야 한다
-- SELECT ordinal_position, column_name, column_type, is_nullable, column_default
--   FROM information_schema.columns
--  WHERE table_schema = DATABASE() AND table_name = 'benefit_service'
--    AND column_name IN ('max_payment_amount', 'min_tx_amount');
--
-- 2) 기존 행은 전부 0 이어야 한다
-- SELECT COUNT(*) AS rows_, SUM(min_tx_amount = 0) AS zero_rows FROM benefit_service;
