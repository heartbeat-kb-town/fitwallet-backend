"""공공데이터에 없는 것을 만든다 — 회원·보유카드·결제내역·검색이력.

    python3 scripts/perf-data/build_synthetic.py

입력  $PERF_DATA_DIR/out/store.tsv       build_store.py 산출물
      $PERF_DATA_DIR/out/keywords.tsv    검색 키워드 풀
      DB의 benefit_service · service_brand · service_category · benefit_tier · point_currency

출력  $PERF_DATA_DIR/out/{users,user_card,payment_transaction,search_history}.tsv

**혜택 적용값을 정합성 있게 채운다.** applied_benefit_service_id / applied_tier_id를 NULL로 두면
report 도메인 매퍼 3개가 0행을 반환하고, 무작위로 채우면 CardMapper의 GROUP BY 그룹 수가
카드당 3개에서 165개로 퍼져 집계 부하가 왜곡된다. 그래서 '이 카드로 이 가맹점에서 결제했을 때
실제로 적용 가능한 혜택'만 고른다. 조인 대상 테이블이 다 합쳐 1,000행대라 메모리에 올려도 무해하다.

**재현성** — 같은 시드와 같은 기준일이면 출력이 바이트 단위로 같다. paid_at이 기준일 상대라
날짜가 바뀌면 결과도 바뀐다. 고정하려면 --reference-date를 넘긴다.
"""

import argparse
import array
import collections
import datetime
import math
import os
import random
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from build_store import NULL, data_dir, query, write_columns  # noqa: E402

SEED = 20260814

TARGET_USERS = 50_000
TARGET_CARDS = 195_000  # 1인 평균 3.9장
ACTIVE_USERS = 30_000  # 가입자의 60%
# 활성 유저 1명의 연간 결제 건수. 상수가 아니라 로그정규 분포로 뽑는다 — 아래 참고.
TX_MEAN_PER_ACTIVE = 900  # 월 75건 × 12개월
TX_SIGMA = 0.55
TX_MU = math.log(TX_MEAN_PER_ACTIVE) - TX_SIGMA**2 / 2
TX_MIN, TX_MAX = 30, 12_000
TARGET_TX = ACTIVE_USERS * TX_MEAN_PER_ACTIVE  # 약 27,000,000 (분포라 정확히 일치하진 않는다)
KEYWORDS_PER_USER = 16

# 검색어 최소 길이. DefaultStoreService.KEYWORD_MIN_LENGTH와 맞춘다 —
# ngram_token_size = 2라 1글자는 색인되지 않아 서비스가 거부한다.
KEYWORD_MIN_LENGTH = 2

# 거래를 store 세 구간에 나누는 비율. 브랜드 45%는 benefit_service의 스코프 구성비
# (BRAND 75 : INDUSTRY 90)를 반영한 값이며 실제 결제 빈도의 추정치가 아니다.
BRAND_SHARE = 0.45
PLAIN_SHARE = 0.45  # 브랜드 없는 category 1~6
# 나머지 10%가 기타(7). 기타는 혜택이 0건이라 판정이 빈손으로 끝나는 경로를 재현한다.

# 사용자가 검색어를 치는 업종 분포. **공개 통계가 아니라 우리가 정한 가정치다** —
# 근거와 대가는 docs/superpowers/specs/2026-08-22-k6-scenario-realism-design.md §2에 있다.
#
# ⚠️ 같은 벡터가 scripts/perf-k6/extract-scenarios.py에도 있다. 검색어 분포(DB)와 카테고리
#    칩 분포(CSV)가 같은 벡터를 써야 한다는 것이 설계 전제이므로 **둘은 함께 고친다.**
CATEGORY_WEIGHTS = {
    1: 0.26,  # 카페/디저트
    4: 0.26,  # 푸드
    2: 0.20,  # 편의점/마트
    3: 0.16,  # 쇼핑
    6: 0.08,  # 주유
    5: 0.04,  # 병원
}
assert abs(sum(CATEGORY_WEIGHTS.values()) - 1.0) < 1e-9, "CATEGORY_WEIGHTS의 합이 1이 아니다"

BETTER_SHARE = 0.30  # better_user_card_id를 채우는 비율
APP_SHARE = 0.45  # is_used_app = 1 비율 (데모 실측 175/391)

# 생성 거래는 전부 승인 건이다. CANCELED를 섞지 않는 이유는 아직 아무도 이 컬럼을 읽지 않아서다 —
# BenefitReportMapper·CardBenefitMapper의 집계가 transaction_status를 거르지 않으므로,
# 지금 취소 건을 넣으면 그 금액이 리포트 합계에 그대로 잡혀 수치가 틀린다.
# 이슈 #226도 "집계 SQL 반영 전에는 실제 거래를 CANCELED로 변경하지 않는다"고 못박았다.
#
# 후속 작업(집계 SQL이 상태를 거르게 되는 시점)에서 취소 건이 필요해지면 여기서 비율을 나눈다.
# 재생성은 6분이면 끝난다 — 미리 만들어 두는 것보다 그때 만드는 편이 안전하다.
TRANSACTION_STATUS = "APPROVED"

# (장수, 가중치%). 평균 약 3.97장 — 발급 장수 1억 1천만 / 성인 인구 기준 1인당 3.9장에 맞췄다.
# 12장까지 꼬리를 둔 것이 핵심이다. 6장에서 자르면 '카드가 많은 유저'가 모집단에 존재하지
# 않게 되어, benefit/expected의 카드별 N+1이 상한을 못 드러낸다.
CARDS_PER_USER = (
    (1, 12), (2, 16), (3, 19), (4, 17), (5, 13), (6, 10),
    (7, 6), (8, 4), (9, 1.5), (10, 1), (11, 0.3), (12, 0.2),
)

# category별 결제금액 중앙값(원). 실측이 아니라 상식에 기반한 판단이다.
# 데모 실측(min 2,500 / avg 22,039 / max 90,000)을 앵커로 삼았다.
MEDIAN_AMOUNT = {1: 5_000, 2: 12_000, 3: 45_000, 4: 25_000, 5: 30_000, 6: 60_000, 7: 20_000}
AMOUNT_SIGMA = 0.6
AMOUNT_MIN, AMOUNT_MAX = 1_000, 1_000_000

# V900에 이미 커밋돼 있는 로컬 전용 데모 해시. 10만 번 BCrypt를 돌리면 약 3시간이라 재사용한다.
PASSWORD_HASH = "$2a$10$fVOyYs72w2Bos1Yqg9kAzO5R7muqmDB65Q.ZnXFRIpnZ2LWyj8Fou"
PIN_HASH = "$2a$10$VFxQaZFMVYRlbzkFiDmDuuMyKrT58iJNqNPlMzchKkOiMh4y7DlGq"

SURNAMES = "김이박최정강조윤장임한오서신권황안송류전홍고문양손배백허남심노정"
GIVEN_1 = "민서지예준우현도하유주은태윤성진영수정혁규연희"
GIVEN_2 = "준호아연우진서현민율찬빈은솔결영수정"
BANKS = ("국민", "신한", "우리", "하나", "농협", "기업", "카카오뱅크", "토스뱅크")


def rows_to_pairs(rows):
    return [tuple(int(v) for v in row) for row in rows]


class StoreIndex:
    """store.tsv를 세 구간으로 나눠 들고 있는다.

    272만 건을 파이썬 리스트로 들면 수백 MB가 되므로 array 모듈로 담는다. store_id는 int32,
    category는 1바이트, brand_id는 int16이면 충분하다 — 합쳐 20MB 남짓이다.
    """

    def __init__(self):
        self.brand_store = array.array("i")
        self.brand_of = array.array("h")
        self.brand_category = array.array("B")
        self.plain_store = array.array("i")
        self.plain_category = array.array("B")
        self.etc_store = array.array("i")

    @classmethod
    def load(cls, path):
        index = cls()
        with open(path, encoding="utf-8") as fh:
            for line in fh:
                store_id, category_id, brand_id, _ = line.split("\t", 3)
                category_id = int(category_id)
                if brand_id != NULL:
                    index.brand_store.append(int(store_id))
                    index.brand_of.append(int(brand_id))
                    index.brand_category.append(category_id)
                elif category_id == 7:
                    index.etc_store.append(int(store_id))
                else:
                    index.plain_store.append(int(store_id))
                    index.plain_category.append(category_id)
        return index

    def summary(self):
        return (
            f"브랜드 보유 {len(self.brand_store):,} · "
            f"브랜드 없음(cat 1~6) {len(self.plain_store):,} · "
            f"기타 {len(self.etc_store):,}"
        )


class BenefitIndex:
    """'이 카드상품으로 이 가맹점에서 결제하면 어떤 혜택이 붙는가'를 미리 계산해 둔다."""

    def __init__(self):
        self.by_brand = collections.defaultdict(list)  # (card_product_id, brand_id) -> [service_id]
        self.by_category = collections.defaultdict(list)  # (card_product_id, category_id) -> [...]
        self.service = {}  # service_id -> (benefit_type, value_type, value_number, cap, min_tx, krw)
        self.tiers = collections.defaultdict(list)  # service_id -> [tier_id]

    @classmethod
    def load(cls):
        index = cls()

        for service_id, product_id, brand_id in rows_to_pairs(
            query(
                "SELECT bs.service_id, bs.card_product_id, sb.brand_id "
                "FROM benefit_service bs JOIN service_brand sb ON sb.service_id = bs.service_id "
                "WHERE bs.scope_type = 'BRAND'"
            )
        ):
            index.by_brand[(product_id, brand_id)].append(service_id)

        for service_id, product_id, category_id in rows_to_pairs(
            query(
                "SELECT bs.service_id, bs.card_product_id, sc.category_id "
                "FROM benefit_service bs JOIN service_category sc ON sc.service_id = bs.service_id "
                "WHERE bs.scope_type = 'INDUSTRY'"
            )
        ):
            index.by_category[(product_id, category_id)].append(service_id)

        # krw_per_point는 ACCUMULATE 건의 원화 환산에 쓴다. 지금은 전부 1.0000이지만
        # 값을 하드코딩하면 나중에 조용히 틀린다.
        for row in query(
            "SELECT bs.service_id, bs.benefit_type, bs.value_type, bs.value_number, "
            "       COALESCE(bs.per_tx_limit_amount, -1), bs.min_tx_amount, "
            "       COALESCE(pc.krw_per_point, 1) "
            "FROM benefit_service bs "
            "LEFT JOIN point_currency pc ON pc.point_currency_id = bs.point_currency_id"
        ):
            service_id, benefit_type, value_type, value_number, cap, min_tx, krw = row
            index.service[int(service_id)] = (
                benefit_type,
                value_type,
                float(value_number),
                float(cap),
                float(min_tx),
                float(krw),
            )

        # tier는 service에 직결되거나(155건) plan_group을 거쳐 붙는다(45건).
        for tier_id, service_id in rows_to_pairs(
            query("SELECT tier_id, service_id FROM benefit_tier WHERE service_id IS NOT NULL")
        ):
            index.tiers[service_id].append(tier_id)
        for tier_id, service_id in rows_to_pairs(
            query(
                "SELECT bt.tier_id, bs.service_id "
                "FROM benefit_tier bt JOIN benefit_service bs ON bs.plan_group_id = bt.plan_group_id "
                "WHERE bt.service_id IS NULL AND bt.plan_group_id IS NOT NULL"
            )
        ):
            index.tiers[service_id].append(tier_id)

        return index

    def summary(self):
        return (
            f"BRAND 조합 {len(self.by_brand):,} · INDUSTRY 조합 {len(self.by_category):,} · "
            f"service {len(self.service):,} · tier 보유 service {len(self.tiers):,}"
        )


def write_users(out_dir, rng):
    """users 5만 행. user_id는 1부터 — 성능 DB에는 seed-local이 없어 이 테이블이 비어 있다."""
    write_columns(out_dir, "users")
    path = os.path.join(out_dir, "users.tsv")
    with open(path, "w", encoding="utf-8") as fh:
        for user_id in range(1, TARGET_USERS + 1):
            name = rng.choice(SURNAMES) + rng.choice(GIVEN_1) + rng.choice(GIVEN_2)
            phone = f"010-{rng.randrange(1000, 10000)}-{rng.randrange(1000, 10000)}"
            fh.write(
                "\t".join(
                    (
                        str(user_id),
                        f"perf{user_id:06d}",
                        name,
                        phone,
                        PASSWORD_HASH,
                        NULL,  # provider_user_id — UNIQUE라 NULL로 둔다
                        PIN_HASH,
                        "1" if rng.random() < 0.85 else "0",  # is_location_agreed
                        "1" if rng.random() < 0.40 else "0",  # is_marketing_agreed
                        NULL,  # pin_auth_id
                        NULL,  # auth_expires_at
                        "0",  # auth_is_used
                        "0",  # pin_fail_count
                    )
                )
                + "\n"
            )
    return path


def draw_card_counts(rng):
    """유저별 보유 카드 수. 합계가 정확히 TARGET_CARDS가 되도록 맞춘다."""
    values = [count for count, _ in CARDS_PER_USER]
    weights = [weight for _, weight in CARDS_PER_USER]
    counts = rng.choices(values, weights=weights, k=TARGET_USERS)

    # 가중 추출은 합계를 보장하지 않는다. 목표에 닿을 때까지 무작위 유저를 1씩 밀어준다.
    total = sum(counts)
    lo, hi = values[0], values[-1]
    while total != TARGET_CARDS:
        step = 1 if total < TARGET_CARDS else -1
        i = rng.randrange(TARGET_USERS)
        if lo <= counts[i] + step <= hi:
            counts[i] += step
            total += step
    return counts


def write_user_cards(out_dir, rng, product_ids):
    """user_card 약 19.5만 행. UNIQUE(user_id, card_product_id)라 유저별로 비복원 추출한다.

    돌려주는 값은 결제 생성에 쓸 인덱스다. user_card_id가 1부터 순번이라 유저별 시작 위치만
    알면 카드 목록이 정해진다 — 30만 개짜리 리스트를 유저별로 쪼개 들 필요가 없다.
    """
    counts = draw_card_counts(rng)
    card_start = array.array("i")  # user_id - 1 -> 그 유저의 첫 user_card_id 직전 값
    card_product = array.array("B")

    write_columns(out_dir, "user_card")
    path = os.path.join(out_dir, "user_card.tsv")
    user_card_id = 0
    with open(path, "w", encoding="utf-8") as fh:
        for user_id in range(1, TARGET_USERS + 1):
            card_start.append(user_card_id)
            chosen = rng.sample(product_ids, counts[user_id - 1])
            for display_order, product_id in enumerate(chosen, start=1):
                user_card_id += 1
                card_product.append(product_id)
                credit_limit = rng.randrange(100, 1001) * 10_000
                fh.write(
                    "\t".join(
                        (
                            str(user_card_id),
                            str(user_id),
                            str(product_id),
                            f"{rng.choice('45')}{rng.randrange(100, 1000)}",
                            f"{rng.randrange(0, 10000):04d}",
                            f"{rng.randrange(2027, 2032)}-{rng.randrange(1, 13):02d}-01",
                            str(display_order),
                            rng.choice(BANKS),
                            f"{rng.randrange(0, 5_000_001)}.00",  # balance
                            f"{credit_limit}.00",
                            f"{rng.randrange(0, credit_limit + 1)}.00",  # scheduled_payment
                            "0",  # is_deleted
                        )
                    )
                    + "\n"
                )
    card_start.append(user_card_id)  # 보초값 — 마지막 유저의 끝을 잡는다
    return path, card_start, card_product


def pick_benefit(benefits, product_id, brand_id, category_id, amount, rng):
    """이 카드상품 × 이 가맹점에 실제로 붙는 혜택을 고른다.

    BRAND 스코프가 있으면 그쪽을 먼저 본다 — BenefitMapper도 brand가 있으면 BRAND 브랜치를
    먼저 평가한다. 붙는 게 없으면 (None, None, 0, 0)이다. 그것도 현실이라 그대로 둔다.
    """
    candidates = None
    if brand_id is not None:
        candidates = benefits.by_brand.get((product_id, brand_id))
    if not candidates:
        candidates = benefits.by_category.get((product_id, category_id))
    if not candidates:
        return None, None, 0.0, 0.0

    service_id = candidates[0] if len(candidates) == 1 else rng.choice(candidates)
    benefit_type, value_type, value_number, cap, min_tx, krw = benefits.service[service_id]
    if amount < min_tx:
        return None, None, 0.0, 0.0

    native = amount * value_number / 100.0 if value_type == "RATE" else value_number
    if cap >= 0:
        native = min(native, cap)
    native = round(native, 2)
    if native <= 0:
        return None, None, 0.0, 0.0

    # discount_amount는 네이티브(CASHBACK=원, ACCUMULATE=포인트 개수)지만
    # final_amount에서 빼는 값은 원화다. DefaultPaymentService.java:276-278과 같은 규칙이다.
    received_krw = round(native * krw, 2) if benefit_type == "ACCUMULATE" else native

    tiers = benefits.tiers.get(service_id)
    tier_id = None
    if tiers:
        tier_id = tiers[0] if len(tiers) == 1 else rng.choice(tiers)
    return service_id, tier_id, native, received_krw


def write_transactions(out_dir, rng, stores, benefits, card_start, card_product, reference):
    """payment_transaction 약 2,700만 행. 활성 3만명이 12개월 동안 결제한 모양이다.

    **유저당 건수를 상수로 두지 않는다.** 예전에는 전원이 정확히 같은 건수였는데, 그러면
    유저 스코프 쿼리(리포트·카드 계열)의 부하가 모든 유저에게 동일해져 꼬리가 사라진다.
    실제로 3단계 baseline에서 리포트 계열이 p95 12ms로 나온 원인이 이것이었다 — 어떤 유저를
    골라도 그 달 거래가 수십 건뿐이라, 측정이 '쿼리의 성질'이 아니라 '데이터의 성질'을 쟀다.

    평균 TX_MEAN_PER_ACTIVE를 유지하면서 로그정규로 뽑아 꼬리를 만든다. 꼬리 끝의 유저가
    그대로 '최악 케이스'가 되므로 무거운 유저를 인위적으로 심을 필요가 없다.
    """
    write_columns(out_dir, "payment_transaction")
    path = os.path.join(out_dir, "payment_transaction.tsv")
    active = rng.sample(range(1, TARGET_USERS + 1), ACTIVE_USERS)

    # 유저별 연간 결제 건수. 평균은 TX_MEAN_PER_ACTIVE이고 오른쪽에 긴 꼬리가 생긴다.
    tx_counts = {}
    for user_id in active:
        drawn = round(rng.lognormvariate(TX_MU, TX_SIGMA))
        tx_counts[user_id] = min(TX_MAX, max(TX_MIN, drawn))

    window_seconds = 365 * 24 * 3600
    start = reference - datetime.timedelta(seconds=window_seconds)

    # 카드가 1장인 유저는 '더 좋은 카드'가 존재할 수 없다. 전체에 BETTER_SHARE를 그대로 걸면
    # 그만큼 비율이 깎여 나간다(1장 유저가 12%라 0.30 × 0.88 = 26.4%가 된다).
    # 대안이 있는 거래에만 확률을 몰아 전체 비율이 BETTER_SHARE가 되게 맞춘다.
    multi_card_tx = sum(
        tx_counts[user_id]
        for user_id in active
        if card_start[user_id] - card_start[user_id - 1] > 1
    )
    total_tx = sum(tx_counts.values())
    better_rate = min(1.0, BETTER_SHARE * total_tx / multi_card_tx) if multi_card_tx else 0.0

    brand_store, brand_of, brand_category = stores.brand_store, stores.brand_of, stores.brand_category
    plain_store, plain_category = stores.plain_store, stores.plain_category
    etc_store = stores.etc_store
    n_brand, n_plain, n_etc = len(brand_store), len(plain_store), len(etc_store)
    plain_cut = BRAND_SHARE + PLAIN_SHARE

    stats = collections.Counter()
    transaction_id = 0

    with open(path, "w", encoding="utf-8") as fh:
        buffer = []
        for user_id in active:
            first = card_start[user_id - 1]
            last = card_start[user_id]
            card_count = last - first

            for _ in range(tx_counts[user_id]):
                transaction_id += 1

                roll = rng.random()
                if roll < BRAND_SHARE:
                    i = rng.randrange(n_brand)
                    store_id, brand_id, category_id = brand_store[i], brand_of[i], brand_category[i]
                    stats["brand"] += 1
                elif roll < plain_cut:
                    i = rng.randrange(n_plain)
                    store_id, brand_id, category_id = plain_store[i], None, plain_category[i]
                    stats["plain"] += 1
                else:
                    store_id, brand_id, category_id = etc_store[rng.randrange(n_etc)], None, 7
                    stats["etc"] += 1

                offset = first + (0 if card_count == 1 else rng.randrange(card_count))
                user_card_id = offset + 1
                product_id = card_product[offset]

                amount = float(
                    min(
                        AMOUNT_MAX,
                        max(
                            AMOUNT_MIN,
                            round(rng.lognormvariate(math.log(MEDIAN_AMOUNT[category_id]), AMOUNT_SIGMA)),
                        ),
                    )
                )

                service_id, tier_id, native, received = pick_benefit(
                    benefits, product_id, brand_id, category_id, amount, rng
                )
                if service_id is not None:
                    stats["benefit"] += 1

                # 더 유리한 카드가 있었던 건. 카드가 1장뿐이면 대안이 없다.
                better_id = alt_krw = missed = None
                if card_count > 1 and rng.random() < better_rate:
                    other = rng.randrange(card_count - 1)
                    if other >= (user_card_id - first - 1):
                        other += 1
                    better_id = first + other + 1
                    alt_krw = round(received + amount * rng.uniform(0.01, 0.05), 2)
                    missed = round(alt_krw - received, 2)
                    stats["better"] += 1

                paid_at = start + datetime.timedelta(seconds=rng.randrange(window_seconds))
                buffer.append(
                    "\t".join(
                        (
                            str(transaction_id),
                            str(user_card_id),
                            str(store_id),
                            NULL,  # payment_session_id — UNIQUE라 NULL로 둔다
                            f"{amount:.2f}",
                            f"{native:.2f}",
                            f"{amount - received:.2f}",  # final_amount는 원화를 뺀다
                            paid_at.strftime("%Y-%m-%d %H:%M:%S"),
                            TRANSACTION_STATUS,
                            "1" if rng.random() < APP_SHARE else "0",
                            "1",  # is_eligible
                            NULL if service_id is None else str(service_id),
                            NULL if tier_id is None else str(tier_id),
                            NULL if better_id is None else str(better_id),
                            NULL if alt_krw is None else f"{alt_krw:.2f}",
                            NULL if missed is None else f"{missed:.2f}",
                        )
                    )
                )

            if len(buffer) >= 180_000:
                fh.write("\n".join(buffer) + "\n")
                buffer.clear()
        if buffer:
            fh.write("\n".join(buffer) + "\n")

    # 꼬리가 실제로 생겼는지는 문서에 적을 근거이므로 여기서 바로 뽑는다.
    counts = sorted(tx_counts.values())
    stats["tx_p10"] = counts[len(counts) // 10]
    stats["tx_p50"] = counts[len(counts) // 2]
    stats["tx_p90"] = counts[len(counts) * 9 // 10]
    stats["tx_p99"] = counts[len(counts) * 99 // 100]
    stats["tx_max"] = counts[-1]
    stats["tx_mean"] = round(sum(counts) / len(counts))
    return path, stats


def user_typed_form(subcategory):
    """행정 소분류명을 **사용자가 칠 법한 말**로 줄인다. 조각이 없으면 None.

    검색어 풀이 `keywords.tsv`(업종 소분류명)에서 오는 탓에 행정 표기가 그대로 섞여 있었다 —
    `국수/칼국수` · `아이스크림 할인점` · `성형외과 의원` 같은 것들이다. **사용자는 이렇게
    치지 않는다.** `칼국수`라고 치지 `국수/칼국수`라고 치지 않는다.

    측정에서 이게 병목으로 잡혔다. ngram이 `+"국수" +"칼국수"`로 쪼개는데 `칼국수`가 `국수`를
    포함하므로 AND가 무의미해져 **칼국수 매장 8,681건을 전부 꺼낸 뒤** `LIKE '%국수/칼국수%'`로
    11건만 남긴다. 8,670건이 헛수고다. 운영 실측으로 이 검색어가 167ms였다.

    **가장 긴 조각을 쓴다.** 그쪽이 더 구체적이라 사용자가 치는 말에 가깝고, `의원`·`전문`처럼
    단독으로는 안 치는 범용 조각이 안 생긴다.

    ⚠️ **브랜드명에는 적용하지 않는다.** `S-OIL`·`SSG.COM`은 구분자가 들어 있어도 그 자체가
    사용자가 치는 말이다. 이 함수는 소분류명에만 쓴다(브랜드는 아래에서 따로 더한다).

    ⚠️ 매칭 수가 크게 는다 — `족발/보쌈` 1건 → `족발` 7,200건. **측정이 보수적인 쪽으로
    바뀐다.** 실사용에 맞추는 것이 목적이므로 의도된 방향이다.
    """
    fragments = [f for f in re.split(r"[^\w]+", subcategory, flags=re.UNICODE)
                 if len(f) >= KEYWORD_MIN_LENGTH]
    return max(fragments, key=len) if fragments else None


def measure_selectivity(keywords):
    """후보 검색어별 store_name LIKE 매칭 수를 잰다. 이 값 하나가 두 가지를 한다.

    ① **0건이면 풀에서 뺀다.** 소분류명은 대부분 행정 업종 용어라 상호명에 나타나지 않는다 —
       기타(7)를 뺀 뒤에도 117개 중 83개가 0건이었다(`여성 의류 소매업` `내과/소아과 의원`).
       어느 접미사가 업종 용어인지 추측하지 않고, 실제로 매칭되는지를 재서 가른다.
    ② **그 값이 곧 Zipf 순위다.** 매장 수로 순위를 매기면 매장은 5만 개인데 상호명에는
       한 번도 안 나타나는 말이 상위로 올라간다. 실제 쿼리 비용을 좌우하는 건 매장 수가
       아니라 **LIKE가 훑어 만나는 행 수**다.

    ⚠️ LIKE 패턴에 검색어가 그대로 들어가므로 %·_·\\·'를 이스케이프한다. 안 하면 %가
       "아무거나"로 해석돼 매칭 수가 조용히 폭증한다.
    ⚠️ 전부 한 문장으로 묶으면 3~4분짜리 쿼리가 되어 net_write_timeout(기본 60초)에 걸린다.
       25개씩 끊으면 배치 하나가 20초 안쪽이다.
    ⚠️ 결과를 검색어 문자열이 아니라 **인덱스**로 되받는다. MySQL은 문자열 리터럴의 `\\%`를
       백슬래시째로 돌려주므로, 키로 쓰면 %·_가 든 검색어가 조용히 0건으로 떨어진다.
    """
    def esc(k):
        return k.replace("\\", "\\\\").replace("'", "''").replace("%", "\\%").replace("_", "\\_")

    per_cat = {}
    for i in range(0, len(keywords), 25):
        batch = keywords[i:i + 25]
        parts = [f"SELECT {i + j} i, category_id c, COUNT(*) n FROM store "
                 f"WHERE store_name LIKE '%{esc(k)}%' GROUP BY category_id"
                 for j, k in enumerate(batch)]
        for idx, c, n in query(" UNION ALL ".join(parts)):
            per_cat.setdefault(keywords[int(idx)], {})[int(c)] = int(n)
        print(f"  매칭 수·카테고리 측정 {len(per_cat)}/{len(keywords)}", flush=True)

    counts = {}
    for k in keywords:
        cats = per_cat.get(k, {})
        # 동점이면 카테고리 ID가 작은 쪽 — 결정적이어야 재실행 결과가 같다.
        modal = min(cats, key=lambda c: (-cats[c], c)) if cats else 7
        counts[k] = (sum(cats.values()), modal)
    return counts


def allocate_slots(rnd, total, caps):
    """검색어 슬롯 total개를 CATEGORY_WEIGHTS 비율대로 업종에 나눈다.

    검색어가 5개뿐인 주유에 6칸이 떨어지면 비복원 추출이 불가능하므로 caps로 막는다
    (16칸에서 확률 0.03% — 5만 명이면 십수 명). 막힌 칸은 남은 업종으로 재분배된다.
    """
    counts = {c: 0 for c in CATEGORY_WEIGHTS}
    for _ in range(total):
        avail = [c for c in CATEGORY_WEIGHTS if counts[c] < caps[c]]
        if not avail:
            break
        counts[rnd.choices(avail, weights=[CATEGORY_WEIGHTS[c] for c in avail])[0]] += 1
    return counts


def write_search_history(out_dir, rng, keywords, weights, categories, reference, seed):
    """search_history 80만 행 (5만 명 × 16개). UNIQUE(user_id, keyword)라 유저별로 비복원 추출한다.

    **균등 추출을 쓰지 않는다.** 예전에는 `rng.sample()`이라 검색어 빈도가 2,479~2,792로
    평평했는데(최대/최소 1.13배), 실제 검색은 소수 검색어에 몰린다.

    **업종 간 비중은 CATEGORY_WEIGHTS, 업종 안 상대비는 매칭 수 Zipf.** 예전에는 매칭 수
    Zipf 하나만 전역으로 걸었는데, 그러면 "매장이 많은 말 = 많이 검색되는 말"이 되어 카드
    혜택 앱인데도 병원 계열(약국·한의원·치과의원)이 검색의 17%를 차지했다. 업종 비중만
    바꾸고 업종 안 순위는 그대로 두면 무거운 검색어 노출은 유지된다 — 선택도 >13,000 구간
    비중이 16.9% → 16.2%로 사실상 그대로다(설계 §2).

    슬롯 배분으로 업종 비중을 **정확히** 맞춘다. 유저마다 16칸을 업종 벡터대로 나눈 뒤 그
    업종 안에서만 뽑으므로, 실현된 업종 비중이 목표와 일치한다. 전역 Gumbel top-k에 가중치만
    실어서는 top-k의 비선형성 때문에 목표 비중이 재현되지 않는다.

    비복원 가중 추출은 **Gumbel top-k**로 한다 — 후보마다 log(w) + Gumbel 잡음을 주고 상위 k개를
    고르면 가중 비복원 추출과 같아진다. UNIQUE(user_id, keyword) 제약을 그대로 지킨다.
    (top-k는 머리쪽을 압축한다. 업종 안 상대비가 가중치 비와 정확히 같지는 않다 — 예전과 같다.)

    ⚠️ 전용 RNG를 쓴다. main의 rng를 쓰면 앞선 writer들이 소비한 양에 따라 결과가 달라져
    --only-search-history로 뽑은 파일이 전체 실행 결과와 달라진다.
    """
    write_columns(out_dir, "search_history")
    path = os.path.join(out_dir, "search_history.tsv")
    recent_cut = reference - datetime.timedelta(days=7)
    window_seconds = 180 * 24 * 3600
    start = reference - datetime.timedelta(seconds=window_seconds)

    sh_rng = random.Random(seed ^ 0x5EA2C4)
    ranked = sorted(keywords, key=lambda k: (-weights.get(k, 1), k))
    # 전역 순위 Zipf. 업종 안 상대비를 보존하려면 업종별로 다시 매기면 안 된다 —
    # 약국(전역 2위)과 종합병원(전역 50위)의 비가 1/2:1/50에서 1/1:1/7로 바뀌어 버린다.
    log_w = {k: math.log(1.0 / (i + 1)) for i, k in enumerate(ranked)}

    by_cat = {}
    for k in ranked:
        by_cat.setdefault(categories.get(k, 7), []).append(k)
    by_cat.pop(7, None)  # 기타는 칩에도 벡터에도 없다
    missing = [c for c in CATEGORY_WEIGHTS if c not in by_cat]
    if missing:
        sys.exit(f"검색어가 하나도 없는 카테고리가 있다: {missing}")
    caps = {c: len(by_cat[c]) for c in CATEGORY_WEIGHTS}
    print(f"  업종별 검색어 수 " + " · ".join(f"{c}:{caps[c]}" for c in sorted(caps)))

    row_id = 0
    with open(path, "w", encoding="utf-8") as fh:
        for user_id in range(1, TARGET_USERS + 1):
            picks = []
            for cid, n in allocate_slots(sh_rng, KEYWORDS_PER_USER, caps).items():
                if not n:
                    continue
                pool = by_cat[cid]
                gumbel = sorted(
                    ((log_w[k] - math.log(-math.log(sh_rng.random())), k) for k in pool),
                    reverse=True)
                picks.extend(k for _, k in gumbel[:n])
            for keyword in picks:
                row_id += 1
                # 1/4은 최근 7일 이내. 검색 이력 조회가 최신순 상위 N을 본다.
                if sh_rng.random() < 0.25:
                    searched = recent_cut + datetime.timedelta(seconds=sh_rng.randrange(7 * 24 * 3600))
                else:
                    searched = start + datetime.timedelta(seconds=sh_rng.randrange(window_seconds))
                fh.write(
                    f"{row_id}\t{user_id}\t{keyword}\t{searched.strftime('%Y-%m-%d %H:%M:%S')}\n"
                )
    return path, row_id


def main():
    parser = argparse.ArgumentParser(description="성능 테스트용 합성 데이터를 만든다")
    parser.add_argument("--seed", type=int, default=SEED, help=f"난수 시드 (기본 {SEED})")
    parser.add_argument(
        "--reference-date",
        default=None,
        help="paid_at·searched_at의 기준일 YYYY-MM-DD (기본 오늘). 출력을 고정하려면 지정한다",
    )
    parser.add_argument(
        "--only-search-history", action="store_true",
        help="search_history.tsv만 다시 만든다. 검색어 풀을 바꿨을 때 쓴다 — "
             "전용 RNG를 쓰므로 전체 실행과 같은 결과가 나온다.")
    args = parser.parse_args()

    rng = random.Random(args.seed)
    reference = (
        datetime.datetime.strptime(args.reference_date, "%Y-%m-%d")
        if args.reference_date
        else datetime.datetime.now().replace(microsecond=0)
    )

    out_dir = os.path.join(data_dir(), "out")
    store_path = os.path.join(out_dir, "store.tsv")
    if not os.path.exists(store_path):
        sys.exit(f"store.tsv가 없다. build_store.py를 먼저 돌린다: {store_path}")

    print(f"시드 {args.seed} · 기준일 {reference:%Y-%m-%d %H:%M:%S}\n")

    # --only-search-history면 store/혜택 인덱스를 올리지 않는다. search_history는 이 둘을
    # 쓰지 않는데, StoreIndex는 307MB TSV를 통째로 훑어 로드에만 수십 초가 든다.
    if not args.only_search_history:
        stores = StoreIndex.load(store_path)
        print(f"store  {stores.summary()}")

        benefits = BenefitIndex.load()
        print(f"혜택   {benefits.summary()}")

    keyword_path = os.path.join(out_dir, "keywords.tsv")
    weights = {}
    subcategories = set()   # 브랜드명과 구분한다 — 축약은 소분류명에만 건다
    for line in open(keyword_path, encoding="utf-8"):
        if not line.strip():
            continue
        word, _, count = line.rstrip("\n").partition("\t")
        weights[word] = int(count) if count else 1
        subcategories.add(word)

    # 브랜드명도 검색어다. 가중치는 그 브랜드의 매장 수 — 소분류와 같은 척도라 함께 정렬된다.
    brand_stores = {int(b): int(n) for b, n in
                    query("SELECT brand_id, COUNT(*) FROM store "
                          "WHERE brand_id IS NOT NULL GROUP BY brand_id")}
    for brand_id, name in query("SELECT brand_id, brand_name FROM brand"):
        name = name.strip()
        if name:
            weights[name] = max(weights.get(name, 0), brand_stores.get(int(brand_id), 1))

    candidates = sorted(weights)
    print(f"후보 {len(candidates)}개 (기타 제외 소분류명 + 브랜드명)")

    # keywords.tsv의 매장 수는 폴백으로만 남는다. 실제 순위와 필터는 아래 실측이 정한다.
    measured = measure_selectivity(candidates)
    keywords = [k for k in candidates if measured.get(k, (0, 7))[0] > 0]
    dropped = len(candidates) - len(keywords)

    # ⚠️ 축약은 **0건 필터 뒤에** 건다. 앞에 걸면 그 필터가 무력화된다 — `여성 의류 소매업`은
    #    상호명에 안 나타나 0건으로 걸러지던 건데, `소매업`으로 줄이면 4,910건에 매칭돼
    #    되살아난다. 그 필터가 하던 일이 정확히 행정 용어 제거다(실측으로 50종이 되살아났다).
    renamed = {}
    for k in keywords:
        if k in subcategories:
            typed = user_typed_form(k)
            if typed is not None and typed != k:
                renamed[k] = typed
    if renamed:
        print(f"  사용자 표기로 축약 {len(renamed)}종: "
              + " · ".join(f"{a}→{b}" for a, b in sorted(renamed.items())[:6])
              + (" ..." if len(renamed) > 6 else ""))
        # 줄인 형태는 매칭 수가 달라지므로 다시 잰다. 브랜드명과 겹치면 브랜드 쪽을 남긴다.
        fresh = measure_selectivity(sorted(set(renamed.values()) - set(keywords)))
        measured = {**measured, **fresh}
        keywords = sorted({renamed.get(k, k) for k in keywords})
        keywords = [k for k in keywords if measured.get(k, (0, 7))[0] > 0]
    weights = {k: measured[k][0] for k in keywords}

    # 최빈 카테고리는 매칭이 적은 검색어에서 흔들린다 — `AK몰`은 매칭 1건이고 그 한 곳이
    # 카페라 카페/디저트로 잡힌다. 브랜드명과 정확히 일치하면 brand 테이블이 정답이므로
    # 그쪽을 우선한다(실측 7종/80종이 어긋났고 홈플러스·롯데마트가 쇼핑으로 잡혀 있었다).
    brand_categories = {n.strip(): int(c)
                        for c, n in query("SELECT category_id, brand_name FROM brand") if n.strip()}
    categories = {k: brand_categories.get(k, measured[k][1]) for k in keywords}
    overridden = sum(1 for k in keywords
                     if k in brand_categories and brand_categories[k] != measured[k][1])
    print(f"  브랜드 테이블로 카테고리를 바로잡은 검색어 {overridden}개")
    print(f"검색어 {len(keywords)}개 "
          f"(후보 {len(candidates)} − 매칭 0건 {dropped}, "
          f"업종 간 CATEGORY_WEIGHTS · 업종 내 LIKE 매칭 수 Zipf)\n")

    if not args.only_search_history:
        product_ids = [int(row[0]) for row in query("SELECT card_product_id FROM card_product")]

        path = write_users(out_dir, rng)
        print(f"users               {TARGET_USERS:>9,}  → {os.path.basename(path)}")

        path, card_start, card_product = write_user_cards(out_dir, rng, product_ids)
        print(f"user_card           {len(card_product):>9,}  → {os.path.basename(path)}")

        path, stats = write_transactions(
            out_dir, rng, stores, benefits, card_start, card_product, reference
        )
        total = stats["brand"] + stats["plain"] + stats["etc"]
        print(f"payment_transaction {total:>9,}  → {os.path.basename(path)}")

    path, count = write_search_history(
        out_dir, rng, keywords, weights, categories, reference, args.seed)
    print(f"search_history      {count:>9,}  → {os.path.basename(path)}\n")

    if args.only_search_history:
        return

    print("거래 분포")
    print(f"  브랜드 보유 store   {stats['brand']:>9,}  ({stats['brand'] / total * 100:.1f}%)")
    print(f"  브랜드 없음 cat1~6  {stats['plain']:>9,}  ({stats['plain'] / total * 100:.1f}%)")
    print(f"  기타 cat7           {stats['etc']:>9,}  ({stats['etc'] / total * 100:.1f}%)")
    print(f"  혜택이 붙은 건      {stats['benefit']:>9,}  ({stats['benefit'] / total * 100:.1f}%)")
    print(f"  better_user_card    {stats['better']:>9,}  ({stats['better'] / total * 100:.1f}%)")

    print("\n활성 유저당 연간 거래 수 (꼬리가 있어야 한다)")
    print(f"  p10 {stats['tx_p10']:>6,}   p50 {stats['tx_p50']:>6,}   평균 {stats['tx_mean']:>6,}")
    print(f"  p90 {stats['tx_p90']:>6,}   p99 {stats['tx_p99']:>6,}   max  {stats['tx_max']:>6,}")


if __name__ == "__main__":
    main()
