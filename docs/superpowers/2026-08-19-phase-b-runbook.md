# Phase B 실행 런북 — 최종 부하 테스트

작성 2026-08-19 · 브랜치 `perf/final-load-test-harness` (**push 안 됨**)

> **다른 계정/세션이 Phase B를 실행하기 위한 문서다.** Phase A(하네스·데이터셋)는 끝났다.
> 여기서는 **측정만** 한다. 코드를 고칠 일이 없어야 정상이다.
> 배경과 판정 근거는 `docs/superpowers/2026-08-19-final-load-test-handoff.md`.

---

## ⚠️ 시작 전에 — Phase A 전체 리뷰가 미완이다

Phase A의 태스크별 리뷰는 전부 통과했지만(1·2·3·4·5·6·10), **브랜치 전체를 한 번에 보는
최종 리뷰는 끝나지 못했다**(2026-08-19, 세션 한도). 측정을 시작하기 전에 한 번 돌리는 것을 권한다.

```
브랜치 perf/final-load-test-harness 전체(origin/main..HEAD, 23커밋)를 리뷰해줘.
설계는 docs/superpowers/specs/2026-08-19-final-load-test-design.md,
판정 기록은 .superpowers/sdd/2026-08-19-final-load-test/progress.md에 있어.

특히 "측정 결과를 조용히 왜곡할 수 있는 것"을 봐줘. 이 부류가 지금까지 셋 나왔다:
① 밀도 표가 에러 없이 빈 채 렌더링 (summaryTrendStats에 count 누락)
② 매칭 수를 문자열 키로 되받아 % 포함 검색어가 조용히 0건
③ 격자 수 분위수로 밀도가 한 단계에 91.3% 몰림
같은 부류가 더 있는지가 최우선이야.
```

이 리뷰 없이 진행해도 측정은 돌아간다. 다만 **위 세 부류가 전부 "돌려보기 전에는 안 보이는"
종류**였으므로, 한 번 더 보는 값이 있다.

---

## 0. 쓸 스크립트는 이 넷뿐이다

| 스크립트 | 언제 | 무엇을 |
|---|---|---|
| `scripts/perf-k6/prod-sql.sh` | 인덱스 조작·검증 | 운영 RDS에 SQL 실행. 자격증명을 EB에서 런타임 조회 |
| `scripts/perf-k6/k6ec2.sh` | 전송·측정 | k6 EC2에 올리고 돌리고 결과를 `results/`로 회수 |
| `aws elasticbeanstalk update-environment` | 되돌리기·복구 | WAR 버전 라벨 교체 |
| `gh pr merge 270` | 최신화 | #270 머지 → CD 자동 배포 |

**쓰지 말아야 할 것:**

- 🛑 **`scripts/perf-data/load.sh --reset`** — `PERF_TABLES`와 무관하게 `payment_transaction`
  2,712만 행을 TRUNCATE한다. Phase B에는 적재가 없으니 **이 스크립트를 아예 쓸 일이 없다**
- 🛑 **`python3 scripts/perf-k6/extract-scenarios.py`** — 시나리오 CSV를 다시 만든다.
  **개선 전/후가 같은 표본을 봐야 하므로 측정 중에는 절대 돌리지 않는다.** CSV는 이미
  커밋돼 있다(`scenarios-load.csv` 2,000행 · `scenarios-baseline.csv` 40행)
- 🛑 **로컬에서 `k6 run`** — 인터넷 왕복이 껴서 1차 측정과 조건이 어긋난다. 반드시 `k6ec2.sh`

---

## 1. 측정 4회 — 명령 그대로

```bash
# 한 번만. 스크립트와 CSV를 k6 EC2로 옮긴다.
scripts/perf-k6/k6ec2.sh push

# 개선 전 (Phase B-1 이후)
scripts/perf-k6/k6ec2.sh run baseline.js baseline-before-20260819 READ_ITERATIONS=30 WARMUP=10
scripts/perf-k6/k6ec2.sh run load.js     load-before-20260819

# 개선 후 (Phase B-3 이후)
scripts/perf-k6/k6ec2.sh run baseline.js baseline-after-20260819 READ_ITERATIONS=30 WARMUP=10
scripts/perf-k6/k6ec2.sh run load.js     load-after-20260819
```

> ⚠️ **baseline에 `READ_ITERATIONS=30 WARMUP=10`을 반드시 넘긴다.** 기본값이 200이라 생략하면
> 측정 30행을 6~7회씩 돌아 "40행 1:1" 층화 설계가 깨지고, 3단계의 "N=200 재실행 금지" 결정과
> 충돌하며 측정 시간이 크게 는다.

> ⚠️ **`push`는 처음 한 번만.** 측정 중에 다시 push하면 CSV가 바뀔 수 있다(스크립트를 고쳤다면
> 그때만 다시 push하고, **그 경우 before부터 다시 잰다**).

결과는 `scripts/perf-k6/results/<이름>.{md,json}`으로 내려온다. `results/`는 gitignore이므로
**수치의 정본은 노션**이다.

---

## 2. 순서 — 끝났을 때 운영이 최신이어야 한다

### B-1. 개선 전 만들기

```bash
# ① V12 인덱스 DROP (개선 전 store/keywords가 이 인덱스 덕을 보면 개선 폭이 과소평가된다)
scripts/perf-k6/prod-sql.sh -e "DROP INDEX idx_search_history_searched_at_keyword ON search_history;"
scripts/perf-k6/prod-sql.sh -N -e "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='fitwallet' AND index_name='idx_search_history_searched_at_keyword';"
#   → 0 이어야 한다

# ② 개선 전 WAR로 되돌린다 (EB에 이미 있어 재빌드 불필요, 3~5분)
aws elasticbeanstalk update-environment --environment-name fitwallet-prod \
  --version-label gh-283.1-4b6f6af --region ap-northeast-2
```

**③ Flyway 기동 확인 — 이 계획의 단일 최대 위험이다.**

이력에 V11~V13이 있는데 그 WAR은 V10까지만 안다. Flyway 13의 `ignoreMigrationPatterns`
기본값이 `*:future`라 통과할 것으로 보지만, **틀리면 앱이 아예 안 뜬다.**

```bash
aws elasticbeanstalk describe-environments --environment-names fitwallet-prod \
  --region ap-northeast-2 --query 'Environments[].{S:Status,H:Health,V:VersionLabel}'
curl -s -o /dev/null -w '%{http_code}\n' \
  http://fitwallet-backend-prod.ap-northeast-2.elasticbeanstalk.com/health/db
```
→ `Ready` / `Green` / `gh-283.1-4b6f6af`, HTTP `200`

**실패하면 즉시 멈추고 복구한다:**
```bash
aws elasticbeanstalk update-environment --environment-name fitwallet-prod \
  --version-label gh-293.1-9194df4 --region ap-northeast-2
scripts/perf-k6/prod-sql.sh -e "CREATE INDEX idx_search_history_searched_at_keyword ON search_history (searched_at, keyword); ANALYZE TABLE search_history;"
```
그다음 `flyway_schema_history` 조작 여부는 **사람에게 묻는다.** 임의로 하지 않는다.

**④ 옛 코드가 실제로 도는지 육안 확인** — `store/search` 좌표 갈래가 **1초 이상**이어야 한다.
40ms대면 새 WAR이 아직 돈다(배포 미완).

### B-2. before 측정 → 위 §1의 앞 두 줄

1차 때와 같은 붕괴(SLO 0/19 · 5xx 45%)가 재현될 것으로 예상한다. **실패가 아니라 예상된 결과다.**

### B-3. 복구 + 최신화

```bash
# ① V12 재생성 — 잊으면 after가 부당하게 느려진다
scripts/perf-k6/prod-sql.sh -e "CREATE INDEX idx_search_history_searched_at_keyword ON search_history (searched_at, keyword); ANALYZE TABLE search_history;"
scripts/perf-k6/prod-sql.sh -N -e "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='fitwallet' AND index_name='idx_search_history_searched_at_keyword';"
#   → 1 이어야 한다

# ② #270 머지 (프론트 합의 확인 후) → CD가 자동 배포
gh pr merge 270 --repo heartbeat-kb-town/fitwallet-backend --squash
```

**③ V14 인덱스 생성에 80초+ 걸린다.** 그동안 앱이 늦게 올라온다. EB `Ready`/`Green`과
`/health/db` 200을 확인한 뒤,

```bash
scripts/perf-k6/prod-sql.sh -t -e "
SELECT index_name FROM information_schema.statistics
 WHERE table_schema='fitwallet' AND table_name='store'
   AND index_name IN ('ft_store_name','idx_store_lat_lng_name') GROUP BY index_name;
SELECT (SELECT COUNT(*) FROM store WHERE store_name LIKE '%bar%') like_n,
       (SELECT COUNT(*) FROM store WHERE MATCH(store_name) AGAINST('+\"bar\"' IN BOOLEAN MODE)) match_n;"
```
→ 인덱스 2개가 나오고 **`like_n = match_n`**. 다르면 기본 stopword로 만들어진 것이다 —
`ft_store_name`을 DROP하고 V14를 다시 돌린다. **이걸 놓치면 영어 검색어가 조용히 0건이 되어
after가 부당하게 빨라진다.**

### B-4. after 측정 → 위 §1의 뒤 두 줄

---

## 3. 측정 중 지켜야 할 것

- **어떤 PR도 머지하지 않는다**(4단계 설계 §8). `main` 머지 = 즉시 운영 배포다.
  #270 머지는 B-3의 계획된 단계라 예외
- **CSV를 다시 뽑지 않는다.** 두 측정이 같은 표본을 봐야 한다
- **`search_history`를 다시 적재하지 않는다.** 2026-08-19에 이미 재생성했고,
  `store/keywords`가 `NOW() - 7 DAY`를 보므로 before/after가 같은 데이터를 봐야 한다

---

## 4. 첫 측정에서 한 번 더 확인할 것 둘

스모크(10 VU · 30초)에서 표본이 얇아 확정하지 못한 항목이다. **본 측정 첫 실행 결과로 확인한다.**

1. **`load.js`의 밀도 표에 `empty` 버킷이 뜨는가.** 본 측정은 약 4,590 요청 × 3% ≈ 138건이라
   충분하다. 안 뜨면 그때 멈추고 원인을 본다
2. **`empty` 좌표에서 `benefit_expected`가 에러 없이 도는가.** 희소 앵커 6곳 전부 3km 안에는
   매장이 있어(1·41·5·43·14·4건) `storeId`가 매번 채워질 것으로 본다 — 폴백은 방어 코드다

---

## 5. 결과 보고

**노션 「9. 최종 결과」**(https://app.notion.com/3c0a561881a4805886d6edd2833ebaba)에 쓴다.
이미 있는 페이지이고 상태가 `시작 전`이다. **1차 결과 문서(「3. SLI/SLO 확정 , 1차 부하 테스트」)는
건드리지 않는다** — 데이터셋이 달라 같은 표에 못 섞는다.

### 서술 규칙 하나

**"N배 빨라졌다"는 1 VU 표에서만 쓴다.** 100 VU의 before는 커넥션 풀(20) 고갈을 잰 것이라
19개가 전부 3.00초에 붙어 있고, **손대지도 않은 `health_db`까지 74배 빨라진 것처럼 나온다.**
인과는 맞지만(쿼리가 빨라져 풀이 안 마름) 엔드포인트별 배수로 읽으면 틀린다.
100 VU는 **SLO 충족 수 · 에러율 · 달성 처리량**으로만 말한다.

### 반드시 공개할 한계

1. **커넥션 풀 20을 바꾸지 않았다** — before의 100 VU는 쿼리가 아니라 풀 포화를 잰 값이다
2. **coordinated omission** — before는 실패가 많아 표본이 줄고 p95가 실제보다 낙관적이다
3. **희소 좌표 3%는 주입한 값이다** — 매장 밀도 가중 샘플링이 1km 0건 좌표를 구조적으로
   배제하기 때문이고(지터 ±1.1km에서도 0.0%, 실측), 그 비율 자체에는 근거가 없다.
   **희소 구간의 p95는 표본이 얇아 말하지 않는다**
4. **밀도 단계는 `0.01°` 격자 근사**이지 반경 실측이 아니다. 경계는 **매장 수 가중** 7분위수다
5. **1 VU는 N=30이라 p95를 말하지 않는다** — 중앙값과 max로 읽는다
6. **개선 전은 V12 인덱스만 되돌렸다** — V11 좌표 인덱스와 V13 요약 테이블은 남아 있지만
   옛 코드에 그것을 쓰는 SQL이 없어 영향이 0이다
7. **검색어 풀이 85개로 줄었고 매칭 0건이 0%다** — 실제 "검색 결과 없음" 비율은 알 수 없어
   임의 값을 넣지 않았다. 0건 검색은 싸므로 이 선택은 테스트를 **더 어렵게** 만든다
8. **1차 부하 테스트(2026-08-17)와 직접 비교할 수 없다** — 데이터셋이 다르다

---

## 6. 범위 밖

- 커넥션 풀·JVM 힙 조정(노션 「8. 커넥션 풀 조정」) — 이번엔 건드리지 않는다
- breakpoint(파괴 지점) 측정
- 50 VU 측정 — "우리 실제 규모에서 되는가"는 이번에도 미답으로 남는다
- `load.sh --reset`의 지뢰 수정 — 별도 작업
