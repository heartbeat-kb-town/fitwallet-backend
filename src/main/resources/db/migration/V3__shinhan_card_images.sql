-- V3 — 신한카드(issuer_id = 1) 20종 카드 이미지 URL 채우기 (2026-08-05)
--
-- 새로 만드는 DB에서는 V2(참조 데이터)가 이미 같은 값을 넣으므로 아무 행도 바뀌지 않는다.
-- 이 파일이 실제로 일하는 곳은 V2 이전 상태로 굳어 있는 DB(운영 RDS)다.
--
-- 이미지 출처: 신한카드 공식 CDN. 카드 상세 페이지의 og:image 메타에 실린 플레이트 코드를 그대로 쓴다.
--   규격은 420x264 가 기본이며 아래 3건은 예외다.
--     - id  9 (RPM Platinum#)     300x190  구형 플레이트라 원본이 작다
--     - id 12 (SOL트립앤샵 체크)     744x468  _s 변형이 없어 _d 를 쓴다
--     - id  7, 8, 14              .webp    정적 PNG 가 없고 애니메이션 플레이트만 제공된다
--
-- 멱등하다. 여러 번 실행해도 결과가 같다.

START TRANSACTION;

--  1. 신한카드 Mr.Life
UPDATE `card_product` SET `card_image_url` = 'https://www.shinhancard.com/pconts/static/images/card/plate/AUAARH_00_v_f_s.png'
 WHERE `card_product_id` = 1 AND `issuer_id` = 1;

--  2. 신한카드 Deep Dream
UPDATE `card_product` SET `card_image_url` = 'https://www.shinhancard.com/pconts/static/images/card/plate/BJABE3_00_h_f_s.png'
 WHERE `card_product_id` = 2 AND `issuer_id` = 1;

--  3. 신한카드 Deep Dream 체크
UPDATE `card_product` SET `card_image_url` = 'https://www.shinhancard.com/pconts/static/images/card/plate/BJBBE4_00_h_f_s.png'
 WHERE `card_product_id` = 3 AND `issuer_id` = 1;

--  4. 신한카드 O2O
UPDATE `card_product` SET `card_image_url` = 'https://www.shinhancard.com/pconts/static/images/card/plate/AXAAZE_00_h_f_s.png'
 WHERE `card_product_id` = 4 AND `issuer_id` = 1;

--  5. 신한카드 Way 체크
UPDATE `card_product` SET `card_image_url` = 'https://www.shinhancard.com/pconts/static/images/card/plate/BGKCSJ_E5_v_f_s.png'
 WHERE `card_product_id` = 5 AND `issuer_id` = 1;

--  6. 신한카드 Simple Plan
UPDATE `card_product` SET `card_image_url` = 'https://www.shinhancard.com/pconts/static/images/card/plate/POGDXC_G7_v_f_s.png'
 WHERE `card_product_id` = 6 AND `issuer_id` = 1;

--  7. 신한카드 처음 체크
UPDATE `card_product` SET `card_image_url` = 'https://www.shinhancard.com/pconts/static/images/card/plate/BGSDU9_E5_v_f_s.webp'
 WHERE `card_product_id` = 7 AND `issuer_id` = 1;

--  8. 신한카드 SOL Plan
UPDATE `card_product` SET `card_image_url` = 'https://www.shinhancard.com/pconts/static/images/card/plate/POFDVR_E5_v_f_s.webp'
 WHERE `card_product_id` = 8 AND `issuer_id` = 1;

--  9. 신한카드 RPM Platinum#
UPDATE `card_product` SET `card_image_url` = 'https://www.shinhancard.com/pconts/static/images/card/plate/AFH920_00_h_f_s.png'
 WHERE `card_product_id` = 9 AND `issuer_id` = 1;

-- 10. 신한카드 Unboxing
UPDATE `card_product` SET `card_image_url` = 'https://www.shinhancard.com/pconts/static/images/card/plate/BCECA3_00_v_f_s.png'
 WHERE `card_product_id` = 10 AND `issuer_id` = 1;

-- 11. 신한카드 Deep Store
UPDATE `card_product` SET `card_image_url` = 'https://www.shinhancard.com/pconts/static/images/card/plate/BCBBLO_E5_v_f_s.png'
 WHERE `card_product_id` = 11 AND `issuer_id` = 1;

-- 12. 신한카드 SOL트립앤샵 체크
UPDATE `card_product` SET `card_image_url` = 'https://www.shinhancard.com/pconts/static/images/card/plate/BUBDWL_E5_v_f_d.png'
 WHERE `card_product_id` = 12 AND `issuer_id` = 1;

-- 13. 신한카드 혼디모앙
UPDATE `card_product` SET `card_image_url` = 'https://www.shinhancard.com/pconts/static/images/card/plate/BOACF4_00_v_f_s.png'
 WHERE `card_product_id` = 13 AND `issuer_id` = 1;

-- 14. 신한카드 Deep Oil
UPDATE `card_product` SET `card_image_url` = 'https://www.shinhancard.com/pconts/static/images/card/plate/BIABE0_E5_v_f_s.webp'
 WHERE `card_product_id` = 14 AND `issuer_id` = 1;

-- 15. 신한카드 All Pass
UPDATE `card_product` SET `card_image_url` = 'https://www.shinhancard.com/pconts/static/images/card/plate/BEKBCU_00_h_f_s.png'
 WHERE `card_product_id` = 15 AND `issuer_id` = 1;

-- 16. 신한카드 The CLASSIC-Lite
UPDATE `card_product` SET `card_image_url` = 'https://www.shinhancard.com/pconts/static/images/card/plate/ALVBIJ_00_h_f_s.png'
 WHERE `card_product_id` = 16 AND `issuer_id` = 1;

-- 17. 신한카드 Deep Making
UPDATE `card_product` SET `card_image_url` = 'https://www.shinhancard.com/pconts/static/images/card/plate/BLABSJ_00_h_f_s.png'
 WHERE `card_product_id` = 17 AND `issuer_id` = 1;

-- 18. 신한카드 플리(체크)
UPDATE `card_product` SET `card_image_url` = 'https://www.shinhancard.com/pconts/static/images/card/plate/PLBCXR_R1_v_f_s.png'
 WHERE `card_product_id` = 18 AND `issuer_id` = 1;

-- 19. 신한카드 On 체크
UPDATE `card_product` SET `card_image_url` = 'https://www.shinhancard.com/pconts/static/images/card/plate/BGJCS5_E5_v_f_s.png'
 WHERE `card_product_id` = 19 AND `issuer_id` = 1;

-- 20. 신한카드 Pick E 체크
UPDATE `card_product` SET `card_image_url` = 'https://www.shinhancard.com/pconts/static/images/card/plate/BGND9K_E5_v_f_s.png'
 WHERE `card_product_id` = 20 AND `issuer_id` = 1;

COMMIT;

-- 검증: 0 이어야 한다
-- SELECT COUNT(*) AS null_image_rows FROM `card_product` WHERE `card_image_url` IS NULL;
