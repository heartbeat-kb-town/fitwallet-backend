# scripts

시드 데이터를 만드는 **로컬 실행 전용** 스크립트. 애플리케이션 빌드·실행과는 무관하다.

Python 3 표준 라이브러리만 쓴다. 이 저장소에 Python 의존성 관리 체계가 없어서,
pandas 같은 외부 패키지를 쓰면 실행 환경이 사람마다 갈린다.

---

## `brand_alias.csv` — 브랜드·별칭 기준 파일 (정본)

`brand` / `brand_alias` 시드의 **정본**이다. 브랜드나 별칭을 고칠 때는 이 CSV를 고치고
아래 생성기를 다시 돌린다. `003-seed.sql`을 직접 손대지 않는다.

| 컬럼 | 설명 |
|---|---|
| `brand_id` | `brand` PK. **임의로 바꾸지 않는다** — `service_brand` 197행이 참조한다 |
| `brand_name` | 사람이 읽는 브랜드명. `brand.brand_name`이 된다 |
| `category_id` | 업종 6종 중 하나 (1 카페/디저트, 2 편의점/마트, 3 쇼핑, 4 푸드, 5 병원, 6 주유) |
| `alias` | 매칭 키. **정규화형으로 적는다** (아래) |
| `alias_type` | `OFFICIAL` / `KOREAN` / `ENGLISH` / `SHORT` / `LEGACY` |

브랜드 등록 요건과 별칭 규칙 전문은 [`docs/erd.md` §2.3](../docs/erd.md)에 있다.

### 정규화 규칙

> 소문자화 → 공백과 `.` `-` `_` `(` `)` `&` `,` `'` 제거. 나머지는 보존.

`메가MGC커피` → `메가mgc커피` · `S-OIL` → `soil` · `SSG.COM` → `ssgcom`

`gen_brand_seed.py`의 `normalize()`가 이 규칙의 실행 가능한 정본이다. 가맹점 적재
스크립트도 상호명에 같은 함수를 적용한 뒤 별칭과 대조한다.

---

## `gen_brand_seed.py` — 기준 파일 → 시드 SQL

```bash
python3 scripts/gen_brand_seed.py --check    # CSV 검증만 (CI·리뷰용)
python3 scripts/gen_brand_seed.py            # stdout 으로 INSERT 블록 출력
```

`--check`는 다음을 검사하고 위반이 있으면 종료코드 1로 끝난다.

- `alias`가 정규화형인가 / 2자 이상인가 / 전역 UNIQUE인가
- `alias_type`이 CHECK 값인가
- 브랜드마다 `OFFICIAL` 별칭이 정확히 1개이고 `brand_name`의 정규화형과 같은가
- 같은 `brand_id`가 행마다 다른 이름·업종으로 나오지 않는가

출력을 `docker/mysql/init/003-seed.sql`의 brand / brand_alias 블록에 덮어쓴다.
시드를 바꾼 뒤에는 볼륨을 비워야 반영된다 — init 스크립트는 빈 볼륨에서만 실행된다.

```bash
docker compose down -v && docker compose up -d
```
