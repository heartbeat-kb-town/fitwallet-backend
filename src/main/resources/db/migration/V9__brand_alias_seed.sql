-- v26: brand_alias 시드. V8이 만든 빈 테이블을 채운다.
--
-- 출처 — 소상공인시장진흥공단 상가(상권)정보 202603 전수(2,725,318행)를 스캔해
-- 실제로 등장한 표기만 넣었다. 추측으로 채운 항목은 없다. 주석의 숫자가 실측 건수다.
--
-- 왜 필요한가 — 현실 데이터에서 별칭이 정규 브랜드명보다 많다.
--   CU        : 씨유 15,985  vs  cu 626          (25배)
--   메가MGC커피: 메가엠지씨커피 3,832  vs  메가mgc커피 91  (42배)
--   GS25      : 지에스25 11,008  vs  gs25 5,496   (2배)
-- brand_name만으로 매칭하면 66,844건 중 35,406건(53%)을 놓친다.
--
-- ⚠️ 지주·운영사 법인명은 넣지 않았다. 한 법인이 여러 브랜드를 운영해 별칭으로 쓰면
-- 오매칭이 난다. 실측으로 확인한 것들:
--   비케이알   -> 아웃백이 아니라 버거킹 운영사 ('비케이알버거킹부산연산DT점')
--   롯데지알에스-> 엔젤리너스뿐 아니라 롯데리아도 운영
--   지에스리테일-> GS25뿐 아니라 GS더프레시(슈퍼)도 운영
--   롯데쇼핑   -> 롯데마트뿐 아니라 롯데슈퍼·백화점도 운영
--   씨제이푸드빌-> VIPS뿐 아니라 뚜레쥬르도 운영 (빕스는 1/3에 불과)
--   신세계     -> 이마트가 아니라 신세계푸드·백화점 등 (주업종이 의류)
-- 반대로 단일 브랜드 전용 법인은 안전해서 넣었다 (코리아세븐, 비지에프리테일).
--
-- brand_id를 하드코딩하지 않고 brand_name으로 조인한다. 환경마다 brand_id가 다를
-- 가능성을 배제하고, 브랜드가 없는 환경에서는 조인 결과가 비어 조용히 넘어간다.
--
-- 멱등성 — alias에 UNIQUE(uk_brand_alias_alias)가 있어 재실행 시 INSERT IGNORE가
-- 전부 통과한다.
--
-- alias는 정규화 전 원문 표기다. 적재 스크립트가 읽을 때 정규화(공백·특수문자 제거,
-- 소문자화, 전각->반각)한 뒤 접두사 최장 일치로 매칭한다.

INSERT IGNORE INTO brand_alias (brand_id, alias)
SELECT b.brand_id, s.alias
  FROM brand b
  JOIN (
      -- 카페 · 디저트
      SELECT '메가MGC커피'    AS brand_name, '메가엠지씨커피'  AS alias  -- 3,832
      UNION ALL SELECT '메가MGC커피',    '메가커피'            -- 176
      UNION ALL SELECT '이디야커피',     '이디야'              -- 1,065
      UNION ALL SELECT '투썸플레이스',    '투썸'                -- 79
      UNION ALL SELECT '파리바게뜨',     '파리바게트'          -- 753 (오타 표기가 굳어진 사례)
      UNION ALL SELECT '파리바게뜨',     '파리크라상'          -- 22  (운영 법인, 파리바게뜨 전용)
      UNION ALL SELECT '던킨도너츠',     '던킨'                -- 284
      UNION ALL SELECT '엔젤리너스',     '엔제리너스'          -- 183 (V8에서 지운 중복 brand 43의 표기)

      -- 편의점 · 마트
      UNION ALL SELECT 'GS25',          '지에스25'            -- 11,008
      UNION ALL SELECT 'GS25',          '쥐에스25'            -- 2
      UNION ALL SELECT 'CU',            '씨유'                -- 15,985
      UNION ALL SELECT 'CU',            '비지에프리테일'      -- 137 (CU 전용 법인)
      UNION ALL SELECT '세븐일레븐',     '코리아세븐'          -- 252 (세븐일레븐 전용 법인)
      UNION ALL SELECT '세븐일레븐',     '7eleven'             -- 37
      UNION ALL SELECT '이마트24',       'emart24'             -- 12
      UNION ALL SELECT '올리브영',       '씨제이올리브영'      -- 1,337
      UNION ALL SELECT '올리브영',       'CJ올리브영'          -- 5
      UNION ALL SELECT '홈플러스',       '홈플'                -- 14
      UNION ALL SELECT '농협하나로마트',  '하나로마트'          -- 35
      UNION ALL SELECT '농협하나로마트',  '농협하나로'          -- 12
      UNION ALL SELECT '다이소',         '아성다이소'          -- 1 (다이소 전용 법인)
      UNION ALL SELECT '롯데VIC마켓',    '빅마켓'              -- 5
      UNION ALL SELECT '롯데VIC마켓',    'VIC마켓'             -- 2

      -- 푸드
      UNION ALL SELECT 'VIPS',          '빕스'                -- 30 (한글 표기)

      -- 주유
      UNION ALL SELECT 'GS칼텍스',       '지에스칼텍스'        -- 206
      UNION ALL SELECT 'SK에너지',       '에스케이에너지'      -- 7
      UNION ALL SELECT 'HD현대오일뱅크',  '현대오일뱅크'        -- 344
      UNION ALL SELECT 'S-OIL',         '에스오일'            -- 2
  ) s ON s.brand_name = b.brand_name;
