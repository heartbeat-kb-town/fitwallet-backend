#!/usr/bin/env python3
"""brand_alias.csv -> 003-seed.sql 의 brand / brand_alias INSERT 블록 생성기.

기준 파일(brand_alias.csv)이 정본이고 시드 SQL은 그 산출물이다. 브랜드나 별칭을
고칠 때는 CSV를 고치고 이 스크립트를 다시 돌린다.

가맹점 적재 스크립트(#159)는 같은 CSV를 읽어 store -> brand 매칭을 수행한다.
"같은 기준 파일을 참조한다"가 이 구조의 목적이다.

Python 3 표준 라이브러리만 쓴다 (이 저장소에 Python 의존성 관리 체계가 없다).

    python3 scripts/gen_brand_seed.py                    # stdout 으로 SQL 출력
    python3 scripts/gen_brand_seed.py --check            # CSV 검증만 (종료코드로 판정)
"""

import argparse
import csv
import re
import sys
from collections import Counter
from pathlib import Path

CSV_PATH = Path(__file__).parent / "brand_alias.csv"

# 시드 전체가 쓰는 고정 타임스탬프. 기존 brand 행과 같은 값을 유지한다.
SEEDED_AT = "2026-07-27 12:27:52"

ALIAS_TYPES = {"OFFICIAL", "KOREAN", "ENGLISH", "SHORT", "LEGACY"}

# docs/erd.md §2.3 의 정규화 규칙과 같아야 한다. 여기가 그 규칙의 실행 가능한 정본이다.
STRIP_CHARS = re.compile(r"[\s.\-_()&,']")

MIN_ALIAS_LENGTH = 2


def normalize(name: str) -> str:
    """상호명/브랜드명을 매칭 키로 정규화한다 — 소문자화 후 공백·특수문자 제거."""
    return STRIP_CHARS.sub("", name.lower())


def load_rows(path: Path) -> list[dict]:
    with path.open(encoding="utf-8") as f:
        # '#' 로 시작하는 주석 줄은 건너뛴다. csv 모듈은 주석을 모른다.
        lines = [line for line in f if not line.lstrip().startswith("#")]
    return list(csv.DictReader(lines))


def validate(rows: list[dict]) -> list[str]:
    """CSV 가 §2.3 의 별칭 규칙을 지키는지 검사하고 위반 목록을 돌려준다."""
    errors = []

    # 브랜드 하나가 서로 다른 이름/업종으로 여러 번 나오면 시드가 비결정적이 된다.
    brands = {}
    for row in rows:
        brand_id = row["brand_id"]
        identity = (row["brand_name"], row["category_id"])
        if brands.setdefault(brand_id, identity) != identity:
            errors.append(
                f"brand_id={brand_id} 의 이름/업종이 행마다 다르다: "
                f"{brands[brand_id]} vs {identity}"
            )

    alias_counts = Counter(row["alias"] for row in rows)
    for alias, count in alias_counts.items():
        if count > 1:
            errors.append(f"alias '{alias}' 가 {count}번 나온다 (UNIQUE 위반)")

    for row in rows:
        alias, alias_type = row["alias"], row["alias_type"]
        if alias != normalize(alias):
            errors.append(f"alias '{alias}' 가 정규화형이 아니다 (기대: '{normalize(alias)}')")
        if len(alias) < MIN_ALIAS_LENGTH:
            errors.append(f"alias '{alias}' 가 {MIN_ALIAS_LENGTH}자 미만이다 (오탐 방지)")
        if alias_type not in ALIAS_TYPES:
            errors.append(f"alias '{alias}' 의 alias_type '{alias_type}' 이 CHECK 값이 아니다")

    # 모든 브랜드는 brand_name 의 정규화형을 OFFICIAL 별칭으로 정확히 하나 갖는다.
    official = {}
    for row in rows:
        if row["alias_type"] == "OFFICIAL":
            official.setdefault(row["brand_id"], []).append(row["alias"])
    for brand_id, (brand_name, _) in brands.items():
        aliases = official.get(brand_id, [])
        if len(aliases) != 1:
            errors.append(f"brand_id={brand_id}({brand_name}) 의 OFFICIAL 별칭이 {len(aliases)}개다 (1개여야 한다)")
        elif aliases[0] != normalize(brand_name):
            errors.append(
                f"brand_id={brand_id} 의 OFFICIAL 별칭 '{aliases[0]}' 이 "
                f"brand_name 정규화형 '{normalize(brand_name)}' 과 다르다"
            )

    return errors


def emit_sql(rows: list[dict]) -> str:
    """003-seed.sql 에 그대로 붙일 수 있는 INSERT 블록을 만든다.

    기존 시드 스타일을 따른다 — 행당 INSERT 하나, 컬럼 전체 명시.
    """
    out = []

    out.append("-- ---------------------------------------------------------")
    out.append("-- brand — 브랜드 (scripts/brand_alias.csv 에서 생성)")
    out.append("-- ---------------------------------------------------------")
    seen = set()
    for row in rows:
        brand_id = row["brand_id"]
        if brand_id in seen:
            continue
        seen.add(brand_id)
        out.append(
            "INSERT INTO `brand` (`brand_id`, `brand_name`, `category_id`, `brand_image_url`, "
            "`created_at`, `updated_at`) VALUES "
            f"({brand_id},'{row['brand_name']}',{row['category_id']},NULL,"
            f"'{SEEDED_AT}','{SEEDED_AT}');"
        )

    out.append("")
    out.append("-- ---------------------------------------------------------")
    out.append("-- brand_alias — 브랜드 별칭 (scripts/brand_alias.csv 에서 생성)")
    out.append("-- ---------------------------------------------------------")
    for alias_id, row in enumerate(rows, start=1):
        out.append(
            "INSERT INTO `brand_alias` (`brand_alias_id`, `brand_id`, `alias`, `alias_type`, "
            "`created_at`, `updated_at`) VALUES "
            f"({alias_id},{row['brand_id']},'{row['alias']}','{row['alias_type']}',"
            f"'{SEEDED_AT}','{SEEDED_AT}');"
        )

    return "\n".join(out)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="SQL 을 찍지 않고 CSV 검증만 한다")
    args = parser.parse_args()

    rows = load_rows(CSV_PATH)
    errors = validate(rows)

    brand_count = len({row["brand_id"] for row in rows})
    print(f"brand {brand_count}건 / alias {len(rows)}건", file=sys.stderr)

    if errors:
        print(f"\n검증 실패 {len(errors)}건:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1

    print("검증 통과", file=sys.stderr)
    if not args.check:
        print(emit_sql(rows))
    return 0


if __name__ == "__main__":
    sys.exit(main())
