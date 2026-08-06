-- =========================================================
-- fitwallet 스키마 DDL (확정 ERD v25)
-- MySQL 8.x / InnoDB / utf8mb4
--
-- docker-entrypoint-initdb.d 스크립트. 컨테이너 최초 기동(빈 볼륨) 시
-- 001 -> 002 -> 003 순으로 자동 실행된다. DB는 entrypoint가 MYSQL_DATABASE로
-- 이미 만들어 두고 그 DB에 접속한 상태로 실행하므로, 여기서 CREATE DATABASE /
-- USE 를 하지 않는다(.env의 MYSQL_DATABASE를 바꿔도 그대로 동작하도록).
--
-- 스키마를 바꿀 때는 docs/erd.md 도 함께 갱신한다.
-- =========================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =========================================================
-- 회원
-- =========================================================

DROP TABLE IF EXISTS users;
CREATE TABLE users (
    user_id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    -- v16: ERDCloud 반영. email -> login_id 로 변경(직접가입 로그인 식별자, 현재는 이메일 값).
    -- 소셜(카카오) 가입 유저는 login_id가 없어 NULL.
    login_id          VARCHAR(255) NULL,
    name              VARCHAR(50)  NOT NULL,
    -- v19: 회원가입 화면 반영. 휴대폰 번호(예: 010-0000-0000). 소셜 가입 등
    -- 미입력 경로가 있을 수 있어 NULL 허용.
    phone             VARCHAR(20)  NULL,
    -- v16: ERDCloud 반영. 직접가입(이메일+비번) 유저의 로그인 비밀번호 해시.
    -- 카카오 가입 유저는 비밀번호가 없어 NULL.
    password_hash     VARCHAR(255) NULL,
    -- v16: 카카오 회원번호. 직접가입 유저는 카카오 연동이 없어 NULL(NOT NULL -> NULL).
    provider_user_id  VARCHAR(100) NULL,
    payment_pin_hash  VARCHAR(255) NULL,
    -- v16: ERDCloud 반영. 위치 정보 동의 여부(주변 가맹점 위치 기반 기능용).
    -- boolean 플래그. MySQL BOOLEAN == TINYINT(1)로, 로컬 기존 플래그
    -- (is_used_app/is_eligible)와 동일 타입/컨벤션.
    is_location_agreed  TINYINT(1) NOT NULL DEFAULT 0,
    -- v19: 회원가입 화면의 [선택] 마케팅 정보 수신 동의. 필수 약관(서비스/개인정보)은
    -- 가입 조건이라 미저장하고, 선택 동의만 플래그로 보관. 위치정보 동의
    -- (is_location_agreed)와는 별개 항목. 기본 미동의(0).
    is_marketing_agreed TINYINT(1) NOT NULL DEFAULT 0,
    -- v22: 인증 관련. 인증 세션 식별자·PIN 실패 카운트.
    -- v23: 리프레시 토큰(refresh_token_hash/refresh_token_expires_at)은 별도
    -- refresh_token 테이블로 분리했다(아래 참고).
    -- v25: qr_auth_id -> pin_auth_id 로 rename. 이 식별자가 가리키는 것은 QR 발급이 아니라
    -- 결제 PIN 인증 세션이라, 짝을 이루는 auth_expires_at/auth_is_used/pin_fail_count와
    -- 이름이 어긋나 있었다(QR 세션 열쇠는 payment_session.session_token 쪽이다).
    -- pin_auth_id: 결제 PIN 인증 세션 식별자. auth_expires_at 만료 시각, auth_is_used 사용 여부.
    pin_auth_id               VARCHAR(64) NULL,
    auth_expires_at           DATETIME NULL,
    auth_is_used              TINYINT(1) NOT NULL DEFAULT 0,
    -- pin_fail_count: 결제 PIN 연속 실패 횟수(잠금 판정용). 성공 시 0으로 초기화.
    pin_fail_count            INT NOT NULL DEFAULT 0,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_users_login_id (login_id),
    UNIQUE KEY uk_users_provider_user_id (provider_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- v23: 리프레시 토큰을 users 컬럼에서 분리한 전용 테이블. 유저당 활성 리프레시
-- 토큰은 최대 1개(재로그인 시 갱신)라 user_id를 UNIQUE로 둔다.
DROP TABLE IF EXISTS refresh_token;
CREATE TABLE refresh_token (
    refresh_token_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT   NOT NULL,
    token_hash        CHAR(64) NOT NULL,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_refresh_token_user_id (user_id),
    CONSTRAINT fk_refresh_token_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =========================================================
-- 카드 마스터
-- =========================================================

DROP TABLE IF EXISTS issuer;
CREATE TABLE issuer (
    issuer_id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    card_company_name  VARCHAR(100) NOT NULL,
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_issuer_card_company_name (card_company_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS card_product;
CREATE TABLE card_product (
    card_product_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
    issuer_id        BIGINT NOT NULL,
    card_name        VARCHAR(100) NOT NULL,
    card_type        VARCHAR(20) NOT NULL,
    card_image_url   VARCHAR(500) NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_card_product_issuer
        FOREIGN KEY (issuer_id) REFERENCES issuer (issuer_id),
    CONSTRAINT ck_card_product_card_type
        CHECK (card_type IN ('CREDIT', 'DEBIT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_card_product_issuer_id ON card_product (issuer_id);

-- =========================================================
-- 가맹점 · 분류
-- =========================================================

DROP TABLE IF EXISTS category;
CREATE TABLE category (
    category_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_name  VARCHAR(50) NOT NULL,
    -- v16: ERDCloud 반영. 업종(카테고리) 대표 이미지 S3 경로.
    category_image_url VARCHAR(500) NULL,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_category_category_name (category_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS brand;
CREATE TABLE brand (
    brand_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_name   VARCHAR(100) NOT NULL,
    category_id  BIGINT NOT NULL,
    -- v16: ERDCloud 반영. 상표(브랜드) 대표 이미지 S3 경로.
    brand_image_url VARCHAR(500) NULL,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_brand_brand_name (brand_name),
    CONSTRAINT fk_brand_category
        FOREIGN KEY (category_id) REFERENCES category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_brand_category_id ON brand (category_id);

-- v14: 적립(ACCUMULATE) 혜택이 쌓이는 포인트 화폐(마이신한포인트, M포인트,
-- KB포인트리 등)와 그 원화 환산 비율. 이슈어 하나 안에도 여러 화폐가 공존할 수
-- 있어(예: KB 해피포인트 vs 포인트리) benefit_service 단위로 참조한다.
DROP TABLE IF EXISTS point_currency;
CREATE TABLE point_currency (
    point_currency_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
    currency_name       VARCHAR(50) NOT NULL,
    krw_per_point        DECIMAL(10,4) NOT NULL,
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_point_currency_currency_name (currency_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS store;
-- v17: store를 상표(brand) 대신 업종(category) 중심으로 변경. 실제 오프라인 매장은
-- 대부분 등록 브랜드에 해당하지 않으므로(동네 식당/개인 병원/로컬 카페) 모든 매장이
-- 항상 갖는 업종(category_id, NOT NULL)을 직접 참조한다. 이전엔 category를
-- store -> brand -> category 로 추론했는데, 브랜드 미상 매장은 업종조차 못 잡혀 어떤
-- 혜택에도 매칭되지 않는 문제가 있었다. brand_id는 스타벅스/GS25 같은 인식 가능한
-- 체인일 때만 채워(NULL 허용) BRAND-scope 혜택 매칭을 살린다.
-- 규칙(적재 시 보장): brand_id가 있으면 brand.category_id == store.category_id.
CREATE TABLE store (
    store_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id      BIGINT NOT NULL,
    brand_id         BIGINT NULL,
    store_name       VARCHAR(100) NOT NULL,
    store_rank       INT NULL,
    latitude         DECIMAL(10,8) NULL,
    longitude        DECIMAL(11,8) NULL,
    address          VARCHAR(255) NULL,
    kakao_place_id   VARCHAR(50) NULL,
    -- v16: ERDCloud 반영. 가맹점 QR 결제용 QR코드 인코딩 문자열(가맹점당 고유 -> UNIQUE).
    -- v23: qr_code_string -> store_qr_token 으로 컬럼명 변경.
    store_qr_token   VARCHAR(64) NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_store_kakao_place_id (kakao_place_id),
    UNIQUE KEY uk_store_qr_token (store_qr_token),
    CONSTRAINT fk_store_category
        FOREIGN KEY (category_id) REFERENCES category (category_id),
    CONSTRAINT fk_store_brand
        FOREIGN KEY (brand_id) REFERENCES brand (brand_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_store_category_id ON store (category_id);
CREATE INDEX idx_store_brand_id ON store (brand_id);

-- =========================================================
-- 혜택
-- =========================================================

DROP TABLE IF EXISTS benefit_plan_group;
-- v12: limit_unit 삭제. benefit_limit.limit_basis에 POINT가 생기면서(v11)
-- 그룹의 단위(KRW/POINT)가 소속 benefit_limit.limit_basis(AMOUNT/POINT)로
-- 완전히 대체 가능해졌다(실측: 18개 그룹 전부 단일 basis로 일치, 불일치/누락 0건).
CREATE TABLE benefit_plan_group (
    plan_group_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    card_product_id  BIGINT NOT NULL,
    group_name       VARCHAR(100) NOT NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_benefit_plan_group_card_product
        FOREIGN KEY (card_product_id) REFERENCES card_product (card_product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_benefit_plan_group_card_product_id ON benefit_plan_group (card_product_id);

-- 카테고리(1~6종)는 이 테이블에 컬럼으로 두지 않고 전부 service_category(M:N,
-- 아래쪽에 정의)로 표현한다. 카테고리가 1개든 여러 개든 업종 제한이 없든
-- 전부 같은 방식(service_category row)으로 조회할 수 있게 하기 위함(v7).
DROP TABLE IF EXISTS benefit_service;
CREATE TABLE benefit_service (
    service_id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    card_product_id       BIGINT NOT NULL,
    plan_group_id         BIGINT NULL,
    benefit_name          VARCHAR(100) NOT NULL,
    benefit_type          VARCHAR(20) NOT NULL,
    value_type            VARCHAR(10) NOT NULL,
    value_number          DECIMAL(15,2) NOT NULL,
    scope_type            VARCHAR(20) NOT NULL,
    -- v9: "조건 없음"을 NULL 대신 0으로 표현(NULL은 <= 비교에서 UNKNOWN이 되어
    -- "조건 없음" 행이 조회 쿼리에서 조용히 누락되는 문제가 있었음).
    min_payment_amount    DECIMAL(15,2) NOT NULL DEFAULT 0,
    -- v15: 전월실적 구간의 상한(exclusive). 구간을 반열린 인터벌 [min, max)로
    -- 표현해, 유저 전월실적 P에 대해 min<=P<max 로 그룹당 정확히 한 구간이
    -- 뽑히게 한다(rate group을 몰라도 구간 선택이 됨). NULL=상한 없음(최상위
    -- 구간, 실적 문턱만 있는 단일 혜택, 실적 무관 혜택 전부 NULL).
    max_payment_amount    DECIMAL(15,2) NULL,
    per_tx_limit_amount   DECIMAL(15,2) NULL,
    -- v10: monthly_limit/monthly_count_limit 제거. 165건 전부 NULL로, 월/일
    -- 한도는 이미 benefit_tier+benefit_limit이 전담하고 있어 한 번도 안 쓰인
    -- 죽은 컬럼이었음.
    -- v14: point_currency_id 추가. ACCUMULATE 행은 반드시 값이 있어야
    -- 원화 환산이 가능하고(카드 추천 로직에서 CASHBACK과 비교하려면 필요),
    -- CASHBACK 행은 원화 자체라 환산이 필요 없어 NULL.
    point_currency_id     BIGINT NULL,
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_benefit_service_card_product
        FOREIGN KEY (card_product_id) REFERENCES card_product (card_product_id),
    CONSTRAINT fk_benefit_service_plan_group
        FOREIGN KEY (plan_group_id) REFERENCES benefit_plan_group (plan_group_id),
    CONSTRAINT fk_benefit_service_point_currency
        FOREIGN KEY (point_currency_id) REFERENCES point_currency (point_currency_id),
    CONSTRAINT ck_benefit_service_benefit_type
        CHECK (benefit_type IN ('ACCUMULATE', 'CASHBACK')),
    CONSTRAINT ck_benefit_service_value_type
        CHECK (value_type IN ('FIXED', 'RATE')),
    -- ACCUMULATE는 point_currency_id가 반드시 있어야 하고, CASHBACK은 원화라
    -- 필요 없다(있으면 오히려 의미가 애매해지므로 강제로 NULL).
    CONSTRAINT ck_benefit_service_point_currency_required
        CHECK ((benefit_type = 'ACCUMULATE') = (point_currency_id IS NOT NULL)),
    -- v8: INDUSTRY/CATEGORY_LIST 통합. 둘 다 service_brand는 항상 비어 있고
    -- service_category 개수(1개/6개 vs 2~4개)만으로 이미 구분 가능해 별도 라벨의
    -- 실익이 없었음. BRAND_LIST -> BRAND로 이름만 단축.
    CONSTRAINT ck_benefit_service_scope_type
        CHECK (scope_type IN ('BRAND', 'INDUSTRY')),
    -- v15: 구간 상한은 있으면 반드시 하한보다 커야 한다(빈/역전 구간 방지).
    -- 구간 사이의 빈틈/겹침(cross-row)은 CHECK로 못 막으므로 로드 시 보장한다.
    CONSTRAINT ck_benefit_service_max_payment_amount
        CHECK (max_payment_amount IS NULL OR max_payment_amount > min_payment_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_benefit_service_card_product_id ON benefit_service (card_product_id);
CREATE INDEX idx_benefit_service_plan_group_id ON benefit_service (plan_group_id);
CREATE INDEX idx_benefit_service_point_currency_id ON benefit_service (point_currency_id);

DROP TABLE IF EXISTS benefit_tier;
CREATE TABLE benefit_tier (
    tier_id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_group_id          BIGINT NULL,
    service_id             BIGINT NULL,
    tier_order             INT NOT NULL,
    min_prev_month_spend   DECIMAL(15,2) NOT NULL,
    max_prev_month_spend   DECIMAL(15,2) NULL,
    -- v13: limit_amount 삭제. plan_group 소속 tier(45건)에서만 값이 있었고,
    -- 전부 같은 tier_id를 참조하는 benefit_limit.limit_value와 완전히 중복
    -- (실측: 불일치 0건). 단독 tier(155건)는 애초에 NULL이라 쓰인 적도 없었음.
    created_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_benefit_tier_plan_group
        FOREIGN KEY (plan_group_id) REFERENCES benefit_plan_group (plan_group_id),
    CONSTRAINT fk_benefit_tier_service
        FOREIGN KEY (service_id) REFERENCES benefit_service (service_id),
    CONSTRAINT ck_benefit_tier_xor
        CHECK ((plan_group_id IS NULL) <> (service_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_benefit_tier_plan_group_id ON benefit_tier (plan_group_id);
CREATE INDEX idx_benefit_tier_service_id ON benefit_tier (service_id);

DROP TABLE IF EXISTS benefit_limit;
CREATE TABLE benefit_limit (
    limit_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    tier_id        BIGINT NOT NULL,
    limit_basis    VARCHAR(10) NOT NULL,
    limit_period   VARCHAR(20) NOT NULL,
    limit_value    DECIMAL(15,2) NOT NULL,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_benefit_limit_tier
        FOREIGN KEY (tier_id) REFERENCES benefit_tier (tier_id),
    -- v11: POINT 추가. AMOUNT=원화 금액, POINT=포인트 개수로 명확히 분리한다.
    -- 이전엔 두 개념이 전부 AMOUNT 하나로 저장돼, 같은 ACCUMULATE(적립) 혜택인데
    -- 한도가 "원화 환산"인지(RPM Platinum#) "포인트 개수"인지(마이신한포인트 등)
    -- DB만 보고는 구분할 수 없었다(단독 tier엔 benefit_plan_group.limit_unit 같은
    -- 단위 컬럼이 아예 없었음).
    CONSTRAINT ck_benefit_limit_limit_basis
        CHECK (limit_basis IN ('COUNT', 'AMOUNT', 'POINT')),
    CONSTRAINT ck_benefit_limit_limit_period
        CHECK (limit_period IN ('PER_TRANSACTION', 'DAY', 'MONTH', 'YEAR')),
    -- 같은 tier에 같은 종류(기준+주기)의 한도가 중복/모순되게 들어가는 것을 방지
    -- (예: 같은 tier에 AMOUNT/MONTH 한도가 두 개 동시에 존재할 수 없음)
    CONSTRAINT uq_benefit_limit_tier_basis_period
        UNIQUE (tier_id, limit_basis, limit_period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_benefit_limit_tier_id ON benefit_limit (tier_id);

DROP TABLE IF EXISTS service_brand;
CREATE TABLE service_brand (
    service_brand_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_id        BIGINT NOT NULL,
    brand_id          BIGINT NOT NULL,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_service_brand_service_id_brand_id (service_id, brand_id),
    CONSTRAINT fk_service_brand_service
        FOREIGN KEY (service_id) REFERENCES benefit_service (service_id),
    CONSTRAINT fk_service_brand_brand
        FOREIGN KEY (brand_id) REFERENCES brand (brand_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- benefit_service가 어느 카테고리(1~6종)에 해당하는지는 전부 이 M:N 테이블로만
-- 표현한다(v7). 예전엔 benefit_service.category_id 컬럼이 있어서 "카테고리 1개인
-- 경우엔 category_id 직접 참조, 여러 개인 경우엔 service_category 경유"로
-- 갈라져 있었는데, 그 이원화 때문에 조회할 때마다 두 경로를 다 확인해야 하는
-- 문제가 있었다(HYUNDAI_REPORT.md/KB_REPORT.md의 "직접 참조 0건, 간접 참조 2건"
-- 같은 서술이 그 증상). category_id 컬럼을 아예 없애고 서비스 하나당
-- service_category에 관련 카테고리를 전부 나열하는 방식으로 통일했다:
--   - 카테고리 1개(예: "TIME 할인 - 편의점") -> service_category 1행
--   - 카테고리 여러 개(예: "더해드림", DREAM 영역 2개) -> service_category 2행 이상
--   - 업종 제한 없음(예: "모두드림 기본적립", 전 가맹점 대상) -> 6종 전부 매핑
-- service_brand와 이제 완전히 대칭 구조(둘 다 "이 서비스가 여러 대상에
-- 걸린다"는 걸 표현하는 M:N 테이블이고, 항상 사용됨).
DROP TABLE IF EXISTS service_category;
CREATE TABLE service_category (
    service_category_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_id            BIGINT NOT NULL,
    category_id            BIGINT NOT NULL,
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_service_category_service_id_category_id (service_id, category_id),
    CONSTRAINT fk_service_category_service
        FOREIGN KEY (service_id) REFERENCES benefit_service (service_id),
    CONSTRAINT fk_service_category_category
        FOREIGN KEY (category_id) REFERENCES category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =========================================================
-- 카드 · 결제 내역
-- =========================================================

DROP TABLE IF EXISTS user_card;
-- v16: ERDCloud 반영. bank_account 테이블을 제거하고 은행명/잔액을 이 테이블로
-- 흡수(비정규화). masked_number는 카드 앞4/뒤4(first4/last4)로 분리.
CREATE TABLE user_card (
    user_card_id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                  BIGINT NOT NULL,
    card_product_id          BIGINT NOT NULL,
    first4                   CHAR(4) NOT NULL,
    last4                    CHAR(4) NOT NULL,
    expiry_date              DATE NOT NULL,
    display_order            INT NOT NULL,
    -- 은행 계좌 정보(구 bank_account에서 흡수). 카드 종류별로 쓰이는 컬럼이 갈린다:
    --   bank_name, balance                     -> 체크카드(DEBIT) 전용 (balance=모의결제용 잔액, 구 mock_balance)
    --   credit_limit, scheduled_payment_amount -> 신용카드(CREDIT) 전용
    bank_name                VARCHAR(50) NULL,
    balance                  DECIMAL(15,2) NULL,
    credit_limit             DECIMAL(15,2) NULL,
    scheduled_payment_amount DECIMAL(15,2) NULL,
    created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- v20: 카드 삭제는 물리 DELETE 불가(payment_transaction이 user_card를 FK 참조 →
    -- 결제 이력 보존 필요). 소프트 삭제로 처리: 삭제 시 is_deleted=1, 살아있으면 0.
    -- 기존 boolean 플래그(is_used_app/is_eligible 등)와 동일 TINYINT(1) 컨벤션.
    -- 조회는 항상 is_deleted = 0 조건. 같은 카드 재등록은 새 행 INSERT가 아니라
    -- 삭제행의 is_deleted를 0으로 되살려(재활성화, A안) UNIQUE 제약을 그대로 유지.
    is_deleted               TINYINT(1) NOT NULL DEFAULT 0,
    -- 같은 유저가 동일 카드 상품을 중복 등록하지 못하게 방지(소프트 삭제행 포함 —
    -- 재등록은 재활성화로 처리하므로 (user_id, card_product_id) 조합은 항상 유일).
    UNIQUE KEY uk_user_card_user_id_card_product_id (user_id, card_product_id),
    CONSTRAINT fk_user_card_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_user_card_card_product
        FOREIGN KEY (card_product_id) REFERENCES card_product (card_product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_user_card_user_id ON user_card (user_id);
CREATE INDEX idx_user_card_card_product_id ON user_card (card_product_id);

-- 결제 세션. 사용자 등록 카드로 특정 가맹점에서 (QR 등) 결제를 진행하는 동안의 세션
-- 상태를 추적한다.
-- v25: payment_transaction 이 payment_session 을 FK 참조하게 되면서(fk_pt_session),
-- 이 블록을 "v16 신규 테이블" 섹션에서 여기로 올려 파일을 FK 의존 순서로 맞췄다.
-- 파일 상단의 SET FOREIGN_KEY_CHECKS = 0 덕에 전방 참조로도 적재되긴 하지만,
-- 시드(003-seed.sql)는 FK 검사가 켜진 채 실행돼 순서를 반드시 지켜야 하므로
-- 스키마도 같은 순서로 두는 편이 읽기 쉽다.
DROP TABLE IF EXISTS payment_session;
CREATE TABLE payment_session (
    payment_session_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_card_id           BIGINT NOT NULL,
    -- v23: QR 스캔 전에는 가맹점이 아직 확정되지 않은 세션이 있을 수 있어 NULL 허용으로 변경.
    store_id               BIGINT NULL,
    session_token          VARCHAR(64) NOT NULL,
    -- v25: 앱이 만든 QR이 가맹점에서 스캔되는 시점에 발급되는 "결제 건" 식별자.
    -- 스캔 이후의 결제 요청들이 이 값으로 세션을 조회한다. session_token 과는 발급 시점과
    -- 용도가 다르다 — session_token 은 QR 생성(세션 시작) 시점에 발급되는 세션 자체의
    -- 열쇠라 NOT NULL이고, payment_id 는 스캔 전에는 존재하지 않아 NULL이다.
    payment_id             VARCHAR(64) NULL,
    amount                 DECIMAL(12,2) NULL,
    status                 VARCHAR(20) NOT NULL,
    -- v25: 실패 사유. status = 'FAILED' 일 때만 채운다. 만료는 status = 'EXPIRED' 가 이미
    -- 표현하므로 값 집합에 넣지 않았다(넣으면 두 컬럼이 어긋난 상태가 표현 가능해진다).
    fail_reason            VARCHAR(50) NULL,
    expires_at             DATETIME NOT NULL,
    created_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_payment_session_token (session_token),
    -- v25: 결제 건 단위 유일. MySQL UNIQUE는 NULL을 중복 허용하므로 스캔 전(PENDING)
    -- 세션이 여러 개 있어도 충돌하지 않는다.
    UNIQUE KEY uk_payment_session_payment_id (payment_id),
    CONSTRAINT fk_payment_session_user_card
        FOREIGN KEY (user_card_id) REFERENCES user_card (user_card_id),
    CONSTRAINT fk_payment_session_store
        FOREIGN KEY (store_id) REFERENCES store (store_id),
    -- v23: 세션 상태: PENDING -> SCANNED -> PROCESSING -> COMPLETED, 그 외 EXPIRED/FAILED.
    CONSTRAINT ck_payment_session_status
        CHECK (status IN ('PENDING','SCANNED','PROCESSING','COMPLETED','EXPIRED','FAILED')),
    -- v26: MOCK_RANDOM_DECLINE 추가. 결제 결과 조회 Mock이 무작위로 승인을 거절시킬 때
    -- 쓰는 임시 값이다. 실제 PG 연동 시 진짜 거절 사유 코드로 대체될 예정.
    CONSTRAINT ck_payment_session_fail_reason
        CHECK (fail_reason IS NULL OR fail_reason IN (
            'PIN_MISMATCH',        -- 결제 PIN 불일치
            'PIN_LOCKED',          -- PIN 연속 실패로 잠금 (users.pin_fail_count)
            'CANCELED_BY_USER',    -- 사용자 직접 취소
            'CARD_UNAVAILABLE',    -- 카드 삭제/사용 불가
            'SYSTEM_ERROR',        -- 그 외 산발 오류
            'MOCK_RANDOM_DECLINE'  -- v26: 결제 결과 Mock의 무작위 승인 거절 (임시)
        ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_payment_session_user_card_id ON payment_session (user_card_id);
CREATE INDEX idx_payment_session_store_id ON payment_session (store_id);

DROP TABLE IF EXISTS payment_transaction;
-- v16: user_card_transaction -> payment_transaction 로 rename.
-- category_id 제거(store_id -> store.category_id 로 직접 참조 가능. v17 이전엔
-- store -> brand -> category 였음), matched_service_id 제거.
-- approved_at 추가.
CREATE TABLE payment_transaction (
    payment_transaction_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_card_id              BIGINT NOT NULL,
    store_id                  BIGINT NULL,
    -- v25: 이 거래로 확정된 결제 세션. 앱을 거치지 않은 거래(외부 승인 내역)는 NULL.
    payment_session_id        BIGINT NULL,
    amount                    DECIMAL(15,2) NOT NULL,
    discount_amount           DECIMAL(15,2) NOT NULL DEFAULT 0,
    -- v22: 최종금액(= amount - discount_amount). 할인 적용 후 실제 결제 금액.
    final_amount              DECIMAL(15,2) NOT NULL,
    -- 실제 결제(승인) 발생 시각(비즈니스 시각). created_at(레코드 생성)과 분리.
    paid_at                   DATETIME NOT NULL,
    is_used_app               TINYINT(1) NOT NULL DEFAULT 0,
    is_eligible               TINYINT(1) NOT NULL DEFAULT 1,
    -- v18: 적용 혜택 / 놓친 혜택을 인라인. 현행 정책상 결제:적용혜택은 "건당 최선 1개"라
    -- 1:1, 놓친 혜택도 "최선 대안 1개"만 → 별도 테이블(benefit_application/missed_benefit)
    -- 대신 여기에 둔다. discount_amount 는 적용된 혜택 하나의 혜택값(할인+적립 원환산).
    -- (진짜 혜택 중첩/다중 대안이 필요해지면 benefit_service에 중첩 규칙을 추가하고
    --  적용내역을 별도 테이블로 분리하는 세트 변경이 필요.)
    applied_benefit_service_id   BIGINT NULL,        -- 적용된 혜택(attribution). 혜택 0이면 NULL
    applied_tier_id              BIGINT NULL,        -- 적용 혜택의 한도 그룹(한도 소진 집계 키)
    better_user_card_id          BIGINT NULL,        -- 놓친 혜택: 더 유리했던 보유 카드. 없으면 NULL
    alternative_discount_amount  DECIMAL(15,2) NULL, -- 그 카드였다면 받았을 혜택값
    missed_amount                DECIMAL(15,2) NULL, -- 놓친 금액(= alternative - discount_amount)
    created_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- v25: 세션 1건은 거래 최대 1건으로 확정된다(1:1). NULL은 중복 허용되므로 앱을
    -- 거치지 않은 거래 다수와 공존한다. 이 UNIQUE 키가 fk_pt_session 의 인덱스를
    -- 겸하므로 별도 CREATE INDEX 를 두지 않는다.
    UNIQUE KEY uk_pt_payment_session_id (payment_session_id),
    CONSTRAINT fk_pt_user_card
        FOREIGN KEY (user_card_id) REFERENCES user_card (user_card_id),
    CONSTRAINT fk_pt_store
        FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_pt_session
        FOREIGN KEY (payment_session_id) REFERENCES payment_session (payment_session_id),
    CONSTRAINT fk_pt_applied_service
        FOREIGN KEY (applied_benefit_service_id) REFERENCES benefit_service (service_id),
    CONSTRAINT fk_pt_applied_tier
        FOREIGN KEY (applied_tier_id) REFERENCES benefit_tier (tier_id),
    CONSTRAINT fk_pt_better_user_card
        FOREIGN KEY (better_user_card_id) REFERENCES user_card (user_card_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_pt_card_paid_at ON payment_transaction (user_card_id, paid_at);
CREATE INDEX idx_pt_store_id ON payment_transaction (store_id);
-- 한도 소진 집계용: (카드, 적용 tier, 시각) 범위로 COUNT/SUM
CREATE INDEX idx_pt_card_tier_paid_at ON payment_transaction (user_card_id, applied_tier_id, paid_at);

-- =========================================================
-- v16: ERDCloud 반영 — 신규 테이블 (검색 기록 · 놓친 혜택)
-- v25: 결제 세션(payment_session)은 FK 의존 순서 때문에 "카드 · 결제 내역" 섹션으로 옮겼다.
-- =========================================================

-- 사용자별 최근 검색어 기록.
-- v24: (user_id, keyword) 유일 제약을 애플리케이션에서 DB로 내렸다. 같은 키워드를
-- 재검색하면 행을 추가하지 않고 searched_at만 갱신하는 것이 명세인데, 제약이 없어
-- 애플리케이션이 "UPDATE -> 0행이면 INSERT"로 막고 있었다. 그 패턴은 동시 요청 둘이
-- 모두 UPDATE에서 0행을 받고 둘 다 INSERT하면 중복이 생기는 경쟁 조건이 있고,
-- 실제로 시드가 이 불변식을 위반한 상태였다(40행 중 13개 키워드 중복 -> v24 직전 16행 정리).
-- 제약이 있으면 INSERT ... ON DUPLICATE KEY UPDATE 한 문장으로 원자적으로 끝난다.
--
-- v24: 검색어 보존 정책이 "사용자당 최대 5개 저장"에서 "전부 보관 + 화면에 최신 5개 표시"로
-- 바뀌면서(요구사항 LOCATION-003 개정) 이 테이블에 남은 불변식은 이 유일 제약 하나다.
-- 5개 상한은 CHECK가 서브쿼리를 못 쓰고 트리거가 자기 테이블을 수정할 수 없어 애초에
-- DB로 표현할 수 없는 보존 정책이었는데, 정책이 바뀌면서 그 문제 자체가 없어졌다.
--
-- v24: idx_search_history_user_id 삭제. 새 UNIQUE 키의 좌측 프리픽스가 user_id라
-- WHERE user_id = ? 조회를 그대로 커버해 중복이었다.
--
-- keyword 콜레이션은 utf8mb4_0900_ai_ci(대소문자·악센트 무시)라 'CU'와 'cu'가 같은 키다.
-- 재검색 시 기존 항목이 갱신되고 검색어 칩이 둘로 갈라지지 않으므로 의도된 동작이다.
DROP TABLE IF EXISTS search_history;
CREATE TABLE search_history (
    search_history_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT NOT NULL,
    keyword            VARCHAR(100) NOT NULL,
    searched_at        DATETIME NOT NULL,
    UNIQUE KEY uk_search_history_user_id_keyword (user_id, keyword),
    CONSTRAINT fk_search_history_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- v18: 놓친 혜택(missed_benefit) 테이블 제거 → payment_transaction 에 인라인.
-- 결제:놓친혜택이 "최선 대안 1개"로 1:1이라 별도 테이블의 이점이 없어, 트랜잭션 행의
-- better_user_card_id / alternative_discount_amount / missed_amount 컬럼으로 이동함.
-- (payment_transaction 정의 참고)

-- =========================================================
-- v21: 이벤트(한시적 프로모션)
-- =========================================================

DROP TABLE IF EXISTS card_event;
-- v21: 홈2 이벤트 화면. 한시적 프로모션이라 상시 혜택(benefit_service)으로 표현 불가
-- (benefit_service엔 기간 컬럼이 없음). 요약/기간/링크만 최소로 보관한다.
-- 대상은 특정 카드(card_product_id) 또는 그 카드사 전체(issuer_id) 중 정확히 하나(XOR).
-- 카드 특정이 안 되는 issuer-wide 이벤트는 issuer_id로 "그 카드사 모든 카드 대상"임을 명시.
CREATE TABLE card_event (
    event_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    card_product_id  BIGINT NULL,               -- 특정 카드 이벤트
    issuer_id        BIGINT NULL,               -- 카드 특정 불가 시: 그 카드사 모든 카드 대상
    summary          VARCHAR(255) NOT NULL,     -- 한줄 요약("스타벅스 결제 시 10% 할인" 등)
    starts_at        DATE NOT NULL,
    ends_at          DATE NOT NULL,
    detail_url       VARCHAR(500) NULL,         -- 자세히보기 링크
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_card_event_card_product
        FOREIGN KEY (card_product_id) REFERENCES card_product (card_product_id),
    CONSTRAINT fk_card_event_issuer
        FOREIGN KEY (issuer_id) REFERENCES issuer (issuer_id),
    -- 특정 카드 XOR 카드사 전체: 정확히 하나만 채워진다(benefit_tier 관용구와 동일).
    CONSTRAINT ck_card_event_target_xor
        CHECK ((card_product_id IS NULL) <> (issuer_id IS NULL)),
    CONSTRAINT ck_card_event_period
        CHECK (ends_at >= starts_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_card_event_card_product_id ON card_event (card_product_id);
CREATE INDEX idx_card_event_issuer_id       ON card_event (issuer_id);
CREATE INDEX idx_card_event_period          ON card_event (starts_at, ends_at);

SET FOREIGN_KEY_CHECKS = 1;
