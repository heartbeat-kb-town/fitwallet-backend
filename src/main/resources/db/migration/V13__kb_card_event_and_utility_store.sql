-- V13 — 진행 중인 KB국민카드 이벤트를 넣고, 공과금 가맹점을 추가한다. (#325)
--
-- 두 가지를 한 파일에 담는다. 둘 다 "모든 환경이 같아야 하는 참조 데이터"이고(§11),
-- 청춘대로 톡톡카드 시연 화면 하나를 채우려고 함께 들어가는 변경이다.
--
-- =========================================================
-- 1) card_event — 실제 진행 중인 이벤트로 교체
-- =========================================================
--
-- 청춘대로 톡톡(card_product_id = 47)에 붙어 있던 유일한 이벤트(event_id = 3)는 기간이
-- 2026-07-31로 끝나 화면에 뜨지 않는다. 그 자리를 실제 이벤트로 채운다.
--
-- ⚠️ detail_url은 카드사 홈이 아니라 **그 이벤트의 게시판 글** 주소여야 한다. 홈으로 보내면
-- "자세히보기"를 눌러도 사용자가 이벤트를 다시 찾아야 한다. 이 원칙 때문에 요약문도 실제
-- 게시글 내용과 맞춘다 — 지어낸 요약에 진짜 게시판 링크를 달 수는 없다.
--
-- 아래 3건은 2026-08-24에 card.kbcard.com에서 직접 확인한 진행 중 이벤트다.
-- 만료건(스타벅스 20% 포인트백 ~2026-05-26, 관리비 자동납부 캐시백 2023년,
-- Welcome 캐시백 2023년)은 넣지 않았다.
--
-- 대상 컬럼(card_product_id XOR issuer_id)은 이벤트의 실제 적용 범위를 따른다.
--
-- ⚠️ "할부로 다산다"만 card_product_id = 47로 카드를 지정한다. 이 이벤트는 KB국민 개인
-- **신용카드 전용(체크카드 제외)**인데, issuer_id = 3으로 넣으면 findCardEventItems의
-- `OR ce.issuer_id = cp.issuer_id` 조인 때문에 KB 체크카드(43·44·45 등)에도 뜬다.
-- 스키마에 카드종류 조건을 둘 자리가 없어서, 시연 대상인 47에만 붙이는 방식으로 처리했다.
-- 다른 KB 신용카드에도 필요해지면 카드별로 행을 늘리거나 card_event에 카드종류 조건을
-- 추가하는 세트 변경이 필요하다.
--
-- event_id를 못박는 것은 V2가 1~7을 명시적으로 부여한 관례를 잇기 위해서다.
-- ON DUPLICATE KEY UPDATE라 여러 번 실행해도 결과가 같다.

SET NAMES utf8mb4;

INSERT INTO card_event (event_id, card_product_id, issuer_id, summary, starts_at, ends_at, detail_url)
VALUES
    (8, 47, NULL,
     '할부로 다산다 — 생활편의업종 5만원 이상 결제 시 2~5개월 무이자할부(병원·항공·보험·온라인쇼핑·백화점·여행 등)',
     '2026-08-01', '2026-08-31',
     'https://card.kbcard.com/BON/DVIEW/HBBMCXCRVNEC0001?mainCC=a&eventNum=1001681'),
    (9, NULL, 3,
     'KB Pay 첫 만남 기념 커피 쿠폰 — 최초 신규가입 후 응모 시 메가MGC커피 아이스 아메리카노 쿠폰 1매(9월 말 지급)',
     '2026-08-01', '2026-08-31',
     'https://card.kbcard.com/BON/DVIEW/HBBMCXCRVNEC0001?mainCC=a&eventNum=1001598'),
    (10, NULL, 3,
     '신차타GO~ 할인받GO~! — 신차 대금 100만원 이상 일시불 결제 시 신용 1.3% 청구할인, 체크 0.5% 캐시백(사전 응모 필수)',
     '2026-02-19', '2026-12-31',
     'https://card.kbcard.com/BON/DVIEW/HBBMCXCRVNEC0001?mainCC=a&eventNum=1000020')
ON DUPLICATE KEY UPDATE
    card_product_id = VALUES(card_product_id),
    issuer_id       = VALUES(issuer_id),
    summary         = VALUES(summary),
    starts_at       = VALUES(starts_at),
    ends_at         = VALUES(ends_at),
    detail_url      = VALUES(detail_url);

-- V2의 event_id = 3(청춘대로 톡톡 CGV 5,000원 할인)을 지운다.
--
-- 실제 KB 이벤트가 아니라서 대응하는 게시판 글이 없다. detail_url이 홈 주소
-- (https://card.kbcard.com/)라 "자세히보기"가 이벤트로 연결되지 않는데, 위의 게시판 딥링크
-- 원칙을 지킬 방법이 없어 되살리지 않고 지운다. 이미 2026-07-31로 만료돼 화면에도 안 뜬다.
-- card_event를 참조하는 FK가 없어 그냥 지워진다. 멱등하다.
DELETE FROM card_event WHERE event_id = 3;

-- =========================================================
-- 2) store — 공과금 가맹점
-- =========================================================
--
-- 시연 계정의 "실적 미인정" 거래(db/seed-local/V909)가 붙을 가맹점이다. 거래는 데모 데이터라
-- seed-local에 있지만, 가맹점 마스터는 모든 환경이 같아야 하므로 여기 둔다(§11).
--
-- category_id = 7('기타')를 쓴다. 공과금 전용 업종을 새로 만들지 않은 이유는 혜택·추천이
-- 카테고리 단위로 돌기 때문이다 — 혜택 서비스가 하나도 없는 카테고리를 늘리면 추천 엔진의
-- 순회 대상만 늘고 얻는 게 없다.
--
-- 좌표·주소·kakao_place_id·store_qr_token은 NULL이다. 지도에 찍히거나 QR로 결제할 가맹점이
-- 아니라 거래 내역에 이름과 업종으로만 등장한다. UNIQUE 키(kakao_place_id, store_qr_token)는
-- MySQL에서 NULL 중복을 허용하므로 다른 NULL 가맹점과 공존한다.
--
-- store_id 245는 V2가 1~244를 명시적으로 부여한 다음 번호다.
INSERT INTO store (store_id, category_id, brand_id, store_name,
                   store_rank, latitude, longitude, address, kakao_place_id, store_qr_token)
VALUES (245, 7, NULL, '한국전력공사', NULL, NULL, NULL, NULL, NULL, NULL)
ON DUPLICATE KEY UPDATE
    category_id = VALUES(category_id),
    store_name  = VALUES(store_name);

-- 검증
-- 1) 오늘 기준 청춘대로 톡톡(user_card 1)에 뜨는 이벤트가 3건이어야 한다.
-- SELECT ce.event_id, ce.summary, ce.starts_at, ce.ends_at, ce.detail_url
--   FROM user_card uc
--   JOIN card_product cp ON cp.card_product_id = uc.card_product_id
--   JOIN card_event ce ON ce.card_product_id = cp.card_product_id OR ce.issuer_id = cp.issuer_id
--  WHERE uc.user_card_id = 1 AND CURDATE() BETWEEN ce.starts_at AND ce.ends_at;
--
-- 2) event_id = 3이 사라졌어야 한다.
-- SELECT COUNT(*) FROM card_event WHERE event_id = 3;
--
-- 3) "할부로 다산다"가 KB 체크카드에는 뜨지 않아야 한다(카드 지정 이벤트라서).
-- SELECT COUNT(*) FROM card_event WHERE event_id = 8 AND issuer_id IS NULL;
