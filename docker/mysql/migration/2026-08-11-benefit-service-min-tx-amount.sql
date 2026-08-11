-- benefit_service 스키마 v27 — 건당(1회 결제) 최소 이용금액 컬럼 추가
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
--   추가  min_tx_amount  건당 최소 이용금액. NOT NULL DEFAULT 0 이라 기존 165행은 자동으로 0 이 된다
--
-- 컬럼 추가뿐이라 기존 쿼리는 전부 그대로 동작한다. 애플리케이션 배포 순서에 제약이 없다
-- (새 컬럼을 읽는 코드가 아직 없다 — 판정 로직은 별도 이슈).
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
