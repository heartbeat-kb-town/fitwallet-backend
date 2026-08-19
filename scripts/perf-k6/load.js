/**
 * 4단계 — 1차 부하 테스트 (동시 사용자 100명)
 *
 * 3단계 baseline이 "병목이 어디고 왜인가"를 규명했다면, 이 스크립트는
 * **"우리 규모에서 서비스 가능한가"**만 판정한다. 병목을 다시 찾지 않는다.
 *
 * 실행:
 *   k6 run scripts/perf-k6/load.js
 *
 * 산출물: stdout 마크다운 표 + load-summary.md / load-summary.json
 *
 * ──────────────────────────────────────────────────────────────────────────
 * 왜 동시 사용자 100명인가
 *
 * 계획 문서의 "300 TPS"는 근거가 없다 — 멘토 한 마디가 전부고 우리 역산은 약 15 TPS다.
 * TPS로는 유도가 안 되지만 동시 사용자 수는 깔끔하게 나온다.
 *
 *   DAU 5,000명 × 피크 시간대 방문 20% = 피크 1시간에 1,000명
 *   1,000명 × (세션 3분 ÷ 60분)        = 동시 사용자 약 50명
 *
 * 측정은 그 2배인 100명에서 한다. 50명은 커넥션 풀(20)에 여유가 있어(11개 필요) 경합이
 * 거의 없고 baseline과 비슷한 수치가 나온다. 100명은 22개가 필요해 **풀 상한을 넘는다** —
 * 부하 때문에 새로 실패하는 항목이 여기서 드러난다.
 *
 * 대가: 판정 문장이 "우리 규모에서 되는가"가 아니라 "우리 규모의 2배에서 되는가"가 된다.
 * ──────────────────────────────────────────────────────────────────────────
 * 왜 VU 고정인가 (도착률 고정이 아니라)
 *
 * 도착률 고정(ramping-arrival-rate)은 "N TPS를 견디나"를 직접 답하지만, 목표 TPS가 있어야
 * 의미가 있다. 그 목표를 버렸으므로 장점이 사라진다. 게다가 예상 용량(88 TPS)의 3.4배인
 * 300 TPS를 밀어넣으면 대기열이 무한히 자라 "완전히 무너진 상태"만 오래 재게 된다.
 *
 * ⚠️ VU 고정의 알려진 한계 — coordinated omission. 서버가 느려지면 VU가 응답을 기다리느라
 * 요청을 덜 보내서 **느린 응답이 지연시간 분포에 실제보다 적게 실린다.** 그래서 p95를
 * 항상 달성 처리량과 함께 읽는다 — 97 TPS에 크게 못 미치면 p95는 낙관적인 값이다.
 * ──────────────────────────────────────────────────────────────────────────
 */

import http from 'k6/http';
import exec from 'k6/execution';
import { sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://fitwallet-backend-prod.ap-northeast-2.elasticbeanstalk.com')
    .replace(/\/$/, '');

const VUS = Number(__ENV.VUS || 100);
const RAMP = __ENV.RAMP || '1m';
const DURATION = __ENV.DURATION || '5m';
/** 판정 구간 초. 달성 처리량을 직접 계산할 때 쓴다 (k6 요약은 전체 구간 평균이라 램프에 희석된다). */
const DURATION_SEC = Number(__ENV.DURATION_SEC || 300);

/**
 * 화면 사이 대기(초). 이 값이 없으면 VU 1명이 쉬지 않고 요청을 쏴서 실제 사용자
 * 여러 명분이 되고, **VU 수와 동시 사용자 수가 어긋나** 위 역산과 연결되지 않는다.
 */
const THINK = Number(__ENV.THINK || 3);

const USER_FILE = __ENV.USER_FILE || './active-users.csv';
const PASSWORD = __ENV.PERF_PASSWORD || '11112222';
const YEAR_MONTH = __ENV.YEAR_MONTH || '2026-07';

/** setup()의 폴백 프로브 전용 좌표(강남역). 세션 좌표는 scenarios-load.csv에서 온다. */
const LAT = 37.4979;
const LNG = 127.0276;

/*
 * SLO. 측정값으로 조정하지 않는다.
 *
 * ⚠️ **정본은 노션 「3. SLI/SLO 확정」이고 값은 전 엔드포인트 공통 p95 100ms다.**
 * 여기 있던 200/300/500 3단은 계획 초안 §3의 표에서 온 것인데 확정 단계에서 폐기됐다.
 * 그대로 두면 **하네스가 정본보다 느슨한 기준으로 판정한다** — 실제로 개선 후 100 VU에서
 * 18/19 합격으로 찍혔지만 정본 기준으로는 17/19다(`report_summary` p95 145ms가 3단 기준
 * SLO_AGG 500ms에는 걸리지 않는다). 에러 없이 합격 수만 부풀어 오르는 종류라 눈에 안 띈다.
 *
 * 노션 문서에 "100ms는 잡기 나름, 해보고 비현실적이면 올리기"라고 적혀 있으므로 이 값이
 * 나중에 바뀔 수는 있다. **바뀌면 노션을 먼저 고치고 여기를 따라 고친다.**
 */
const SLO_P95 = Number(__ENV.SLO_P95 || 100);
const SLO_SIMPLE = SLO_P95;
const SLO_SEARCH = SLO_P95;
const SLO_AGG = SLO_P95;

/**
 * 활성 유저 목록. extract-active-users.sh가 만든다.
 * SharedArray는 VU 전체가 메모리 한 벌을 공유한다 — VU마다 복사하면 100배가 된다.
 */
const users = new SharedArray('active users', function () {
    return open(USER_FILE).split('\n').map((s) => s.trim()).filter(Boolean);
});

const SCENARIO_FILE = __ENV.SCENARIO_FILE || './scenarios-load.csv';

/**
 * 시나리오 조합. 좌표·검색어·카테고리가 여기서 온다 — 예전에는 전부 상수였다.
 * SharedArray라 VU 전체가 메모리 한 벌을 공유한다.
 */
const scenarios = new SharedArray('scenarios', function () {
    const lines = open(SCENARIO_FILE).split('\n').map((s) => s.trim()).filter(Boolean);
    return lines.slice(1).map(function (line) {
        const c = line.split(',');
        return { lat: c[0], lng: c[1], tier: Number(c[2]), keyword: c[3], categoryId: c[4] };
    });
});

/** 밀도 단계(0~7)를 태그 버킷으로 줄인다. 태그 카디널리티를 낮게 유지한다. */
function densityBucket(tier) {
    if (tier === 0) return 'empty';   // 1km 안 0건 — 사다리를 10km까지 올리는 유일한 경로
    if (tier <= 2) return 'sparse';
    if (tier <= 5) return 'mid';
    return 'dense';
}

/**
 * 세션마다 조합을 갈아 끼운다. **난수를 쓰지 않는다** — 개선 전과 후가 같은 순서를 밟는 것이
 * 비교의 전제이고, 결정적 순회면 시드를 맞췄는지 확인할 필요조차 없다.
 *
 * VU 100개가 매 반복 서로 다른 100칸씩 전진한다. 2,000행이면 20반복까지 겹침이 없고
 * 판정 구간은 약 15반복이라 한 번도 재사용되지 않는다.
 *
 * 워밍업에 N/2 오프셋을 주는 이유: 같은 조합을 밟으면 판정 구간이 자기가 쓸 페이지를
 * 미리 캐시에 올린 상태로 시작한다.
 *
 * ⚠️ **반복 번호는 `exec.vu.iterationInScenario`다.** `exec.scenario.iterationInInstance`는
 * VU별이 아니라 **시나리오 전역 카운터**라(k6 v2.2.0 실측: 4 VU × 6반복에서 0~23으로 흐른다)
 * 그걸 쓰면 전진 폭이 VU의 진행이 아니라 *다른 VU가 그 사이 몇 번 돌았는지*에 좌우된다.
 * 결과는 둘 다 조용하다 — 에러가 없고 표도 정상으로 보인다:
 *   ① 순회가 타이밍 의존이 되어 **위 "같은 순서를 밟는다"는 전제가 깨진다.** 5xx로 빨리
 *      죽는 개선 전과 정상인 개선 후가 서로 다른 행 부분집합을 본다
 *   ② 같은 행을 재방문해 버퍼 풀이 데워진 채 다시 측정된다 — 실측(1/10 스케일 재현)으로
 *      2,000행 중 1,301행(65%)만 방문했고, 올바른 카운터에서는 1,998행(99.9%)이었다
 */
function pickScenario(isSteady) {
    const offset = isSteady ? 0 : Math.floor(scenarios.length / 2);
    const i = (offset + (__VU - 1) + exec.vu.iterationInScenario * VUS) % scenarios.length;
    return scenarios[i];
}

// ── 여정 정의 ──────────────────────────────────────────────────────────────
// 화면 그룹 사이에만 think time을 넣으므로 평탄한 배열이 아니라 화면 단위로 중첩한다.
// name은 k6 태그로 쓰이고 정적이어야 한다(URL에 ID가 박힌 요청들이 따로 집계되지 않게).

const SCREENS = [
    {
        screen: '홈',
        calls: [
            { name: 'user_me',              slo: SLO_SIMPLE, url: () => '/api/user/me' },
            { name: 'user_cards',           slo: SLO_SIMPLE, url: () => '/api/user-cards' },
            // 5단계 개선 2순위 — 상관관계 없는 파생 테이블로 payment_transaction 전체를 집계한다
            { name: 'user_cards_recent',    slo: SLO_SIMPLE, url: () => '/api/user-cards?sort=RECENTLY_USED' },
            { name: 'report_summary',       slo: SLO_AGG,    url: () => `/api/report/benefit/summary?yearMonth=${YEAR_MONTH}` },
            { name: 'user_frequent_places', slo: SLO_SIMPLE, url: () => '/api/user/frequent-places' },
        ],
    },
    {
        screen: '혜택',
        calls: [
            { name: 'report_missed_app',    slo: SLO_AGG, url: () => `/api/report/benefit/missed?yearMonth=${YEAR_MONTH}&lossType=APP_UNUSED` },
            { name: 'report_missed_card',   slo: SLO_AGG, url: () => `/api/report/benefit/missed?yearMonth=${YEAR_MONTH}&lossType=CARD_MISMATCH` },
            { name: 'report_card_received', slo: SLO_AGG, url: (u) => `/api/report/benefit/received/cards/${u.userCardId}?yearMonth=${YEAR_MONTH}` },
        ],
    },
    {
        screen: '카드',
        calls: [
            { name: 'card_summary',      slo: SLO_AGG, url: (u) => `/api/card/${u.userCardId}/summary` },
            { name: 'card_event',        slo: SLO_AGG, url: (u) => `/api/card/${u.userCardId}/event` },
            { name: 'card_benefit',      slo: SLO_AGG, url: (u) => `/api/card/${u.userCardId}/benefit` },
            { name: 'card_transactions', slo: SLO_AGG, url: (u) => `/api/card/${u.userCardId}/transactions?yearMonth=${YEAR_MONTH}` },
            { name: 'card_usage',        slo: SLO_AGG, url: (u) => `/api/card/${u.userCardId}/usage?yearMonth=${YEAR_MONTH}` },
        ],
    },
    {
        screen: '검색',
        calls: [
            { name: 'store_keywords',        slo: SLO_SEARCH, url: () => '/api/store/keywords' },
            { name: 'store_search_coords',   slo: SLO_SEARCH, geo: true,
              url: (u, sc) => `/api/store/search?latitude=${sc.lat}&longitude=${sc.lng}&radiusMeters=3000` },
            { name: 'store_search_keyword',  slo: SLO_SEARCH, geo: true,
              url: (u, sc) => `/api/store/search?keyword=${encodeURIComponent(sc.keyword)}&latitude=${sc.lat}&longitude=${sc.lng}` },
            { name: 'store_search_category', slo: SLO_SEARCH, geo: true,
              url: (u, sc) => `/api/store/search?categoryId=${sc.categoryId}&latitude=${sc.lat}&longitude=${sc.lng}&radiusMeters=3000` },
        ],
    },
    {
        screen: '결제 전',
        calls: [
            // storeId는 같은 세션의 좌표 검색 응답에서 이어받는다(fire()가 채운다).
            // 사용자의 실제 동선(검색 → 가맹점 선택 → 결제 전 혜택 조회)과 인과가 맞는다.
            { name: 'benefit_expected', slo: SLO_AGG,
              url: (u, sc) => `/api/benefit/expected?storeId=${sc.storeId || u.storeId}&amount=15000` },
        ],
    },
    {
        screen: '기타',
        calls: [
            { name: 'health_db', slo: SLO_SIMPLE, auth: false, url: () => '/health/db' },
        ],
    },
];

const ALL_CALLS = SCREENS.flatMap((s) => s.calls);

// 판정 구간의 요청 수를 직접 센다. k6 요약의 http_reqs.rate는 램프업까지 포함한
// 전체 구간 평균이라 판정 구간 처리량보다 낮게 나온다.
const steadyReqs = new Counter('steady_reqs');
/** 500 응답. 커넥션 풀 고갈(connectionTimeout=3000)이 이 형태로 나타난다. */
const serverErrors = new Counter('server_errors');
/**
 * status가 0인 실패 — 커넥션 거부·리셋·타임아웃. **5xx와 합치지 않는다.**
 * 5xx는 요청이 톰캣까지 도달했다는 뜻이고 status 0은 거기 닿지도 못했다는 뜻이라 원인이 갈린다.
 */
const transportErrors = new Counter('transport_errors');

// ── 시나리오 ───────────────────────────────────────────────────────────────
/*
 * 워밍업과 판정 구간을 **별도 시나리오로 나눈다.** 한 시나리오로 두면 램프업 동안의
 * 느린 응답이 p95에 섞여 판정이 흐려진다. scenario의 tags는 그 안에서 나온 모든 메트릭에
 * 붙으므로, 임계값을 phase:steady로 좁힐 수 있다.
 *
 * 워밍업이 필요한 이유: 데이터 9.21GB가 버퍼 풀 5.25GB의 1.75배라 캐시가 자리 잡는 데
 * 시간이 걸린다. 3단계까지는 전부 캐시(적중률 99.76%)라 변수가 아니었다.
 */
const thresholds = {
    'http_req_failed{phase:steady}': ['rate<0.01'],
};
/*
 * ⚠️ k6는 임계값에 선언되지 않은 태그 조합의 서브메트릭을 만들지 않는다.
 * handleSummary에서 밀도별 수치를 읽으려면 통과가 보장되는 임계값이라도 걸어야 한다.
 * 빠뜨리면 에러 없이 밀도 표가 통째로 비어 나온다.
 */
const DENSITY_BUCKETS = ['empty', 'sparse', 'mid', 'dense'];
for (const c of ALL_CALLS) {
    thresholds[`http_req_duration{name:${c.name},phase:steady}`] = [`p(95)<${c.slo}`];
    if (c.geo) {
        for (const b of DENSITY_BUCKETS) {
            thresholds[`http_req_duration{name:${c.name},phase:steady,density:${b}}`] = ['p(95)<999999'];
        }
    }
}

export const options = {
    scenarios: {
        warmup: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [{ target: VUS, duration: RAMP }],
            gracefulRampDown: '0s',
            /*
             * ⚠️ gracefulRampDown만으로는 안 끊긴다. 그건 스테이지 사이 램프다운용이고
             * **시나리오 종료에는 gracefulStop(기본 30초)이 적용된다.** 빼고 돌리면 판정 구간이
             * 시작된 뒤에도 워밍업 VU가 최대 30초 더 돈다 — 여정 1회가 19.4초라 사실상 전부
             * 해당하고, 초반 부하가 최대 2배(200 VU)가 된다. 그 요청 자체는 phase:warmup이라
             * 표에서 빠지지만 **만들어내는 경합은 phase:steady 수치에 그대로 실린다.**
             *
             * steady에는 같은 설정을 걸지 않는다(README 함정 4) — 거기서 잘린 요청은
             * 판정 메트릭에 실려 에러율 SLO를 잠식한다. warmup은 잘려도 태그가 phase:warmup이라
             * 판정에 영향이 없어 대가 없이 얻는다.
             *
             * 부수 효과: 워밍업 VU가 제때 반납되지 않으면 steady의 __VU 번호가 1..N이 아니라
             * 흩어진 구간으로 잡혀(실측 84~200) default()의 모듈로 매핑이 깨진다 —
             * 일부 유저가 두 VU에 중복 배정되고 일부 CSV 유저는 아예 안 쓰인다.
             */
            gracefulStop: '0s',
            tags: { phase: 'warmup' },
        },
        steady: {
            executor: 'constant-vus',
            vus: VUS,
            duration: DURATION,
            startTime: RAMP,
            tags: { phase: 'steady' },
        },
    },
    thresholds,
    // setup()이 VU 수만큼 로그인하고 카드를 조회한다. 100명이면 1분 가까이 걸려
    // 기본값 60초로는 모자란다.
    setupTimeout: '5m',
    // 'count'가 빠지면 Trend의 values에 count가 안 담겨 buildDensityTable의 !t.values.count가
    // 항상 참이 된다 — 에러 없이 밀도 표가 통째로 빈다(2026-08-19 스모크에서 실측).
    summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'max', 'count'],
    // 응답 본문을 남긴다. 500이 났을 때 CannotGetJdbcConnection인지 봐야
    // 커넥션 풀 고갈을 확인할 수 있다(§설계 5). 응답이 작아 메모리 부담이 없다.
    discardResponseBodies: false,
};

function authHeaders(token) {
    return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
}

function login(loginId) {
    const res = http.post(`${BASE_URL}/api/user/login`,
        JSON.stringify({ loginId, password: PASSWORD }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'setup_login' } });

    if (res.status !== 200) {
        throw new Error(`로그인 실패 (${loginId}): HTTP ${res.status} — ${res.body}`);
    }
    return res.json('data.accessToken');
}

export function setup() {
    if (users.length < VUS) {
        throw new Error(
            `활성 유저가 ${users.length}명뿐인데 VU는 ${VUS}개다. `
            + 'extract-active-users.sh를 LIMIT을 늘려 다시 돌려라. '
            + '유저가 모자라면 같은 유저를 여러 VU가 공유하게 되어 버퍼 풀 적중률이 왜곡된다.');
    }

    // 가맹점 ID는 전 VU가 같은 값을 쓴다. 첫 유저의 토큰으로 한 번만 뽑는다.
    const probeToken = login(users[0]);
    const storeRes = http.get(
        `${BASE_URL}/api/store/search?latitude=${LAT}&longitude=${LNG}&radiusMeters=3000`,
        { headers: authHeaders(probeToken) });
    const stores = storeRes.json('data.stores') || [];
    if (stores.length === 0) {
        throw new Error(
            '폴백 프로브 좌표(강남역)의 검색이 0건이다. 세션 좌표는 희소해도 정상이지만 '
            + '이 프로브는 benefit_expected의 폴백 storeId를 만드는 자리라 비면 안 된다.');
    }
    const storeId = stores[0].storeId;

    const pool = [];
    for (let i = 0; i < VUS; i++) {
        const loginId = users[i];
        const token = i === 0 ? probeToken : login(loginId);

        /*
         * ⚠️ 활성 유저라도 보유 카드 전부에 거래가 있는 건 아니다. 월 75건이 카드 3.9장에
         * 흩어지므로 cards[0]을 그냥 쓰면 카드 계열이 0행을 잰다(3단계 함정 3).
         * **그 달 거래가 가장 많은 카드**를 고른다 — 설계 §3이 정한 규칙이고
         * `baseline.js`도 같은 규칙이라, 이래야 3단계 표와 4단계 표가 같은 데이터 양을 잰다.
         *
         * 카드마다 한 번씩 조회하는 게 비싸 보이지만 실제로는 setup이 **빨라진다.**
         * 유저당 3.9회 × 13ms ≈ 50ms가 늘고, 대신 전 구간을 집계하던
         * `user-cards?sort=RECENTLY_USED`(379ms)를 여기서 안 써도 되기 때문이다.
         * (그 엔드포인트는 여정에 그대로 남아 있으므로 측정 대상에서 빠지지 않는다.)
         *
         * size=100은 선정 정확도용이다. 기본 페이지 크기에서 여러 카드가 상한에 몰려
         * 동률이 되면 "최다"가 사실상 임의 선택이 된다.
         */
        const cardsRes = http.get(`${BASE_URL}/api/user-cards`, { headers: authHeaders(token) });
        const cards = cardsRes.json('data') || [];
        if (cards.length === 0) {
            throw new Error(`${loginId}에 보유 카드가 없다. CSV 추출 조건을 확인해라.`);
        }

        let bestCard = cards[0];
        let bestCount = -1;
        for (const c of cards) {
            const r = http.get(
                `${BASE_URL}/api/card/${c.userCardId}/transactions?yearMonth=${YEAR_MONTH}&size=100`,
                { headers: authHeaders(token) });
            const n = (r.json('data.transactions.content') || []).length;
            if (n > bestCount) {
                bestCount = n;
                bestCard = c;
            }
        }
        if (bestCount <= 0) {
            throw new Error(
                `${loginId}의 보유 카드 ${cards.length}장 어디에도 ${YEAR_MONTH} 거래가 없다. `
                + 'CSV를 뽑은 DB와 측정 대상 DB가 다르거나, extract-active-users.sh가 '
                + 'is_deleted=1인 카드의 거래까지 세어 이 유저를 골랐을 수 있다.');
        }

        pool.push({ loginId, token, userCardId: bestCard.userCardId, cardTxCount: bestCount, storeId });
    }

    /*
     * 표본 검증 — 앞 5명이 실제로 그 달 거래를 갖고 있는지 본다.
     *
     * 이 검사가 없으면 CSV가 잘못돼도 테스트가 초록불로 끝난다. 0행 응답도 HTTP 200이라
     * 에러율 0%가 나오고, 집계 API가 20ms에 답한 값이 표에 그대로 실린다. 표만 보면
     * "리포트는 부하에서도 빠르다"로 읽혀 완전히 틀린 결론이 나간다.
     */
    const sample = Math.min(5, pool.length);
    for (let i = 0; i < sample; i++) {
        const u = pool[i];
        const r = http.get(`${BASE_URL}/api/report/benefit/summary?yearMonth=${YEAR_MONTH}`,
            { headers: authHeaders(u.token) });
        const categories = r.json('data.categories') || [];
        const received = Number(r.json('data.totalReceivedBenefit') || 0);
        if (categories.length === 0 && received === 0) {
            throw new Error(
                `${u.loginId}는 ${YEAR_MONTH}에 거래가 없다(리포트 요약이 비어 있다). `
                + 'CSV를 뽑은 DB와 측정 대상 DB가 다르거나, YEAR_MONTH가 어긋났다. '
                + '앱 시계 고정값 clock.fixed-date의 EB 환경 속성 실제 값을 확인해라.');
        }
    }

    /*
     * 측정 카드가 실제로 몇 행을 재는지 리포트에 남긴다. 카드 계열이 빠르게 나왔을 때
     * "정말 빠른 것"과 "0행을 잰 것"을 사후에 가를 수 있는 유일한 근거다.
     */
    const txCounts = pool.map((u) => u.cardTxCount).sort((a, b) => a - b);
    const minTx = txCounts[0];
    const medTx = txCounts[Math.floor(txCounts.length / 2)];
    const maxTx = txCounts[txCounts.length - 1];

    console.log(`[setup] BASE_URL=${BASE_URL}`);
    console.log(`[setup] 유저 풀 ${pool.length}명 (CSV ${users.length}명 중), storeId=${storeId}`);
    console.log(`[setup] yearMonth=${YEAR_MONTH}, think time=${THINK}초, VU=${VUS}`);
    console.log(`[setup] 표본 ${sample}명 리포트 검증 통과`);
    console.log(`[setup] 측정 카드의 ${YEAR_MONTH} 거래 건수 — `
        + `최소 ${minTx} / 중앙 ${medTx} / 최대 ${maxTx} (size=100 상한)`);
    return { pool };
}

/**
 * 에러 본문 로그 상한. 캡이 없으면 100 VU가 전부 실패할 때 로그가 수만 줄이 된다.
 *
 * ⚠️ **VU당 상한이지 전체 상한이 아니다.** k6는 VU마다 격리된 JS 런타임을 쓰므로 이 변수는
 * VU 로컬이고 실제 최대는 20 × VU 수다(100 VU면 2,000줄). k6에는 VU 간 공유 가변 상태가
 * 없어(`SharedArray`는 읽기 전용, 메트릭은 런타임에 읽을 수 없다) 전역 캡은 구현할 방법이 없다.
 * 목적인 로그 폭주 방지는 이것으로 달성된다.
 */
const ERROR_LOG_LIMIT = 20;
let errorLogged = 0;

function fire(call, u, sc, isSteady) {
    const headers = call.auth === false
        ? {}
        : { Authorization: `Bearer ${u.token}` };

    // tags.name을 고정해야 URL에 userCardId가 박힌 요청들이 따로 집계되지 않는다.
    const tags = { name: call.name };
    if (call.geo) tags.density = densityBucket(sc.tier);

    const res = http.get(`${BASE_URL}${call.url(u, sc)}`, {
        headers,
        tags,
        timeout: '60s',
    });

    // 좌표 검색이 성공하면 그 결과의 첫 가맹점을 세션에 담는다. 뒤따르는 benefit_expected가 쓴다.
    // 0건이면 손대지 않아 setup()의 전역 폴백이 그대로 남는다 — 희소 좌표에서는 0건이 정상이다.
    if (call.name === 'store_search_coords' && res.status === 200) {
        const stores = res.json('data.stores') || [];
        if (stores.length > 0) sc.storeId = stores[0].storeId;
    }

    if (isSteady) steadyReqs.add(1);

    /*
     * ⚠️ status 0을 반드시 함께 센다. 5xx만 세면 커넥션 리셋·타임아웃이 리포트에서 통째로
     * 사라지는데, k6는 그 실패도 http_req_duration에 표본을 남긴다 — 커넥션 에러는 0ms,
     * 타임아웃은 타임아웃 시각으로. 즉 **실패가 늘수록 p50/p95가 빨라진다.**
     * (실측: 50ms 응답 20건에 실패 25건을 섞으면 med 53.9ms → 10.8ms)
     * 커넥션 풀이 고갈돼도 accept 큐가 밀리거나 EB가 끊으면 500이 아니라 이 형태로 나온다.
     */
    if (res.status === 0 || res.status >= 500) {
        if (res.status === 0) {
            transportErrors.add(1, { name: call.name });
        } else {
            serverErrors.add(1, { name: call.name });
        }
        /*
         * 본문을 남기는 이유 — 커넥션 풀(20)이 고갈되면 connectionTimeout=3000에 걸려
         * CannotGetJdbcConnection 계열 500이 난다. 응답이 느려지는 것이 아니라 에러가
         * 나는 형태라, 본문을 봐야 원인이 풀인지 다른 것인지 갈린다(설계 §5).
         * status 0은 본문이 없으므로 대신 k6의 error_code를 남긴다.
         */
        if (errorLogged < ERROR_LOG_LIMIT) {
            errorLogged++;
            const detail = res.status === 0
                ? `error_code=${res.error_code} ${res.error}`
                : String(res.body).slice(0, 300);
            console.warn(`[${call.name}] HTTP ${res.status} — ${detail}`);
        }
    }
}

export default function (data) {
    // __VU는 1부터. 시나리오가 둘이라 VU 번호가 겹칠 수 있어 모듈로로 감싼다.
    const u = data.pool[(__VU - 1) % data.pool.length];
    const isSteady = exec.scenario.name === 'steady';
    // 조합은 세션당 하나다. 한 세션 안에서 좌표가 바뀌면 사용자가 순간이동하고
    // storeId 인과가 깨진다. SharedArray 원소는 읽기 전용이라 복사해서 쓴다.
    const base = pickScenario(isSteady);
    const sc = { lat: base.lat, lng: base.lng, tier: base.tier,
                 keyword: base.keyword, categoryId: base.categoryId, storeId: null };

    for (let i = 0; i < SCREENS.length; i++) {
        for (const call of SCREENS[i].calls) {
            fire(call, u, sc, isSteady);
        }
        /*
         * 마지막 화면 뒤에는 대기하지 않는다 — 세션이 거기서 끝나기 때문이다.
         *
         * 고정 3초가 아니라 1.5~4.5초로 흩뿌린다. 고정하면 VU 100개가 같은 박자로
         * 묶여 파도처럼 몰려가고, 서버가 보는 순간 부하가 실제보다 뾰족해진다.
         * 평균은 그대로 3초라 세션 길이 19.6초 가정이 유지된다.
         */
        if (i < SCREENS.length - 1) {
            sleep(THINK * (0.5 + Math.random()));
        }
    }
}

// ── 결과 표 ────────────────────────────────────────────────────────────────

function ms(v) {
    if (v === undefined || v === null || Number.isNaN(v)) return '—';
    return v >= 1000 ? `**${(v / 1000).toFixed(2)}s**` : `${v.toFixed(0)}ms`;
}

function buildRows(data) {
    const rows = [];
    for (const c of ALL_CALLS) {
        // 판정 구간만 본다. 태그 조합 키는 k6가 서브메트릭으로 만들어 준다.
        const t = data.metrics[`http_req_duration{name:${c.name},phase:steady}`];
        if (!t) continue;
        rows.push({
            name: c.name,
            slo: c.slo,
            med: t.values.med,
            p95: t.values['p(95)'],
            max: t.values.max,
            pass: t.values['p(95)'] < c.slo,
        });
    }
    rows.sort((a, b) => b.p95 - a.p95);
    return rows;
}

function buildDensityRows(data) {
    const rows = [];
    for (const c of ALL_CALLS) {
        if (!c.geo) continue;
        for (const b of DENSITY_BUCKETS) {
            const t = data.metrics[`http_req_duration{name:${c.name},phase:steady,density:${b}}`];
            if (!t || !t.values.count) continue;
            rows.push({ name: c.name, density: b, n: t.values.count,
                        med: t.values.med, p95: t.values['p(95)'], max: t.values.max });
        }
    }
    return rows;
}

export function handleSummary(data) {
    const rows = buildRows(data);
    const passed = rows.filter((r) => r.pass).length;

    /*
     * 달성 처리량을 직접 계산한다. data.metrics.http_reqs.values.rate는 램프업까지 포함한
     * 전체 구간 평균이라 판정 구간 처리량보다 낮게 나온다.
     */
    const steadyCount = data.metrics.steady_reqs ? data.metrics.steady_reqs.values.count : 0;
    const achievedTps = steadyCount / DURATION_SEC;
    const expectedTps = VUS * 0.97;   // VU 1명 ≈ 초당 1 요청 (19호출 ÷ 19.6초)
    const errCount = data.metrics.server_errors ? data.metrics.server_errors.values.count : 0;
    const transportCount = data.metrics.transport_errors
        ? data.metrics.transport_errors.values.count : 0;

    /*
     * 실패율은 판정의 절반인데(임계값 rate<0.01) 지금까지 산출물에 없었다. 임계값 위반은
     * k6 stdout과 exit code에만 나오고 load-summary.md에는 안 실려서, 표만 보관하면
     * "5xx 0건 + baseline보다 빠른 p50"으로 읽히는 상태가 만들어진다.
     * 서브메트릭은 options.thresholds가 만들어 주므로 그대로 읽으면 된다.
     */
    const failedMetric = data.metrics['http_req_failed{phase:steady}'];
    const failedRate = failedMetric ? failedMetric.values.rate : 0;

    const failWarning = failedRate > 0 ? [
        `> ⚠️ **실패한 요청이 ${(failedRate * 100).toFixed(2)}% 있어 위 p50/p95는 실제보다 낙관적이다.**`,
        '> k6는 실패 요청도 `http_req_duration`에 표본을 남기는데 커넥션 에러는 0ms,',
        '> 타임아웃은 타임아웃 시각으로 기록된다. 실패율이 5%를 넘으면 p95까지 성공 표본의 낮은',
        '> 분위수를 읽기 시작한다 — **부하에서 표가 빨라졌다면 개선이 아니라 이것을 의심한다.**',
        '> 달성 처리량도 실패 요청을 포함하므로 빠르게 실패할수록 높게 나온다.',
        '',
    ] : [];

    const lines = [
        '| # | 엔드포인트 | p50 | p95 | max | SLO | 판정 |',
        '|---:|---|---:|---:|---:|---:|:---:|',
    ];
    rows.forEach((r, i) => {
        lines.push(`| ${i + 1} | \`${r.name}\` | ${ms(r.med)} | ${ms(r.p95)} | ${ms(r.max)} `
            + `| ${r.slo}ms | ${r.pass ? '✅' : '❌'} |`);
    });

    const md = [
        `# 4단계 — 1차 부하 테스트 (동시 사용자 ${VUS}명)`,
        '',
        `- 대상: \`${BASE_URL}\``,
        `- 부하: VU ${VUS} 고정 · 램프업 ${RAMP}(집계 제외) + 판정 ${DURATION}`,
        `- think time: 평균 ${THINK}초 (${(THINK * 0.5).toFixed(1)}~${(THINK * 1.5).toFixed(1)}초 무작위)`,
        `- 측정 월: \`${YEAR_MONTH}\` · 유저: 활성 ${VUS}명을 VU마다 배정`,
        '',
        `**SLO 충족 ${passed} / ${rows.length}**`,
        '',
        `- 달성 처리량 **${achievedTps.toFixed(1)} TPS** (예상 ${expectedTps.toFixed(0)} TPS, `
            + `달성률 ${((achievedTps / expectedTps) * 100).toFixed(0)}%)`,
        `- **실패율 ${(failedRate * 100).toFixed(2)}%** (SLO 1% 미만) `
            + `— 5xx ${errCount}건 · 커넥션 실패/타임아웃 ${transportCount}건`,
        '',
        ...failWarning,
        '> **달성률이 낮으면 p95를 액면 그대로 믿지 않는다.** VU 고정 방식은 서버가 느려지면',
        '> 요청을 덜 보내서 느린 응답이 분포에 적게 실린다(coordinated omission).',
        '> 달성률 저하 자체가 커넥션 풀 상한(20개)에 걸렸다는 신호이기도 하다 — 설계 §5.',
        '',
        '> **달성 처리량은 실제보다 약 3% 높게 나온다.** k6의 `gracefulStop`(기본 30초)이 판정 구간',
        '> 종료 후 진행 중이던 세션을 완주시키는데, 그 요청도 `phase:steady`로 집계되지만 분모는',
        '> 늘지 않기 때문이다. 편향 방향이 항상 같으므로 **달성률이 100%에 가까워도 실제로는 그보다 낮다.**',
        '',
        '> 1 VU baseline에서 이미 SLO를 어기던 `store_search_coords`와 `user_cards_recent`가',
        '> 여기서도 실패하는 것은 새 정보가 아니다. **부하 때문에 새로 실패한 항목**을 본다.',
        '',
        'p95 내림차순 정렬.',
        '',
        lines.join('\n'),
        '',
        '## 밀도별 분해 (좌표 스코프 3종)',
        '',
        '> `empty`는 1km 안에 매장이 0건인 주입 좌표다(전체의 3%). 표본이 얇아 **p95를 말하지 않고**',
        '> 중앙값과 max로 읽는다. 정밀 판정은 1 VU 층화 표본이 담당한다.',
        '',
        '| 엔드포인트 | 밀도 | N | p50 | p95 | max |',
        '|---|---|---:|---:|---:|---:|',
        ...buildDensityRows(data).map((r) =>
            `| \`${r.name}\` | ${r.density} | ${r.n} | ${ms(r.med)} | ${ms(r.p95)} | ${ms(r.max)} |`),
        '',
    ].join('\n');

    return {
        stdout: `\n${md}\n`,
        'load-summary.md': md,
        'load-summary.json': JSON.stringify({
            baseUrl: BASE_URL, vus: VUS, ramp: RAMP, duration: DURATION,
            yearMonth: YEAR_MONTH, thinkSeconds: THINK,
            achievedTps, expectedTps,
            failedRate, serverErrors: errCount, transportErrors: transportCount,
            passed, total: rows.length, rows,
            densityRows: buildDensityRows(data),
        }, null, 2),
    };
}
