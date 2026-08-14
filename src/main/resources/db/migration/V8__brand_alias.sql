-- v26: 브랜드 별칭(brand_alias) 테이블을 신설하고, 별칭 부재로 생긴 중복 행을 정리한다.
--
-- 배경 — 같은 브랜드가 현실에서 여러 표기로 나타난다.
--   GS25       <- '지에스25'
--   CU         <- '씨유'
--   메가MGC커피 <- '메가엠지씨커피', '메가커피'
-- 지금은 이걸 표현할 자리가 없어 brand 테이블에 중복 행을 넣는 식으로 우회해 왔다.
-- 실제로 '엔젤리너스'(42)와 '엔제리너스'(43)가 그렇게 두 행으로 등록돼 있다
-- (uk_brand_brand_name UNIQUE를 피하려면 그 방법밖에 없었다).
--
-- 중복 행 방식의 문제는 한 브랜드의 혜택이 두 brand_id로 갈라진다는 것이다.
-- 가맹점이 어느 표기로 등록됐느냐에 따라 BRAND 스코프 혜택 매칭 결과가 달라진다
-- (BenefitMapper.findCandidates의 service_brand EXISTS 조건).
--
-- 이 테이블은 별칭을 brand 본체와 분리해, 표기가 늘어도 brand와 service_brand가
-- 불어나지 않게 한다.
--
-- ⚠️ 이번 마이그레이션은 테이블과 데이터 정합성만 다룬다. 별칭 시드(어떤 표기를
-- 어느 브랜드에 붙일지)는 공공데이터의 실제 상호명 분포를 확인한 뒤 별도 마이그레이션으로
-- 넣는다. 애플리케이션 서비스·매퍼 배선도 이번 범위가 아니다 — 지금은 성능 테스트용
-- 적재 스크립트만 이 테이블을 읽는다.

CREATE TABLE IF NOT EXISTS brand_alias (
    brand_alias_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_id       BIGINT NOT NULL,
    -- 사람이 읽고 관리하는 원문 표기를 그대로 담는다('지에스25', '메가엠지씨커피').
    -- 정규화(공백·특수문자 제거, 소문자화, 접미사 절단)는 읽는 쪽이 수행한다.
    -- 정규화형을 저장하지 않는 이유는 규모다 — 수백 행이라 전건을 메모리에 올려
    -- 매칭해도 무해하고, 표는 사람이 읽을 수 있어야 유지보수가 된다.
    alias          VARCHAR(100) NOT NULL,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- 한 별칭이 두 브랜드를 가리키면 매칭이 모호해진다. DB에서 막는다 —
    -- 이 제약이 위에 적은 중복 부채의 재발 방지 장치다.
    UNIQUE KEY uk_brand_alias_alias (alias),
    -- brand_id 조회용 인덱스를 따로 만들지 않는다. FK 제약이 인덱스를 자동 생성하고
    -- 그것이 "한 브랜드의 별칭 전부" 조회를 그대로 커버한다
    -- (V1의 uk_pt_payment_session_id가 fk_pt_session의 인덱스를 겸하는 것과 같다).
    -- 별도 CREATE INDEX는 멱등하지도 않다 — MySQL에 CREATE INDEX IF NOT EXISTS가 없어
    -- 재실행하면 "Duplicate key name"으로 마이그레이션이 실패한다.
    CONSTRAINT fk_brand_alias_brand
        FOREIGN KEY (brand_id) REFERENCES brand (brand_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- '엔제리너스'(brand_id = 43) 정리.
--
-- brand를 FK로 참조하는 테이블은 store와 service_brand 둘뿐이고, 43번은 양쪽 모두에서
-- 참조가 0건인 고아 행이다(실측 확인). '엔젤리너스'(42)만 service_id = 54
-- '일반 할인 - 커피전문점'에 연결돼 있다. 따라서 삭제해도 혜택 매칭 결과가 바뀌지 않는다.
--
-- 삭제 후 '엔제리너스'라는 표기는 별칭 시드에서 42번으로 흡수시킨다.
--
-- 멱등성 · 안전성 — 조건을 전부 건 DELETE라 재실행하면 0행이 지워지고, 혹시 다른 환경에서
-- 43번에 참조가 생겼다면 아무것도 지우지 않는다. brand_id뿐 아니라 brand_name까지 확인해
-- 다른 환경에서 43번이 엉뚱한 브랜드일 가능성도 배제한다.
DELETE FROM brand
 WHERE brand_id = 43
   AND brand_name = '엔제리너스'
   AND NOT EXISTS (SELECT 1 FROM service_brand sb WHERE sb.brand_id = 43)
   AND NOT EXISTS (SELECT 1 FROM store s WHERE s.brand_id = 43);
