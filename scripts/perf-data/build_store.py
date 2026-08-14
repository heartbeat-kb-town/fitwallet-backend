"""공공데이터 상가정보를 store 테이블 적재용 TSV로 변환한다.

    python3 scripts/perf-data/build_store.py

입력  $PERF_DATA_DIR/raw/*.csv   (소상공인시장진흥공단 상가정보, UTF-8)
출력  $PERF_DATA_DIR/out/store.tsv
      $PERF_DATA_DIR/out/store.columns    load.sh가 LOAD DATA에 넘길 컬럼 목록
      $PERF_DATA_DIR/out/keywords.tsv     build_synthetic.py가 쓰는 검색 키워드 풀

브랜드 별칭은 DB의 brand·brand_alias에서 읽는다. 파이썬 드라이버를 쓰지 않으려고
`mysql` CLI를 subprocess로 부른다 (표준 라이브러리만 쓰는 제약 때문이다).

**한 번만 훑는다.** 브랜드가 붙는 행이 전체의 2.4%뿐이라, 그 후보만 메모리에 모아 두고
나머지는 즉시 기록한다. 후보가 다 모여야 브랜드별 최빈 업종을 알 수 있고(오탐 필터),
그건 전건을 본 뒤에야 확정되기 때문이다.
"""

import collections
import csv
import glob
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from category_mapping import EXPECTED, EXPECTED_TOTAL, NAMES, to_category_id  # noqa: E402
from normalize import BrandMatcher, build_store_name, normalize, strip_branch_suffix  # noqa: E402

# store_id 1~244는 V2 참조데이터(카카오 실데이터)이고 V901 데모가 FK로 참조한다. 그 뒤에 붙인다.
FIRST_STORE_ID = 245

STORE_NAME_MAX = 100  # store.store_name VARCHAR(100)
ADDRESS_MAX = 255  # store.address VARCHAR(255)

NULL = "\\N"  # LOAD DATA가 NULL로 읽는 표기

# TSV 컬럼 순서의 정본. load.sh가 이걸 그대로 LOAD DATA의 컬럼 목록으로 쓴다.
# 손으로 두 군데 적으면 조용히 어긋나 값이 엉뚱한 컬럼에 들어가므로, 쓰는 쪽이 내보낸다.
# created_at·updated_at은 DB DEFAULT가 채우므로 넣지 않는다 (AGENTS.md §10).
COLUMNS = {
    "store": (
        "store_id, category_id, brand_id, store_name, store_rank, "
        "latitude, longitude, address, kakao_place_id, store_qr_token"
    ),
    "users": (
        "user_id, login_id, name, phone, password_hash, provider_user_id, payment_pin_hash, "
        "is_location_agreed, is_marketing_agreed, pin_auth_id, auth_expires_at, "
        "auth_is_used, pin_fail_count"
    ),
    "user_card": (
        "user_card_id, user_id, card_product_id, first4, last4, expiry_date, display_order, "
        "bank_name, balance, credit_limit, scheduled_payment_amount, is_deleted"
    ),
    "payment_transaction": (
        "payment_transaction_id, user_card_id, store_id, payment_session_id, amount, "
        "discount_amount, final_amount, paid_at, is_used_app, is_eligible, "
        "applied_benefit_service_id, applied_tier_id, better_user_card_id, "
        "alternative_discount_amount, missed_amount"
    ),
    "search_history": "search_history_id, user_id, keyword, searched_at",
}


def write_columns(out_dir, table):
    """LOAD DATA에 넘길 컬럼 목록을 TSV 옆에 남긴다."""
    path = os.path.join(out_dir, f"{table}.columns")
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(COLUMNS[table] + "\n")


def data_dir():
    return os.environ.get("PERF_DATA_DIR", os.path.expanduser("~/fitwallet-perf-data"))


def query(sql):
    """mysql CLI로 조회해 행 리스트를 돌려준다.

    -N(헤더 없음) -B(탭 구분)로 뽑는다. --default-character-set이 없으면 한글이 '???'로
    깨져 나온다 — 별칭이 전부 물음표가 되어 매칭이 통째로 실패한다.
    """
    cmd = [
        "mysql",
        "--default-character-set=utf8mb4",
        f"--host={os.environ.get('PERF_DB_HOST', '127.0.0.1')}",
        f"--port={os.environ.get('PERF_DB_PORT', '3308')}",
        f"--user={os.environ.get('PERF_DB_USER', 'fitwallet')}",
        "-N",
        "-B",
        "-e",
        sql,
        os.environ.get("PERF_DB_NAME", "fitwallet"),
    ]
    # 비밀번호는 MYSQL_PWD로 넘긴다 — --password는 경고를 stderr에 뱉고 ps에도 남는다.
    env = dict(os.environ, MYSQL_PWD=os.environ.get("PERF_DB_PASSWORD", "fitwallet1234"))
    done = subprocess.run(cmd, capture_output=True, text=True, env=env)
    if done.returncode != 0:
        sys.exit(f"DB 조회 실패:\n{done.stderr.strip()}")
    return [line.split("\t") for line in done.stdout.splitlines() if line]


def load_brands():
    """brand_name과 brand_alias를 하나의 매처로 합치고, 브랜드별 category_id를 같이 돌려준다."""
    matcher = BrandMatcher()
    for brand_id, name in query("SELECT brand_id, brand_name FROM brand"):
        matcher.add(name, int(brand_id))
    for brand_id, alias in query("SELECT brand_id, alias FROM brand_alias"):
        matcher.add(alias, int(brand_id))
    brand_category = {
        int(brand_id): int(category_id)
        for brand_id, category_id in query("SELECT brand_id, category_id FROM brand")
    }
    return matcher, brand_category


def clean(text, limit):
    """탭·개행을 지우고 길이를 자른다. TSV라 구분자가 값 안에 있으면 컬럼이 밀린다."""
    text = (text or "").strip().replace("\t", " ").replace("\r", " ").replace("\n", " ")
    return text[:limit]


def coord(text, places=8):
    """위경도를 DECIMAL 자릿수에 맞춘다. 비어 있거나 숫자가 아니면 NULL."""
    text = (text or "").strip()
    if not text:
        return NULL
    try:
        return f"{round(float(text), places):.{places}f}"
    except ValueError:
        return NULL


def row_to_fields(record):
    """원본 한 줄에서 store 컬럼으로 쓸 값만 뽑는다."""
    return (
        clean(build_store_name(record["상호명"], record["지점명"]), STORE_NAME_MAX),
        coord(record["위도"]),
        coord(record["경도"]),
        clean(record["도로명주소"], ADDRESS_MAX),
    )


def write_row(out, store_id, category_id, brand_id, fields):
    name, lat, lon, address = fields
    out.write(
        "\t".join(
            (
                str(store_id),
                str(category_id),
                NULL if brand_id is None else str(brand_id),
                name,
                NULL,  # store_rank — 기존 244행도 전부 NULL이라 그대로 둔다
                lat,
                lon,
                address or NULL,
                NULL,  # kakao_place_id
                NULL,  # store_qr_token
            )
        )
        + "\n"
    )


def main():
    raw_dir = os.path.join(data_dir(), "raw")
    out_dir = os.path.join(data_dir(), "out")
    os.makedirs(out_dir, exist_ok=True)

    files = sorted(glob.glob(os.path.join(raw_dir, "*.csv")))
    if not files:
        sys.exit(f"원본 CSV가 없다: {raw_dir}")

    matcher, brand_category = load_brands()
    print(f"별칭 {len(matcher)}개 · 브랜드 {len(brand_category)}개 로드")

    out_path = os.path.join(out_dir, "store.tsv")
    category_counts = collections.Counter()
    # (brand_id, 업종매핑 category) 조합을 세어 브랜드별 최빈 업종을 찾는다.
    candidate_mix = collections.Counter()
    # brand.category_id 우선 규칙으로 옮겨간 행수. 업종 매핑 결과 자체는 건드리지 않는다.
    override_shift = collections.Counter()
    candidates = []
    # 소분류명(247종)을 검색 키워드 풀로 쓴다. '치킨' '약국' '주유소'처럼 사람이 실제로
    # 검색하는 말이고, 지어내지 않아도 데이터에서 그대로 나온다. build_synthetic.py가 읽는다.
    keywords = set()
    skipped_no_name = 0
    total = 0
    store_id = FIRST_STORE_ID

    with open(out_path, "w", encoding="utf-8", newline="") as out:
        for path in files:
            with open(path, encoding="utf-8", newline="") as fh:
                for record in csv.DictReader(fh):
                    total += 1
                    category_id = to_category_id(
                        record["상권업종대분류명"],
                        record["상권업종중분류명"],
                        record["상권업종소분류명"],
                    )
                    category_counts[category_id] += 1
                    keywords.add(record["상권업종소분류명"].strip())

                    fields = row_to_fields(record)
                    if not fields[0]:
                        skipped_no_name += 1
                        continue

                    brand_id = matcher.match(strip_branch_suffix(normalize(record["상호명"])))
                    if brand_id is None:
                        write_row(out, store_id, category_id, None, fields)
                        store_id += 1
                    else:
                        candidate_mix[(brand_id, category_id)] += 1
                        candidates.append((brand_id, category_id, fields))

        # 브랜드별 최빈 업종. '씨유펫'(CU+펫샵)처럼 이름만 스치는 행을 걸러내는 기준이다.
        modal = {}
        for (brand_id, category_id), count in candidate_mix.items():
            best = modal.get(brand_id)
            if best is None or (count, -category_id) > best[0]:
                modal[brand_id] = ((count, -category_id), category_id)
        modal = {brand_id: value[1] for brand_id, value in modal.items()}

        matched = rejected = overridden = 0
        for brand_id, category_id, fields in candidates:
            if category_id != modal[brand_id]:
                # 오탐 — 브랜드를 떼고 업종 매핑 결과만 남긴다.
                write_row(out, store_id, category_id, None, fields)
                rejected += 1
            else:
                # V1 불변식: brand_id가 있으면 store.category_id == brand.category_id 여야 한다.
                # 올리브영은 업종 매핑이 쇼핑(3)인데 brand.category_id는 편의점/마트(2)라
                # 여기서 브랜드 쪽으로 맞춘다.
                final_category = brand_category[brand_id]
                if final_category != category_id:
                    overridden += 1
                    override_shift[category_id] -= 1
                    override_shift[final_category] += 1
                write_row(out, store_id, final_category, brand_id, fields)
                matched += 1
            store_id += 1

    written = store_id - FIRST_STORE_ID
    print(f"\n원본 {total:,}행 → 기록 {written:,}행 (상호명 없음 {skipped_no_name:,}행 제외)")
    print(f"store_id {FIRST_STORE_ID:,} ~ {store_id - 1:,}\n")

    # 업종 매핑 자체가 맞는지(EXPECTED와 일치) 먼저 보고, 브랜드 덮어쓰기 효과는 따로 본다.
    # 둘을 섞으면 정상 동작인 덮어쓰기가 매핑 오류처럼 보인다.
    print(f"{'category':<14}{'업종매핑':>12}{'기대':>12}{'차이':>8}{'덮어씀':>9}{'최종':>12}")
    mapping_ok = True
    for cid in sorted(NAMES):
        got, want, shift = category_counts[cid], EXPECTED[cid], override_shift[cid]
        mapping_ok = mapping_ok and got == want
        print(
            f"{NAMES[cid]:<14}{got:>12,}{want:>12,}{got - want:>+8,}"
            f"{shift:>+9,}{got + shift:>12,}"
        )
    print(f"\n원본 합계{'':<6}{total:>12,}{EXPECTED_TOTAL:>12,}{total - EXPECTED_TOTAL:>+8,}")
    print("업종 매핑: " + ("기대치와 일치" if mapping_ok else "⚠️ 불일치 — 규칙이 바뀌었다"))

    candidate_total = matched + rejected
    print(f"\n브랜드 후보 {candidate_total:,}건")
    print(f"  매칭   {matched:,} ({matched / total * 100:.2f}%)")
    print(f"  기각   {rejected:,} ({rejected / candidate_total * 100:.1f}% — 최빈 업종 불일치)")
    print(f"  category 덮어씀 {overridden:,}건 (brand.category_id 우선, V1 불변식)")

    write_columns(out_dir, "store")

    keyword_path = os.path.join(out_dir, "keywords.tsv")
    with open(keyword_path, "w", encoding="utf-8") as fh:
        for word in sorted(keywords):
            if word:
                fh.write(word + "\n")

    print(f"\n→ {out_path}")
    print(f"→ {keyword_path} ({len(keywords)}개 소분류명 — 검색 키워드 풀)")


if __name__ == "__main__":
    main()
