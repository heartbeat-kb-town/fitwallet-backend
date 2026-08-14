"""공공데이터 상권업종 분류를 fitwallet의 category_id로 옮긴다.

소상공인시장진흥공단 상가정보는 업종을 대/중/소 3단계로 준다. fitwallet의 category는 7개뿐이라
대부분은 중분류만 봐도 결정되지만, 한 중분류가 두 category로 갈리는 자리가 있어 소분류 예외를 둔다.

  음식 > 기타 간이   → 빵/도넛·떡/한과·아이스크림은 카페(1), 치킨·피자·버거는 푸드(4)
  소매 > 의약·화장품 → 약국은 병원(5), 화장품·의료기기는 쇼핑(3)
  소매 > 연료        → 주유소·가스충전소는 주유(6), 가정용 연료(연탄·등유)는 기타(7)

매핑이 맞는지는 이 파일을 직접 실행해 확인한다. 원본을 전부 훑어 category별 행수를 세고
EXPECTED와 대조한다 — 규칙을 고치면 이 숫자가 먼저 틀어진다.

    python3 scripts/perf-data/category_mapping.py
"""

CAFE = 1  # 카페/디저트
MART = 2  # 편의점/마트
SHOP = 3  # 쇼핑
FOOD = 4  # 푸드
CLINIC = 5  # 병원
FUEL = 6  # 주유
ETC = 7  # 기타

# 소분류 예외. 키는 (대분류, 중분류, 소분류) 전체다 — 소분류명만 쓰면 '그 외 기타 ...' 류가
# 여러 중분류에 중복으로 존재해 조용히 잘못 매칭된다.
BY_SUB = {
    ("음식", "기타 간이", "빵/도넛"): CAFE,
    ("음식", "기타 간이", "떡/한과"): CAFE,
    ("음식", "기타 간이", "아이스크림/빙수"): CAFE,
    ("소매", "의약·화장품 소매", "약국"): CLINIC,
    ("소매", "연료 소매", "주유소"): FUEL,
    ("소매", "연료 소매", "가스 충전소"): FUEL,
    ("소매", "연료 소매", "가정용 연료 소매업"): ETC,
}

# 중분류 매핑. 여기에 없는 중분류는 전부 ETC로 떨어진다 —
# 수리·개인 / 과학·기술 / 교육 / 예술·스포츠 / 시설관리·임대 / 부동산 / 숙박 7개 대분류가 그렇다.
BY_MID = {
    ("음식", "비알코올"): CAFE,
    ("음식", "한식"): FOOD,
    ("음식", "기타 간이"): FOOD,
    ("음식", "주점"): FOOD,
    ("음식", "중식"): FOOD,
    ("음식", "일식"): FOOD,
    ("음식", "서양식"): FOOD,
    ("음식", "구내식당·뷔페"): FOOD,
    ("음식", "동남아시아"): FOOD,
    ("음식", "기타 외국"): FOOD,
    ("소매", "종합 소매"): MART,
    ("소매", "식료품 소매"): MART,
    ("소매", "섬유·의복·신발 소매"): SHOP,
    ("소매", "의약·화장품 소매"): SHOP,
    ("소매", "오락용품 소매"): SHOP,
    ("소매", "가전·통신 소매"): SHOP,
    ("소매", "기타 상품 소매"): SHOP,
    ("소매", "기타 생활용품 소매"): SHOP,
    ("소매", "식물 소매"): SHOP,
    ("소매", "안경·정밀기기 소매"): SHOP,
    ("소매", "장식품 소매"): SHOP,
    ("소매", "애완동물·용품 소매"): SHOP,
    ("소매", "가구 소매"): SHOP,
    ("소매", "시계·귀금속 소매"): SHOP,
    ("소매", "중고 상품 소매"): SHOP,
    ("소매", "담배 소매"): SHOP,
    ("소매", "음료 소매"): SHOP,
    # 소매지만 개인 소비 결제가 아니라 기타로 보낸다 (자재·부품·이륜차).
    ("소매", "철물·건설자재 소매"): ETC,
    ("소매", "자동차 부품 소매"): ETC,
    ("소매", "모터사이클 소매"): ETC,
    ("소매", "연료 소매"): ETC,
    ("보건의료", "의원"): CLINIC,
    ("보건의료", "병원"): CLINIC,
    ("보건의료", "기타 보건"): CLINIC,
}

# 2026-08-14 다운로드분(202603) 전건 실측. 규칙을 고치면 이 숫자가 바뀐다.
EXPECTED = {
    CAFE: 158_190,
    MART: 210_746,
    SHOP: 365_638,
    FOOD: 669_638,
    CLINIC: 82_877,
    FUEL: 14_832,
    ETC: 1_223_397,
}
EXPECTED_TOTAL = 2_725_318

NAMES = {
    CAFE: "카페/디저트",
    MART: "편의점/마트",
    SHOP: "쇼핑",
    FOOD: "푸드",
    CLINIC: "병원",
    FUEL: "주유",
    ETC: "기타",
}


def to_category_id(top, mid, sub):
    """대/중/소 분류명을 category_id로 옮긴다. 어디에도 없으면 기타(7).

    ⚠️ strip()이 없으면 안 된다. 원본에 '비알코올 ' '법무관련 ' '장례식장 ' 세 값이
    뒤에 공백을 달고 들어온다. 특히 '비알코올 '이 빠지면 카페 115,722건이 통째로 기타가 된다.
    """
    top, mid, sub = top.strip(), mid.strip(), sub.strip()
    hit = BY_SUB.get((top, mid, sub))
    if hit is not None:
        return hit
    return BY_MID.get((top, mid), ETC)


def _main():
    import collections
    import csv
    import glob
    import os
    import sys

    raw_dir = os.path.join(
        os.environ.get("PERF_DATA_DIR", os.path.expanduser("~/fitwallet-perf-data")), "raw"
    )
    files = sorted(glob.glob(os.path.join(raw_dir, "*.csv")))
    if not files:
        sys.exit(f"원본 CSV가 없다: {raw_dir}")

    counts = collections.Counter()
    for path in files:
        with open(path, encoding="utf-8", newline="") as fh:
            for row in csv.DictReader(fh):
                counts[
                    to_category_id(
                        row["상권업종대분류명"], row["상권업종중분류명"], row["상권업종소분류명"]
                    )
                ] += 1

    total = sum(counts.values())
    print(f"{len(files)}개 파일 · {total:,}행\n")
    print(f"{'category':<14}{'실측':>12}{'기대':>12}{'차이':>10}")
    ok = total == EXPECTED_TOTAL
    for cid in sorted(NAMES):
        got, want = counts[cid], EXPECTED[cid]
        diff = got - want
        ok = ok and diff == 0
        print(f"{NAMES[cid]:<14}{got:>12,}{want:>12,}{diff:>+10,}")
    print(f"\n합계{'':<10}{total:>12,}{EXPECTED_TOTAL:>12,}{total - EXPECTED_TOTAL:>+10,}")
    print("\n" + ("일치" if ok else "불일치 — 매핑 규칙이 바뀌었거나 원본이 다르다"))
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    _main()
