-- V4 — payment_session 스키마 v26: fail_reason 에 MOCK_RANDOM_DECLINE 추가 (2026-08-06, #133)
--
-- 결제 결과 조회 Mock 이 무작위로 승인을 거절시킬 때 쓰는 임시 값이다.
-- PaymentMapper.markSessionFailed 가 이 값을 직접 넣으므로, CHECK 제약이 옛날 값 집합이면
-- 결제 결과 폴링에서 제약 위반으로 실패한다. 실제 PG 연동 시 진짜 거절 사유 코드로 대체될 예정.
--
-- 새로 만드는 DB에서는 V1(기준 스키마)의 CHECK 에 이미 이 값이 있어 아무 일도 하지 않는다.
-- 이 파일이 실제로 일하는 곳은 V1 이전 상태로 굳어 있는 DB(운영 RDS)다.
--
-- MySQL 8 은 CHECK 제약을 수정하는 문법이 없어 DROP 후 ADD 한다. 두 문장이 한 덩어리로
-- 묶여야 하므로, 값 집합에 MOCK_RANDOM_DECLINE 이 없을 때만 둘 다 실행되게 한다.
--
-- 멱등하다. 여러 번 실행해도 결과가 같다.

SET @needs_update := (
    SELECT COUNT(*) FROM information_schema.check_constraints
     WHERE constraint_schema = DATABASE()
       AND constraint_name = 'ck_payment_session_fail_reason'
       AND check_clause NOT LIKE '%MOCK_RANDOM_DECLINE%');

SET @stmt := IF(@needs_update = 1,
    'ALTER TABLE payment_session DROP CHECK ck_payment_session_fail_reason',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt := IF(@needs_update = 1,
    'ALTER TABLE payment_session
        ADD CONSTRAINT ck_payment_session_fail_reason
        CHECK (fail_reason IS NULL OR fail_reason IN (
            ''PIN_MISMATCH'',
            ''PIN_LOCKED'',
            ''CANCELED_BY_USER'',
            ''CARD_UNAVAILABLE'',
            ''SYSTEM_ERROR'',
            ''MOCK_RANDOM_DECLINE''))',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 검증
-- SELECT check_clause LIKE '%MOCK_RANDOM_DECLINE%' AS 반영됨
--   FROM information_schema.check_constraints
--  WHERE constraint_schema = DATABASE() AND constraint_name = 'ck_payment_session_fail_reason';
