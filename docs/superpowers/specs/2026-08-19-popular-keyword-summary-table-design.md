# 인기 검색어 요약 테이블 설계 (2026-08-19)

> 판단 근거의 정본은 노션 「7. store/keywords 쿼리 최적화」
> (https://app.notion.com/p/3c0a561881a4809b9fa4eb94367907d5).
> 여기는 그 결론과 구현 지시를 담는다.

## 왜 또 손대는가 — V12로는 목표선을 못 넘었다

`V12__search_history_popular_index.sql`(PR #266, 2026-08-19 배포)로 커버링 인덱스를 넣어
운영 p50 **297ms → 127ms**까지 왔다. 그런데 **SLO는 100ms**다.

| | 값 |
|---|---|
| SLO | 100ms |
| V12 적용 후 실측 p50 | 127ms |
| 배수 | **1.27×** ❌ |

**남은 27ms를 인덱스로 깎을 방법이 없다는 것은 이미 측정으로 나와 있다.**
V12 전후로 쿼리를 구간별로 쪼개면 이렇게 된다.

| 구간 | V12 이전 | V12 이후 |
|---|---|---|
| 읽기(스캔) | 88.5ms | **25.8ms** |
| 집계(`GROUP BY` 임시 테이블) | 70ms | **69ms** |

인덱스가 고친 것은 읽기 구간뿐이고, **집계 구간 70ms는 전후가 같다.** 이건
"요청마다 22만 행을 전량 재집계한다"는 구조 자체가 원인이라 어떤 인덱스로도 없앨 수 없다.

그래서 노션 §후보 G — **요약 테이블(사전 집계)** 로 간다.

## 목표

**요청 경로에서 집계를 없앤다.** 조회는 5행짜리 테이블 읽기로 끝낸다.

로컬에서 요약 테이블을 실제로 만들어 재본 값:

| | 요청당 DB 시간 |
|---|---|
| V12 이전 | 190ms |
| V12 (커버링 인덱스) | 106ms |
| **요약 테이블 읽기** | **0.025ms** (`Index scan on PRIMARY`, 5행) |

집계 1회 비용은 운영에서 **135ms**다(V12 인덱스가 이미 있으므로).
5분마다 돌리면 DB 부하는 `135ms / 300초 = 0.045%`다.

> ⚠️ **이건 SLO 미달을 고치는 작업이면서, 동시에 부하 여유를 사는 작업이다.**
> 4단계 부하 테스트에서 이 API는 p95 7.03초로 5개 개선 대상 중 배수가 가장 컸다(26.4×).
> 요청당 DB 점유가 0에 수렴하면 **이 API가 커넥션 풀을 놓아주고**, 남은 개선 대상이
> 그만큼 숨을 쉰다. 4단계 문서의 "위 5개를 고치면 나머지 14개는 따라온다"가 작동하는 방식이다.

## 확정된 결정

사용자가 고른 것 두 가지다.

| 항목 | 결정 | 갈린 대안 |
|---|---|---|
| 갱신 주체 | **Spring `<task:scheduled>`** | MySQL `EVENT` |
| 갱신 주기 | **5분** | 1시간 · 매일 |

### 왜 Spring 스케줄러인가

MySQL `EVENT`는 앱과 무관하게 돌고 인스턴스가 늘어도 중복이 없다는 장점이 있다. 그런데
**집계 로직이 자바가 아니라 SQL 안에 숨어 테스트와 관측이 어렵고**, RDS 파라미터 그룹에
`event_scheduler`를 켜야 한다(저장소 밖 설정이 하나 늘어난다).

Spring 스케줄러의 유일한 약점이었던 **다중 인스턴스 중복 실행은 지금 해당하지 않는다** —
EB가 단일 인스턴스 `t4g.micro`다. 저장소 코드에 보이고, 앱 로그에 남고, 단위 테스트가 된다.

### 왜 5분인가

집계가 135ms짜리라 **하루로 묶을 이유가 없다.** 5분이면 신선도 지연이 최대 5분인데
DB 부하는 0.045%다. "낡은 데이터가 나간다"는 반론이 거의 사라진다.

## 설계

### 1. 테이블 `popular_keyword`

```sql
CREATE TABLE popular_keyword (
    `rank`        TINYINT      NOT NULL,
    keyword       VARCHAR(100) NOT NULL,
    search_count  INT          NOT NULL,
    aggregated_at DATETIME     NOT NULL,
    PRIMARY KEY (`rank`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- **`rank`가 PK다.** 항상 1~5뿐이라 별도 대리키가 의미 없고, PK로 두면 조회가
  `Index scan on PRIMARY` 5행으로 끝난다. `rank`는 MySQL 8.0.2+ 예약어라 백틱이 필요하다
- **`aggregated_at`은 응답에 내보내지 않는다.** 계약(`PopularKeywordsResponse`)을 바꾸지 않기
  위해서다. 이 컬럼의 용도는 "지금 나가는 값이 언제 만들어졌나"를 사람이 확인하는 것 하나다
- `period_days`를 컬럼으로 두지 않는다. 집계 기간은 서비스 정책값이고 이미
  `DefaultStoreService.POPULAR_PERIOD_DAYS`가 소유한다 — 여기 복사하면 정본이 둘이 된다

### 2. 갱신 — `DELETE` + `INSERT`를 한 트랜잭션에

```java
@Scheduled(fixedDelay = 300_000L, initialDelay = 0L)
@Transactional
public void refreshPopularKeywords() { ... }
```

**`REPLACE INTO`가 아니라 `DELETE` 후 `INSERT`인 이유**는, 7일 창 안 고유 키워드가 5개
미만으로 줄었을 때 `REPLACE`가 옛 4·5위 행을 남기기 때문이다. 지운 뒤 넣어야 항상
"지금의 상위 N개"만 남는다.

**읽는 쪽이 중간의 0행 상태를 보지 않는다.** InnoDB 기본 격리수준
`REPEATABLE READ` + MVCC라, 갱신 트랜잭션이 커밋되기 전까지 조회는 이전 5행 스냅샷을 본다.

**`initialDelay = 0`이라 기동 직후 한 번 돈다.** 이게 없으면 첫 배포 후 5분간
`popular`이 빈 배열로 나간다. Flyway가 `sqlSessionFactory`보다 먼저 뜨므로(root-context.xml의
`depends-on`) 스케줄러가 처음 돌 때 테이블은 이미 존재한다.

### 3. 조회 — 매퍼 메서드 이름을 유지한다

`StoreMapper.findPopularKeywords()`의 SQL만 `popular_keyword`를 읽도록 바꾼다.
**서비스·DTO·컨트롤러는 손대지 않는다.** 응답 JSON도 동일하다.

### 4. 배선

`root-context.xml`에 `task` 네임스페이스와 `<task:annotation-driven/>`을 추가한다.
서비스는 루트 컨텍스트에서 스캔되므로(웹 컨텍스트가 아니라) `@Transactional`이 걸리는
자리와 같다(AGENTS.md §9).

## 손대는 파일

| 파일 | 변경 |
|---|---|
| `db/migration/V13__popular_keyword_table.sql` | 테이블 생성 (멱등) |
| `root-context.xml` | `task` 네임스페이스 + `<task:annotation-driven/>` |
| `mapper/store/StoreMapper.xml` | `findPopularKeywords` SQL 교체, `deletePopularKeywords`·`insertPopularKeywords` 추가 |
| `store/mapper/StoreMapper.java` | 매퍼 메서드 2개 선언 |
| `store/service/StoreService.java` | `refreshPopularKeywords()` 선언 |
| `store/service/DefaultStoreService.java` | 구현 + `@Scheduled` |
| `store/service/DefaultStoreServiceTest.java` | 갱신 단위 테스트 |
| `store/mapper/StoreMapperIntegrationTest.java` | popular 검증 5건을 집계 INSERT 쪽으로 이동 |

### 검토했지만 하지 않은 것 — 별도 `PopularKeywordService`

`SearchHistoryService`가 트랜잭션 전파(`REQUIRES_NEW`) 때문에 분리된 전례가 있어
같은 방식으로 나누는 안을 검토했다. **하지 않는다** — 여기서는 트랜잭션 의미가 다르지 않고
(그냥 쓰기 트랜잭션이다), 분리해야 할 만큼 로직이 크지도 않다. 분리의 근거가
"파일이 늘어나서"뿐이면 나누지 않는다.

## ⚠️ 이 변경이 실제로 바꾸는 것

### ① 문서화된 동작 하나가 바뀐다 — 명세를 함께 고쳐야 한다

노션 「최근 검색어 전체 삭제」·「최근 검색어 개별 삭제」 명세에 이렇게 적혀 있다.

> 인기 검색어 영향: popular 집계가 같은 테이블을 읽으므로 지운 행만큼 집계에서도 빠집니다.

**"즉시 반영"에서 "최대 5분 지연"으로 바뀐다.** 사용자가 자기 검색 이력을 지워도 인기
검색어에서 빠지는 데 최대 5분이 걸린다. 이 문구를 고치지 않으면 명세가 코드와 어긋난다.

### ② 기존 popular 통합 테스트 5건의 검증 지점이 이동한다

지금 `StoreMapperIntegrationTest`의 다섯 건은 `search_history`를 직접 집계한 결과를 검증한다.

- `인기_검색어_집계는_7일보다_오래된_기록을_제외한다`
- `인기_검색어_순위는_1부터_순서대로_매겨진다`
- `인기_검색어가_동점이면_최근에_검색된_키워드가_앞선다`
- `인기_검색어는_최대_5건이다`
- `인기_검색어는_search_count_내림차순이다`

조회가 `popular_keyword`를 읽게 되면 이 검증들은 조회로는 확인할 수 없다.
**지우지 말고 집계 INSERT(`insertPopularKeywords`) 쪽으로 옮긴다** — 안 그러면 집계 규칙의
회귀 가드가 통째로 사라진다. 이게 이번 작업에서 가장 조용히 망가지기 쉬운 지점이다.

### ③ 조용히 틀릴 수 있게 된다

지금 쿼리는 느릴지언정 **틀릴 수가 없다.** 요약 테이블은 갱신이 멈추면 아무도 모르는 채로
낡은 값이 나간다. 그래서 `aggregated_at`을 두고, 갱신 성공/실패를 로그로 남긴다.

`@Scheduled` 메서드에서 예외가 나도 Spring의 기본 `ErrorHandler`가 로그를 남기고 삼키므로
다음 주기가 취소되지는 않는다. 그래도 **명시적으로 잡아 `log.error`를 남긴다** — 의도가
드러나고, 메시지에 맥락(마지막 성공 시각 등)을 담을 수 있다.

### ④ 단일 인스턴스 전제다

인스턴스가 2대가 되면 5분마다 두 번 집계한다. 결과가 같아 무해하지만 낭비이고,
동시에 돌면 한쪽이 잠깐 락을 기다린다. **인스턴스를 늘릴 때 이 주석을 다시 읽어야 한다.**

## 완료 조건

- `GET /api/store/keywords` 응답 JSON이 이전과 동일하다
- 운영 p50이 **100ms 아래**로 내려온다 (k6 1 VU · N=30으로 확인, 정식 판정은 N=200)
- 집계 규칙 5건의 검증이 `insertPopularKeywords` 쪽에 살아 있다
- 앱 기동 직후 `popular_keyword`에 5행이 채워진다
- 노션 「최근 검색어 전체/개별 삭제」 명세의 "즉시 반영" 문구가 수정된다

## 작업 순서

성능 작업이므로 `main`에서 분기하고 PR base도 `main`이다(AGENTS.md §14).
**`main` 머지 = 즉시 운영 배포**다.

1. 이슈 등록
2. `main`에서 `perf/popular-keyword-summary-table` 분기
3. `V13` → `root-context.xml` → 매퍼 → 서비스 → 테스트
4. `./gradlew build`
5. 커밋 · push · PR (base `main`)
6. 배포 후 k6 1 VU · N=30 재측정
7. 노션 7번 페이지와 검색어 삭제 명세 갱신
