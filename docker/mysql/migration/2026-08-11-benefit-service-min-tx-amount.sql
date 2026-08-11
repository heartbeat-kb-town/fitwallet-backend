-- benefit_service 스키마 v27 — 건당 최소 결제금액 추가 · 전월실적/건당 컬럼 개명
--
-- 왜 별도 파일인가:
--   docker/mysql/init/*.sql 은 MySQL 볼륨이 비어 있을 때 단 한 번만 실행된다.
--   따라서 002-schema.sql 을 고쳐도 "이미 떠 있는" 로컬 MySQL 과 운영 RDS 에는 반영되지 않는다.
--   이 디렉터리는 컨테이너에 마운트되지 않으므로 자동 실행되지 않는다 (의도된 동작).
--
-- 적용 방법:
--   로컬  docker compose exec -T mysql \
--           mysql -uroot -p"$MYSQL_ROOT_PASSWORD" fitwallet < docker/mysql/migration/2026-08-11-benefit-service-min-tx-amount.sql
--   운영  mysql -h {RDS_ENDPOINT} -u {USER} -p fitwallet < docker/mysql/migration/2026-08-11-benefit-service-min-tx-amount.sql
--
-- 볼륨을 새로 만드는 경우(docker compose down -v)에는 002/003 이 같은 결과를 만드므로 실행할 필요가 없다.
--
-- 변경 내용:
--   추가  min_tx_amount        건당(1회 결제) 최소 이용금액. 전부 0 으로 채운다
--   개명  min_payment_amount  -> min_prev_month_spend   (실제 의미는 전월실적 하한이었다)
--   개명  max_payment_amount  -> max_prev_month_spend
--   개명  CHECK 제약 ck_benefit_service_max_payment_amount -> ..._max_prev_month_spend
--
--   per_tx_limit_amount 는 건드리지 않는다(영향 범위 최소화).
--
-- ⚠️ 애플리케이션 코드가 함께 배포되어야 한다. CardMapper 의 findUsageBenefitRules /
--    findMonthlyBenefitRules 가 benefit_service 와 benefit_tier 의 실적 구간을 함께
--    SELECT 하는데, 개명 후 두 컬럼명이 같아져 AS 별칭으로 분리해 뒀다. 옛 코드에
--    새 스키마를 물리면 그 쿼리가 없는 컬럼을 찾아 실패한다.
--
-- 멱등하다. 여러 번 실행해도 결과가 같다 (이미 적용된 상태면 아무것도 하지 않는다).

START TRANSACTION;

-- 1. CHECK 제약 먼저 제거 — 아래 개명이 이 제약을 참조하고 있어 순서를 지켜야 한다.
SET @stmt := IF(
    (SELECT COUNT(*) FROM information_schema.table_constraints
      WHERE table_schema = DATABASE()
        AND table_name = 'benefit_service'
        AND constraint_name = 'ck_benefit_service_max_payment_amount') > 0,
    'ALTER TABLE benefit_service DROP CHECK ck_benefit_service_max_payment_amount',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 2. 컬럼 개명 (RENAME COLUMN 은 MySQL 8.0+)
SET @stmt := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'benefit_service'
        AND column_name = 'min_payment_amount') > 0,
    'ALTER TABLE benefit_service RENAME COLUMN min_payment_amount TO min_prev_month_spend',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'benefit_service'
        AND column_name = 'max_payment_amount') > 0,
    'ALTER TABLE benefit_service RENAME COLUMN max_payment_amount TO max_prev_month_spend',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 3. 건당 최소 결제금액 추가. max_prev_month_spend 뒤에 둬서 002-schema.sql 의 컬럼 순서와 맞춘다.
SET @stmt := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'benefit_service'
        AND column_name = 'min_tx_amount') = 0,
    'ALTER TABLE benefit_service ADD COLUMN min_tx_amount DECIMAL(15,2) NOT NULL DEFAULT 0 AFTER max_prev_month_spend',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 4. CHECK 제약을 새 이름으로 다시 건다.
SET @stmt := IF(
    (SELECT COUNT(*) FROM information_schema.table_constraints
      WHERE table_schema = DATABASE()
        AND table_name = 'benefit_service'
        AND constraint_name = 'ck_benefit_service_max_prev_month_spend') = 0,
    'ALTER TABLE benefit_service ADD CONSTRAINT ck_benefit_service_max_prev_month_spend
        CHECK (max_prev_month_spend IS NULL OR max_prev_month_spend > min_prev_month_spend)',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

COMMIT;

-- 검증
-- 1) 새 컬럼 3개가 있어야 한다
-- SELECT column_name FROM information_schema.columns
--  WHERE table_schema = DATABASE() AND table_name = 'benefit_service'
--    AND column_name IN ('min_prev_month_spend','max_prev_month_spend','min_tx_amount');
--
-- 2) 옛 이름은 0건이어야 한다 (per_tx_limit_amount 는 개명 대상이 아니므로 여기 없다)
-- SELECT COUNT(*) AS old_columns FROM information_schema.columns
--  WHERE table_schema = DATABASE() AND table_name = 'benefit_service'
--    AND column_name IN ('min_payment_amount','max_payment_amount');
