-- benefit_service 데이터 — 건당 최소 이용금액(min_tx_amount) 값 채우기
--
-- 왜 별도 파일인가:
--   docker/mysql/init/*.sql 은 MySQL 볼륨이 비어 있을 때 단 한 번만 실행된다.
--   따라서 003-seed.sql 을 고쳐도 "이미 떠 있는" 로컬 MySQL 과 운영 RDS 에는 반영되지 않는다.
--   이 디렉터리는 컨테이너에 마운트되지 않으므로 자동 실행되지 않는다 (의도된 동작).
--
-- 적용 방법:
--   로컬  docker compose exec -T mysql \
--           mysql -uroot -p"$MYSQL_ROOT_PASSWORD" fitwallet < docker/mysql/migration/2026-08-12-benefit-service-min-tx-amount-values.sql
--   운영  mysql -h {RDS_ENDPOINT} -u {USER} -p fitwallet < docker/mysql/migration/2026-08-12-benefit-service-min-tx-amount-values.sql
--
-- 볼륨을 새로 만드는 경우(docker compose down -v)에는 003-seed.sql 이 같은 결과를 만드므로 실행할 필요가 없다.
--
-- ⚠️ 값의 성격 — 이 값들은 실제 약관값이 아니라 데모용 mock 이다.
--   #182 의 MIN_TX_AMOUNT_NOT_MET 판정이 동작하는 것을 로컬에서 재현할 수 있게 만드는 것이 목적이다.
--   실제 3사 약관을 확인한 결과 (2026-08-12):
--     · 신한 Mr.Life 의 "1회 승인금액 1만원까지 할인 적용" 은 건당 최소가 아니라 건당 상한이고,
--       이미 per_tx_limit_amount 로 반영돼 있다
--     · 현대 M포인트(M3P) 의 "일 2회, 1회 10만원 한도" 도 마찬가지로 상한이다
--     · KB 국민 K-패스 체크카드는 이 저장소 시드에 없는 카드다
--   즉 참고 삼은 3건 중 실제로 시드에 옮길 수 있는 조건은 현대 ZERO Up 뿐이었다.
--   ZERO Up 만 실제 약관 문구를 그대로 옮겼다 (svc112·114):
--     "추가 혜택 적용을 위한 최소 결제 금액은 10만원 이상 단일 결제 건 기준(결제 금액 합산 불가)"
--   나머지 3행은 "고율 혜택일수록 소액 남용을 막는 문턱이 있다" 는 가정으로 임의 부여했다.
--
-- 변경 내용: 165행 중 5행. 나머지 160행은 0(조건 없음) 그대로다.
--
--   svc  카드                       혜택                        건당최소   근거
--   ---  -------------------------  -------------------------  --------  ------------------
--   112  현대카드 ZERO Up(포인트형) 추가혜택 2.4% 적립          100,000   약관(언론 보도로 확인)
--   114  현대카드 ZERO Up(할인형)   추가혜택 1.6% 청구할인      100,000   약관(현대카드 공식)
--   124  KB국민 노리 체크카드       아웃백·VIPS 20% 환급할인     20,000   mock
--   125  KB국민 노리 체크카드       스타벅스 20% 환급할인         5,000   mock
--   126  KB국민 노리 체크카드       GS25 5% 환급할인              3,000   mock
--
-- 왜 하필 이 행들인가 — 시드 데모 계정(user_id=1)에서 실제로 게이트까지 도달하는 혜택이어야 한다.
--   판정 순서가 전월실적 → 건당최소 → 한도라, 전월실적에서 먼저 걸리면 MIN_TX_AMOUNT_NOT_MET 이
--   나올 수 없다. user_id=1 의 카드 5장 중 전월실적을 통과하는 것은 KB국민 노리 체크카드
--   (전월 325,900원 / 하한 200,000원)뿐이라 그 카드의 혜택 3건에 문턱을 걸었다.
--   ZERO Up 2건은 데모 계정이 보유하지 않지만 실제 약관 조건이라 함께 넣는다.
--
-- 재현 시나리오 (시드 데모 계정 user_id=1):
--   스타벅스 가맹점(store_id=20)에 amount=1000 으로 예상 혜택을 조회하면
--   KB국민 노리 체크카드가 CONDITION_NOT_MET / MIN_TX_AMOUNT_NOT_MET 으로 내려온다.
--   메시지는 "5,000원 이상 결제해야 받을 수 있는 혜택이에요." 이고,
--   amount=5000 으로 올리면 같은 카드가 AVAILABLE(1,000원 할인)로 바뀐다.
--
-- 멱등하다. 여러 번 실행해도 결과가 같다 (조건 없는 단순 UPDATE 이므로 두 번째 실행은 0행 변경).

UPDATE benefit_service SET min_tx_amount = 100000.00 WHERE service_id = 112;
UPDATE benefit_service SET min_tx_amount = 100000.00 WHERE service_id = 114;
UPDATE benefit_service SET min_tx_amount =  20000.00 WHERE service_id = 124;
UPDATE benefit_service SET min_tx_amount =   5000.00 WHERE service_id = 125;
UPDATE benefit_service SET min_tx_amount =   3000.00 WHERE service_id = 126;

-- 검증
-- 1) 값이 있는 행이 정확히 5행이어야 한다
-- SELECT COUNT(*) AS 전체, SUM(min_tx_amount > 0) AS 문턱있음 FROM benefit_service;
--
-- 2) 각 행의 값 확인
-- SELECT service_id, benefit_name, min_tx_amount
--   FROM benefit_service WHERE min_tx_amount > 0 ORDER BY service_id;
