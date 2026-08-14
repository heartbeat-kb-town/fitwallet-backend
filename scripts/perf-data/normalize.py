"""상호명 정규화와 브랜드 접두사 최장 일치 매처.

DB를 모르는 순수 모듈이다. 별칭 목록은 build_store.py가 brand·brand_alias에서 읽어 넣어준다.

**왜 접두사인가** — 공공데이터의 `지점명` 컬럼은 채워질 때도 있고 비어 있을 때도 있다.
비어 있으면 지점이 상호명 안에 들어온다('CU수원운동장점'). 어느 쪽이든 브랜드는 맨 앞에 오므로
접두사로 찾는다.

**왜 최장 일치인가** — 짧은 별칭부터 훑으면 '이마트24역삼점'이 '이마트'로 잘못 매칭된다.
같은 함정이 롯데마트/롯데몰/롯데VIC마켓, 컬리/마켓컬리에도 있다. 그래서 긴 후보부터 본다.
"""

import re
import unicodedata

# 공백 · '-' · '.' · 괄호를 지운다. 상호명 표기 흔들림의 대부분이 여기서 흡수된다.
_STRIP_CHARS = re.compile(r"[\s\-.()]")

# 접미사 절단. 긴 것부터 지워야 '2호점'이 '점'만 떨어져 '2호'가 남는 일이 없다.
_SUFFIXES = ("직영점", "지점", "호점", "점")


def normalize(name):
    """상호명을 비교 가능한 형태로 만든다.

    NFKC가 전각 영숫자('ＣＵ')를 반각으로 접어준다. 그 뒤 소문자화하고 구분자를 지운다.
    """
    if not name:
        return ""
    text = unicodedata.normalize("NFKC", name).lower()
    return _STRIP_CHARS.sub("", text)


def strip_branch_suffix(text):
    """정규화된 문자열에서 지점 접미사를 한 번 떼어낸다.

    '스타벅스강남2호점'처럼 상호명 한 칸에 지점까지 들어온 행을 위한 것이다.
    브랜드명 자체가 접미사로 끝나는 경우를 지우지 않도록 결과가 비면 원본을 돌려준다.
    """
    for suffix in _SUFFIXES:
        if text.endswith(suffix) and len(text) > len(suffix):
            return text[: -len(suffix)]
    return text


class BrandMatcher:
    """정규화된 상호명 앞부분에서 가장 긴 별칭을 찾는다.

    별칭을 길이별 집합으로 나눠 두고 긴 길이부터 조회한다. 별칭이 86개뿐이라 트라이까지 갈
    필요가 없고, 길이별 dict 조회는 상호명 하나당 최대 (가장 긴 별칭 길이)번이면 끝난다.
    """

    def __init__(self):
        self._by_length = {}
        self._max_length = 0

    def add(self, alias, brand_id):
        """별칭 하나를 등록한다. 원문 표기를 받아 내부에서 정규화한다.

        같은 정규화형이 두 브랜드를 가리키면 데이터가 모순이므로 즉시 실패시킨다.
        DB의 UNIQUE(alias)는 원문 표기 기준이라 정규화 후 충돌까지는 막지 못한다.
        """
        key = normalize(alias)
        if not key:
            return
        bucket = self._by_length.setdefault(len(key), {})
        existing = bucket.get(key)
        if existing is not None and existing != brand_id:
            raise ValueError(
                f"별칭 '{alias}'의 정규화형 '{key}'가 brand_id {existing}와 {brand_id} 양쪽을 가리킨다"
            )
        bucket[key] = brand_id
        self._max_length = max(self._max_length, len(key))

    def match(self, normalized_name):
        """접두사 최장 일치로 brand_id를 찾는다. 없으면 None."""
        upper = min(self._max_length, len(normalized_name))
        for length in range(upper, 0, -1):
            bucket = self._by_length.get(length)
            if bucket:
                hit = bucket.get(normalized_name[:length])
                if hit is not None:
                    return hit
        return None

    def __len__(self):
        return sum(len(bucket) for bucket in self._by_length.values())


def build_store_name(business_name, branch_name):
    """store_name을 조립한다. 지점명이 따로 있으면 뒤에 붙인다.

    '지점명'이 이미 상호명 안에 들어 있는 행이 있어, 중복되면 붙이지 않는다.
    """
    business_name = (business_name or "").strip()
    branch_name = (branch_name or "").strip()
    if not branch_name or branch_name in business_name:
        return business_name
    return f"{business_name} {branch_name}"
