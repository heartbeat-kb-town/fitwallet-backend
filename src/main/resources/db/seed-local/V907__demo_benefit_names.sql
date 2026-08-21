-- =========================================================
-- V907 — 시연 혜택명 정리와 결제 취소 1건
--
-- V905·V906 으로 라인업은 잡혔지만 화면에 뜨는 "혜택 이름"이 시연 상황과 어긋났다.
--   · 알고는 파스타집인데 1등 혜택명이 "Enjoy 서비스 - 버거/패스트푸드"였다
--   · 2등은 "추가혜택 - 일반음식점(주말)" 이라 오늘이 주말인지 따지게 만든다
--   · 동부플란트치과는 네 장 모두 "병원 업종 ~" 이라 카드사 구분이 안 됐다
--
-- 바꾸는 것은 benefit_name 뿐이다. 요율·한도·스코프 같은 판정 재료는 건드리지 않으므로
-- V905·V906 이 만든 세 가맹점 라인업(순위·금액·조건불가 사유)은 그대로다.
--
-- 이름은 카드사별 기존 작명 규칙에 맞춘다(참조 데이터에 이미 있는 패턴을 따른다).
--   KB국민 청춘대로 톡톡 : "Great 서비스 - ", "Enjoy 서비스 - "
--   KB국민 노리 체크     : "~ 환급할인"
--   신한카드 All Pass    : "일반 할인 - "
--   신한카드 Pick E 체크 : "~ 적립"
--   현대카드 Z everyday  : "기본혜택 - "
-- =========================================================

SET NAMES utf8mb4;

SET @month_start := DATE_FORMAT(NOW(), '%Y-%m-01');

-- ---------------------------------------------------------
-- 1. 혜택명 정리
-- ---------------------------------------------------------

-- 컴포즈커피 세종대학교점 (카페/디저트)
UPDATE `benefit_service` SET `benefit_name` = 'Enjoy 서비스 - 카페'      WHERE `service_id` = 901;  -- KB 청춘대로 20%
UPDATE `benefit_service` SET `benefit_name` = '일반 할인 - 카페·디저트'   WHERE `service_id` = 902;  -- 신한 All Pass 10%
UPDATE `benefit_service` SET `benefit_name` = '기본혜택 - 카페'          WHERE `service_id` = 903;  -- 현대 Z everyday 5%
-- 73(신한 Pick E 체크 "커피 업종 적립")은 이미 규칙에 맞아 그대로 둔다.

-- 알고 (푸드 — 파스타집)
UPDATE `benefit_service` SET `benefit_name` = 'Enjoy 서비스 - 외식'      WHERE `service_id` = 134;  -- KB 청춘대로 20%
-- 79 는 참조 데이터상 "추가혜택 - 일반음식점(주말)" 이지만, 판정은 늘 이쪽이 이겨서
-- 화면에는 "(주말)" 붙은 이름만 뜬다. 주중 5%(78)와 나뉜 구조는 그대로 두고 표기만 걷어낸다.
UPDATE `benefit_service` SET `benefit_name` = '기본혜택 - 외식'          WHERE `service_id` =  79;  -- 현대 Z everyday 10%
UPDATE `benefit_service` SET `benefit_name` = '외식 환급할인'            WHERE `service_id` = 904;  -- KB 노리 5%
UPDATE `benefit_service` SET `benefit_name` = '외식 업종 적립'           WHERE `service_id` = 905;  -- 신한 Pick E 체크 5% 적립

-- 동부플란트치과의원 (병원) — 네 장이 서로 다른 결로 읽히게 한다
UPDATE `benefit_service` SET `benefit_name` = '일반 할인 - 병의원'       WHERE `service_id` = 906;  -- 신한 All Pass 10%
UPDATE `benefit_service` SET `benefit_name` = '의료비 환급할인'          WHERE `service_id` = 907;  -- KB 노리 7%
UPDATE `benefit_service` SET `benefit_name` = '건강생활 적립'            WHERE `service_id` = 908;  -- 신한 Pick E 체크 5% 적립
UPDATE `benefit_service` SET `benefit_name` = 'Care 서비스 - 병의원'     WHERE `service_id` = 909;  -- KB 청춘대로 15%(전월실적 미달)

-- 시연 가맹점 밖 — 포인트 물량용 두 건도 이름을 맞춘다
UPDATE `benefit_service` SET `benefit_name` = '온라인쇼핑 적립'          WHERE `service_id` = 910;  -- KB 노리 3% 포인트리
UPDATE `benefit_service` SET `benefit_name` = '기본혜택 - 생활편의'      WHERE `service_id` = 911;  -- 현대 Z everyday 3% M포인트

-- ---------------------------------------------------------
-- 2. 스타벅스 건대후문점 결제 1건을 승인취소로 돌린다
--
-- 취소 화면과 집계 제외를 이 계정에서도 보여주기 위한 것이다(V902 와 같은 취지).
-- 앱에 결제 취소 API 가 없어 상태 컬럼을 직접 바꾼다.
--
-- ⚠️ payment_transaction_id 로 찾지 않는다. V906 의 거래는 AUTO_INCREMENT 로 들어가
-- 적재 환경마다 id 가 다르기 때문이다(로컬 993, 시연 환경은 다를 수 있다).
-- V906 이 만든 그 한 행을 특정하는 조합으로 찾는다 — 당월·카드1·스타벅스 건대후문점(32)
-- ·스타벅스 50% 할인(133)·4,800원.
-- ---------------------------------------------------------
UPDATE `payment_transaction`
SET `transaction_status` = 'CANCELED'
WHERE `user_card_id` = 1
  AND `store_id` = 32
  AND `applied_benefit_service_id` = 133
  AND `amount` = 4800.00
  AND `paid_at` >= @month_start
  AND `transaction_status` = 'APPROVED';
