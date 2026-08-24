-- V12 — card_product에 상품 상세 페이지 URL을 추가한다. (#325)
--
-- 카드 추천 화면에서 카드를 누르면 그 카드의 카드사 상품 페이지로 넘어가야 하는데,
-- card_product에는 이동할 주소가 없었다. card_image_url 옆에 같은 성격의 컬럼을 하나 둔다 —
-- 카드마다 값이 다르고 컬럼은 하나다.
--
-- 값은 지금 44·54 두 장만 채운다. 시연에서 추천으로 노출되는 카드가 이 둘이기 때문이고
-- (db/seed-local/V908 참고), 나머지 55장은 NULL로 남는다. 읽는 쪽은 NULL을 "이동할 곳 없음"
-- 으로 처리한다(추천 응답의 detailUrl이 null이면 프론트가 이동 버튼을 감춘다).
--
-- ⚠️ 카드사 홈페이지가 아니라 그 카드의 상품 상세 페이지여야 한다. 홈으로 보내면 사용자가
-- 카드를 다시 찾아야 해서 "이 카드 보러가기"가 성립하지 않는다.
--
-- URL 규칙 (2026-08-24 실측) — KB국민카드는 신용·체크가 같은 페이지(HCAMCXPRICAC0076)를 쓰고
-- cooperationcode로 카드를 가른다. 그 코드는 card_image_url 파일명의 숫자와 같다.
--   47 청춘대로 톡톡  09222_img.png → cooperationcode=09222 ✓
--   54 톡톡M          09290_img.png → cooperationcode=09290 ✓
--   44 국민행복체크    02066_img.png → cooperationcode=02066 ✓
-- 덕분에 다른 KB 카드를 채울 때도 스키마 변경 없이 UPDATE만 하면 된다.
--
-- 운영 RDS에 컬럼이 이미 있을 가능성을 고려해 존재 여부를 확인한다. 멱등하다.

SET @stmt := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'card_product'
        AND column_name = 'detail_url') = 0,
    'ALTER TABLE card_product
        ADD COLUMN detail_url VARCHAR(500) NULL AFTER card_image_url',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 조건 없는 UPDATE로 쓴다(§11). 이미 올바른 행은 값이 같아 다시 실행해도 바뀌지 않는다.
UPDATE card_product
   SET detail_url = 'https://card.kbcard.com/CRD/DVIEW/HCAMCXPRICAC0076?mainCC=a&cooperationcode=02066'
 WHERE card_product_id = 44;

UPDATE card_product
   SET detail_url = 'https://card.kbcard.com/CRD/DVIEW/HCAMCXPRICAC0076?mainCC=a&cooperationcode=09290'
 WHERE card_product_id = 54;

-- 검증
-- 1) 컬럼이 card_image_url 다음에 있고 NULL 허용이어야 한다.
-- SELECT ordinal_position, column_name, column_type, is_nullable
--   FROM information_schema.columns
--  WHERE table_schema = DATABASE() AND table_name = 'card_product'
--    AND column_name IN ('card_image_url', 'detail_url');
--
-- 2) 44·54만 채워져 있어야 한다.
-- SELECT card_product_id, card_name, detail_url
--   FROM card_product WHERE detail_url IS NOT NULL;
