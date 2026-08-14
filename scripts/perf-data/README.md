# 성능 테스트용 데이터 적재

부하 테스트의 기준선이 될 데이터를 만들고 적재한다. 공공데이터(소상공인 상가정보)를 `store`
스키마로 옮기고, 회원·보유카드·결제내역·검색이력은 생성한다.

**판단 근거는 노션 「📦 데이터 적재 설계 (1단계)」에 있다.** 이 문서는 돌리는 법과 구현 함정만 다룬다.

| 테이블 | 행수 | 출처 |
|---|---:|---|
| `store` | 244 + 2,725,318 | 공공데이터 (상호명·좌표·주소는 실데이터) |
| `users` | 100,000 | 생성 |
| `user_card` | 300,000 | 생성 |
| `payment_transaction` | 5,400,000 | 생성 |
| `search_history` | 800,000 | 생성 |

---

## 실행

```bash
# 0. 원본 CSV를 ~/fitwallet-perf-data/raw/ 에 둔다 (data.go.kr 상가(상권)정보, 17개 시도)
#    다른 곳에 두려면 PERF_DATA_DIR로 덮어쓴다.

docker compose -f docker-compose.perf.yml up -d    # 1. 성능 DB (3308)
./gradlew appStart -Denv=perf                      # 2. 스키마 적용 (V1~V9)
./gradlew appStop

python3 scripts/perf-data/build_store.py           # 3. 약 30초
python3 scripts/perf-data/build_synthetic.py       # 4. 약 4분

scripts/perf-data/load.sh                          # 5. 약 2분

MYSQL_PWD=fitwallet1234 mysql --default-character-set=utf8mb4 \
  -h127.0.0.1 -P3308 -ufitwallet fitwallet -t < scripts/perf-data/verify.sql   # 6. 약 2분
```

`verify.sql`의 **판정 컬럼이 전부 `OK`** 여야 한다.

### 환경변수

| 이름 | 기본값 | 쓰임 |
|---|---|---|
| `PERF_DATA_DIR` | `~/fitwallet-perf-data` | 원본(`raw/`)과 생성물(`out/`) 위치 |
| `PERF_DB_HOST` / `PERF_DB_PORT` | `127.0.0.1` / `3308` | 성능 DB |
| `PERF_DB_USER` / `PERF_DB_PASSWORD` / `PERF_DB_NAME` | `fitwallet` / `fitwallet1234` / `fitwallet` | 접속 정보 |

원본 1.4GB와 생성물 약 4GB는 **저장소 밖**에 둔다. 유출 방지가 아니라 파일 보존 때문이다 —
저장소 안에 두면 `git clean -xfd`가 ignore된 파일까지 지워 원본을 다시 받아야 한다.

### 되돌리기

두 번 적재하면 PK 중복으로 실패한다. 다시 넣으려면 `--reset`을 준다.

```bash
scripts/perf-data/load.sh --reset                    # 적재분만 비우고 다시 넣는다
docker compose -f docker-compose.perf.yml down -v    # 볼륨까지 지우고 처음부터
```

`--reset`은 `store`를 `TRUNCATE`하지 않는다 — V2 참조데이터 244행은 Flyway가 넣은 것이라
`DELETE FROM store WHERE store_id > 244`로 적재분만 지운다.

> ⚠️ 손으로 `TRUNCATE`하면 `user_card`에서 막힌다. `payment_session`이 `user_card`를 FK 참조하고
> 있어 **참조 행이 0건이어도 제약 자체로 거부된다**(`ERROR 1701`). `--reset`은
> `FOREIGN_KEY_CHECKS`를 끄고 돈다.

---

## 파일

| 파일 | 하는 일 |
|---|---|
| `category_mapping.py` | 공공데이터 업종 3단계 → `category_id`. **직접 실행하면 매핑을 검증한다** |
| `normalize.py` | 상호명 정규화 + 브랜드 접두사 최장 일치 매처 |
| `build_store.py` | raw CSV → `out/store.tsv` |
| `build_synthetic.py` | 회원·카드·결제·검색 생성 |
| `load.sh` | `LOAD DATA LOCAL INFILE` |
| `verify.sql` | 행수·분포·정합성 검증 14블록 |

`build_synthetic.py`는 `build_store.py`에서 `query()`·`COLUMNS` 등을 가져다 쓴다. 두 파일은
같은 디렉터리에 있어야 한다.

---

## 구현 함정 — 전부 실제로 밟은 것들

### 1. 업종 분류명에 뒤 공백이 있다

원본에 `'비알코올 '` `'법무관련 '` `'장례식장 '` 세 값이 뒤에 공백을 달고 들어온다.
`strip()`이 없으면 **카페 115,722건이 통째로 `기타`로 빠진다.**

### 2. 브랜드 매칭은 접두사 최장 일치여야 한다

`지점명` 컬럼은 채워질 때도(`CU` + `명일점`) 비어 있을 때도(`CU수원운동장점`) 있다.
어느 쪽이든 브랜드가 맨 앞에 오므로 접두사로 찾는다.

짧은 별칭부터 훑으면 `이마트24역삼점`이 `이마트`로 잘못 잡힌다. 같은 함정이
`롯데마트`/`롯데몰`/`롯데VIC마켓`, `컬리`/`마켓컬리`에도 있다.

### 3. 이름만 스치는 행은 기각해야 한다

`씨유펫`(CU + 펫샵), `이마트원스탑주얼리` 같은 행이 브랜드로 잡힌다. 브랜드별 **최빈 업종**과
다른 행은 브랜드를 떼어낸다. 실측 기각률 2.0% (66,574 후보 중 1,349건).

### 4. `brand_id`가 있으면 category는 브랜드를 따른다

V1 불변식이다. 올리브영은 업종 매핑이 쇼핑(3)인데 `brand.category_id`는 편의점/마트(2)라,
브랜드 쪽으로 맞춘다. 실측 1,618건이 이렇게 옮겨간다. `verify.sql` §4가 위반 0건을 확인한다.

### 5. CSV를 콤마로 자르면 안 된다

따옴표 안에 쉼표가 든 필드가 있어 컬럼이 밀린다(헤더에서 38번이던 경도가 데이터 행에서 37번으로
나온다). `csv` 모듈로 읽는다.

### 6. `mysql` CLI에 `--default-character-set=utf8mb4`가 없으면 한글이 `???`가 된다

별칭이 전부 물음표가 되어 매칭이 통째로 실패한다. 조용히 실패하지 않도록 `BrandMatcher.add()`가
같은 정규화형이 두 브랜드를 가리키면 예외를 던진다 — 이 사고를 실제로 잡아냈다.

### 7. `final_amount`는 네이티브가 아니라 원화를 뺀다

`discount_amount`는 네이티브다(CASHBACK=원, ACCUMULATE=포인트 개수). 하지만
`final_amount = amount - (discount × krw_per_point)`다 (`DefaultPaymentService.java:276-278`).

> ⚠️ `point_currency` 5행이 **전부 `krw_per_point = 1.0000`**이라 틀리게 구현해도 숫자가 같다.
> 아무 검증도 깨지지 않으므로 환산을 명시적으로 구현한다.

### 8. `docker-compose.perf.yml`의 `name:`을 지우면 개발 DB가 날아간다

compose 프로젝트 이름은 기본이 디렉터리명이다. perf 파일이 `docker-compose.yml`과 같은
프로젝트에 들어가면 compose가 **같은 서비스의 새 설정으로 보고 개발 DB 컨테이너를 교체한다.**
실제로 겪었다 — `docker compose -f docker-compose.perf.yml up -d` 한 번에 `fitwallet-mysql`이
제거됐다(볼륨이 남아 데이터는 보존됐다). 최상단 `name: fitwallet-perf`가 이걸 막는다.

### 9. 스키마에 컬럼이 끼어들어도 밀리지 않게 컬럼 목록을 명시한다

V10이 `transaction_status`를 **`AFTER paid_at`**, 즉 테이블 중간에 넣었다. `load.sh`가 컬럼을
이름으로 넘기기 때문에 아무것도 밀리지 않았다. 위치 기반이었다면 `is_used_app`의 `1`/`0`이
`transaction_status`로 들어가 CHECK 제약에 걸렸을 것이다.

컬럼 목록은 `build_store.py`의 `COLUMNS`가 정본이고, 생성 스크립트가 `.columns` 파일로 내보낸다.
**손으로 두 군데 적지 않는다.**

> ⚠️ V10을 540만 행에 적용하면 **71초**가 걸린다(INSTANT DDL이 아니고 CHECK 제약을 검증한다).
> 스테이징에서는 **마이그레이션을 끝낸 뒤 적재한다** — 순서가 뒤집히면 그만큼 더 걸린다.

### 10. `build.gradle`이 Gretty 프로파일을 하드코딩하고 있었다 (수정함)

`jvmArgs = ['-Denv=local', ...]`이라 `./gradlew appRun -Denv=perf`가 조용히 무시되고
**3307 개발 DB에 붙어 `seed-local`까지 적용했다.** `test` 태스크와 같은 방식
(`System.getProperty('env', 'local')`)으로 맞췄다. 기본값이 `local`이라 팀원 동작은 그대로다.

---

## 왜 이렇게 만들었나

**Python 표준 라이브러리만 쓴다.** pandas는 272만·540만 행에서 메모리만 먹고 이득이 없다.
전 스크립트를 스트리밍으로 써서 메모리를 상수로 유지한다. DB 접근은 `mysql` CLI를
`subprocess`로 부른다 — 드라이버를 설치시키지 않으려는 것이다.

**혜택 적용값을 정합성 있게 채운다.** `applied_benefit_service_id`/`applied_tier_id`를 NULL로
두면 report 도메인 매퍼 3개가 0행을 반환하고, 무작위로 채우면 `CardMapper.xml:421`의
`GROUP BY` 그룹 수가 카드당 3개에서 165개로 퍼져 집계 부하가 왜곡된다. 그래서 '이 카드로 이
가맹점에서 결제했을 때 실제로 적용 가능한 혜택'만 고른다. 조인 대상 테이블이 다 합쳐 1,000행대라
메모리에 올려도 무해하다. `verify.sql` §8·§9가 이걸 검증한다.

**거래는 전부 `transaction_status = 'APPROVED'`다.** 아직 아무도 이 컬럼을 읽지 않는다 —
`BenefitReportMapper`·`CardBenefitMapper`의 집계가 상태를 거르지 않으므로, 지금 `CANCELED`를
섞으면 그 금액이 리포트 합계에 그대로 잡혀 수치가 틀린다. 이슈 #226도 "집계 SQL 반영 전에는
실제 거래를 `CANCELED`로 변경하지 않는다"고 못박았다.

> **후속 작업이 집계 SQL에 상태 필터를 넣을 때 함께 고칠 것** —
> `build_synthetic.py`의 `TRANSACTION_STATUS`에서 비율을 나누고, `verify.sql` §7-1의 단언을
> 바꾼다. 재생성은 6분이면 끝나므로 미리 만들어 두지 않는다.

**재현성.** 같은 시드(`--seed`)와 같은 기준일(`--reference-date`)이면 출력이 바이트 단위로 같다.
`paid_at`이 기준일 상대라 날짜가 바뀌면 결과도 바뀐다 — 고정하려면 `--reference-date`를 넘긴다.

**개발 DB와 성능 DB는 구조로 분리한다.** 프로젝트·컨테이너·볼륨·포트가 전부 다르다.
`./gradlew test`는 3307만 보므로 매퍼 통합테스트가 성능 데이터의 영향을 받지 않는다.

---

## 실측값 (2026-08-14, 시드 20260814)

```
업종 매핑     카페 158,190 / 편의점마트 210,746 / 쇼핑 365,638 / 푸드 669,638
             병원 82,877 / 주유 14,832 / 기타 1,223,397 (44.9%)
브랜드 매칭   65,225건 (2.39%)   ← 후보 66,574 중 1,349건 기각
거래 분포     브랜드 45.0% / 일반 cat1~6 45.0% / 기타 10.0%
혜택 적용     54.7% (2,954,081건)  ·  better_user_card 30.0% (1,619,560건)
카드당 그룹수 평균 2.03 (무작위였다면 165에 가까움)

소요 시간     build_store 30초 · build_synthetic 4분 · load 2분 · verify 2분
```
