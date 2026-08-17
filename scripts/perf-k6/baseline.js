/**
 * 3단계 — API별 baseline 측정 (부하 없이 1 VU)
 *
 * 목적은 "부하를 거는 것"이 아니라 **경합이 0인 상태의 순수 처리시간**을 재는 것이다.
 * 계획 문서가 3단계를 4단계(부하 테스트)보다 앞에 둔 이유가 이것이다 — 먼저 이 표를 만들어
 * 두어야, 부하를 얹었을 때 늘어난 시간을 경합 탓으로 돌릴 수 있다.
 *
 * 실행:
 *   BASE_URL=http://fitwallet-backend-prod.ap-northeast-2.elasticbeanstalk.com \
 *   k6 run scripts/perf-k6/baseline.js
 *
 * 산출물: stdout 마크다운 표 + baseline-summary.json / baseline-summary.md
 *
 * ──────────────────────────────────────────────────────────────────────────
 * 왜 READ와 WRITE를 나눠 재는가
 *
 * 34개를 한 목록에 넣고 똑같이 N번씩 돌리면 측정이 데이터를 부순다.
 *   - DELETE /store/keywords/recent    → 그 유저의 검색 이력을 통째로 지운다.
 *                                        이후 GET /store/keywords가 빈 결과를 재게 된다
 *   - POST /card                       → UNIQUE(user_id, card_product_id)에 걸려 2회차부터 전부 409
 *   - PATCH /user/payment-pin          → PIN이 바뀌어 이후 결제 계열이 전부 실패한다
 *
 * 그래서 두 계열로 나눈다.
 *   READ  — 반복해도 상태가 변하지 않는다. N=READ_ITERATIONS(기본 200).
 *           **SLO 판정의 정본은 이쪽이다**
 *   WRITE — 반복하면 제약에 걸리거나 데이터를 오염시킨다. N=WRITE_ITERATIONS(기본 3),
 *           그리고 측정 전용 유저에게만 쏜다(§ WRITE_USER)
 *
 * WRITE 수치는 표본이 작아 p95를 신뢰하지 말고 p50과 max만 참고한다. 이건 한계가 아니라
 * 제약이며, 표에 N을 함께 찍어 읽는 쪽이 착각하지 않게 한다.
 * ──────────────────────────────────────────────────────────────────────────
 */

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://fitwallet-backend-prod.ap-northeast-2.elasticbeanstalk.com')
    .replace(/\/$/, '');

/**
 * 측정 반복 횟수. 워밍업 분은 기록하지 않고 따로 돈다.
 *
 * **N=200인 이유** — p95를 말하려면 상위 5% 구간에 표본이 충분히 있어야 한다.
 * N=200이면 상위 5%가 10개다. 처음에 N=30으로 쟀는데 그때는 상위 5%가 1.5개뿐이라
 * p95가 사실상 "두 번째로 큰 값"이었다.
 *
 * **p99는 출력하지 않는다.** N=200이어도 상위 1%가 2개뿐이라 보간으로 지어낸 값이 되고,
 * 실측하면 항상 max 바로 밑에 붙어 max의 그림자 노릇만 한다. 꼬리가 궁금하면 max를 본다.
 */
const WARMUP = Number(__ENV.WARMUP || 10);
const READ_ITERATIONS = Number(__ENV.READ_ITERATIONS || 200);
/** WRITE는 반복하면 제약에 걸리거나 데이터를 오염시켜 늘릴 수 없다. 통계값을 내지 않는다. */
const WRITE_ITERATIONS = Number(__ENV.WRITE_ITERATIONS || 3);

/**
 * 읽기 측정 유저.
 *
 * ⚠️ **활성 유저는 연속 블록이 아니라 가입자 전체에 흩어져 있다.** 가입자 5만 중 60%(3만)만
 * 거래가 있고, 앞번호를 아무거나 고르면 40% 확률로 거래 0건인 유저를 잡는다. 그러면 집계 API가
 * 전부 빈 응답을 20ms에 돌려주면서 **"리포트가 빠르다"는 완전히 틀린 표가 나온다.**
 * 실제로 처음 perf000001로 돌렸다가 report 계열이 22~32ms로 찍혀 이 함정을 밟았다.
 *
 * perf000100은 실측으로 확인한 활성 유저다(2026-08-17 재적재 기준 거래 760건).
 * 기본값을 그걸로 두고, setup()에서 한 번 더 검증한다.
 *
 * ⚠️ **유저당 거래 수는 이제 균등하지 않다.** 로그정규 분포라 활성 유저 안에서도 89 ~ 7,052건으로
 * 벌어진다(평균 904). 어느 유저로 재는지가 리포트·카드 계열 수치를 좌우하므로, 표에 유저를
 * 함께 적지 않으면 다른 측정과 비교할 수 없다.
 */
const READ_USER = __ENV.READ_USER || 'perf000100';
/**
 * 변경 측정 전용 유저. 읽기 유저와 반드시 달라야 READ 표가 오염되지 않는다.
 *
 * ⚠️ **거래가 0건인 유저를 골라야 한다.** POST /api/cards/mydata가 멱등하지 않아 실행마다
 * 카드가 늘어나는데, 활성 유저를 쓰면 그 오염이 측정 대상 데이터에 쌓인다.
 * perf050000은 카드 1장 · 거래 0건이다(2026-08-17 실측).
 *
 * 이전 기본값 perf099999는 가입자를 10만 → 5만으로 줄이면서 **존재하지 않게 됐다.**
 * 로그인이 실패해 setup()에서 죽는다 — 가입자 수를 바꾸면 이 값도 함께 확인한다.
 */
const WRITE_USER = __ENV.WRITE_USER || 'perf050000';
const PASSWORD = __ENV.PERF_PASSWORD || '11112222';
const PIN = __ENV.PERF_PIN || '123456';

/**
 * EB 환경 속성 clock.fixed-date=2026-07-24가 앱 시계를 고정하고 있다.
 * 앱이 말하는 "이번 달"은 2026-07이고, 그 달은 거래 45.9만 건의 온전한 한 달이다.
 * 이 값을 2026-08로 바꾸면 진행 중인 달(19.3만 건)을 봐서 집계 부하가 절반 이하로 가벼워진다.
 */
const YEAR_MONTH = __ENV.YEAR_MONTH || '2026-07';

/** 서울 강남역. 272만 건 중 밀도가 높은 좌표라 거리 계산 후 남는 행이 많다. */
const LAT = 37.4979;
const LNG = 127.0276;

// ── 엔드포인트 정의 ────────────────────────────────────────────────────────
// url/body는 setup()이 실제 ID를 채운 뒤에야 확정되므로 함수로 받는다(ctx = setup 반환값).
// name은 init 컨텍스트에서 Trend를 만들어야 해서 정적이어야 한다.

const READ_ENDPOINTS = [
    { name: 'health_db',              method: 'GET', auth: false, url: () => '/health/db' },

    { name: 'user_me',                method: 'GET', url: () => '/api/user/me' },
    { name: 'user_frequent_places',   method: 'GET', url: () => '/api/user/frequent-places' },

    { name: 'user_cards',             method: 'GET', url: () => '/api/user-cards' },
    { name: 'user_cards_recent',      method: 'GET', url: () => '/api/user-cards?sort=RECENTLY_USED' },
    { name: 'card_summary',           method: 'GET', url: (c) => `/api/card/${c.userCardId}/summary` },
    { name: 'card_event',             method: 'GET', url: (c) => `/api/card/${c.userCardId}/event` },
    { name: 'card_benefit',           method: 'GET', url: (c) => `/api/card/${c.userCardId}/benefit` },
    { name: 'card_transactions',      method: 'GET', url: (c) => `/api/card/${c.userCardId}/transactions?yearMonth=${YEAR_MONTH}` },
    { name: 'card_usage',             method: 'GET', url: (c) => `/api/card/${c.userCardId}/usage?yearMonth=${YEAR_MONTH}` },

    // 병목 후보 ② — 272만 행 전건에 ACOS/RADIANS. 세 갈래를 따로 재야 어느 조합이 터지는지 갈린다.
    { name: 'store_search_coords',    method: 'GET', url: () => `/api/store/search?latitude=${LAT}&longitude=${LNG}&radiusMeters=3000` },
    { name: 'store_search_keyword',   method: 'GET', url: () => `/api/store/search?keyword=%EC%8A%A4%ED%83%80%EB%B2%85%EC%8A%A4&latitude=${LAT}&longitude=${LNG}` },
    { name: 'store_search_category',  method: 'GET', url: () => `/api/store/search?categoryId=1&latitude=${LAT}&longitude=${LNG}&radiusMeters=3000` },
    // 병목 후보 ③ — 요청마다 80만 행을 GROUP BY
    { name: 'store_keywords',         method: 'GET', url: () => '/api/store/keywords' },

    { name: 'benefit_expected',       method: 'GET', url: (c) => `/api/benefit/expected?storeId=${c.storeId}&amount=15000` },

    { name: 'report_summary',         method: 'GET', url: () => `/api/report/benefit/summary?yearMonth=${YEAR_MONTH}` },
    // 병목 후보 ① — DATE_FORMAT(paid_at)이 좌변에 있어 인덱스가 죽는다. 540만 행 풀스캔.
    { name: 'report_missed_app',      method: 'GET', url: () => `/api/report/benefit/missed?yearMonth=${YEAR_MONTH}&lossType=APP_UNUSED` },
    { name: 'report_missed_card',     method: 'GET', url: () => `/api/report/benefit/missed?yearMonth=${YEAR_MONTH}&lossType=CARD_MISMATCH` },
    { name: 'report_card_received',   method: 'GET', url: (c) => `/api/report/benefit/received/cards/${c.userCardId}?yearMonth=${YEAR_MONTH}` },
];

const WRITE_ENDPOINTS = [
    {
        name: 'user_login', method: 'POST', auth: false,
        url: () => '/api/user/login',
        body: () => ({ loginId: WRITE_USER, password: PASSWORD }),
    },
    {
        name: 'user_signup', method: 'POST', auth: false,
        url: () => '/api/user/signup',
        // loginId·phone이 UNIQUE라 매 호출 새 값을 만든다. 이 계정들은 측정 후 정리 대상이다(§cleanup).
        body: () => {
            const n = `${Date.now()}${Math.floor(Math.random() * 1000)}`.slice(-8);
            return {
                name: 'k6베이스라인', loginId: `k6b${n}`, phone: `010${n}`,
                password: PASSWORD, passwordConfirm: PASSWORD, marketingAgreed: false,
            };
        },
    },
    {
        name: 'user_pin_verify', method: 'POST',
        url: () => '/api/user/payment-pin/verify',
        body: () => ({ currentPin: PIN }),
        useWriteToken: true,
    },
    {
        name: 'user_location_agreement', method: 'PATCH',
        url: () => '/api/user/location-agreement',
        // 같은 값을 반복해 넣어 상태가 흔들리지 않게 한다.
        body: () => ({ agreed: true }),
        useWriteToken: true,
    },
    {
        name: 'cards_mydata', method: 'POST',
        url: () => '/api/cards/mydata',
        /*
         * ⚠️ **멱등하지 않다.** 컨트롤러 문서는 "이미 등록된 카드는 건너뛰고 새로 발견된 카드만
         * 등록한다"고 적혀 있지만, 실측하면 호출할 때마다 카드가 계속 늘어난다 —
         * WRITE_USER의 보유 카드가 한 세션에 2장 → 6장 → 11장이 됐다(2026-08-16).
         * 연동 소스가 매번 다른 카드를 돌려주는 것으로 보인다.
         *
         * 측정 자체는 되지만 **WRITE_USER의 카드가 실행할 때마다 누적된다.** 이 유저를 다른
         * 측정에 재사용하지 말고, 카드 수가 비현실적으로 불어나면 정리하고 다시 시작한다.
         */
        body: () => null,
        useWriteToken: true,
    },
    {
        name: 'cards_display_order', method: 'PATCH',
        url: (c) => '/api/user-cards/display-order',
        // 현재 순서를 그대로 다시 써서 멱등하게 만든다.
        body: (c) => ({ userCardIds: c.writeUserCardIds }),
        /*
         * 보유 카드 전체와 정확히 일치해야 통과한다(불일치면 400 INVALID_CARD_DISPLAY_ORDER).
         * 앞서 도는 cards_mydata가 카드를 새로 등록하면 setup()에서 잡아둔 목록이 낡는다 —
         * 실제로 그렇게 400을 맞았다. 매 호출 직전에 현재 목록을 다시 읽는다.
         */
        prepare: (c) => {
            const r = http.get(`${BASE_URL}/api/user-cards`,
                { headers: authHeaders(c.writeToken), tags: { name: 'prepare_display_order' } });
            c.writeUserCardIds = (r.json('data') || []).map((x) => x.userCardId);
        },
        useWriteToken: true,
    },
    {
        name: 'payment_pin_verify', method: 'POST',
        url: (c) => '/api/payment/pin/verify',
        body: (c) => ({ userCardId: c.writeUserCardId, paymentPin: PIN }),
        useWriteToken: true,
    },
];

// Trend는 init 컨텍스트에서만 만들 수 있다. 이름이 정적이어야 하는 이유가 이것이다.
const trends = {};
const errors = {};
for (const e of [...READ_ENDPOINTS, ...WRITE_ENDPOINTS]) {
    trends[e.name] = new Trend(`ep_${e.name}`, true);
    errors[e.name] = new Rate(`err_${e.name}`);
}

export const options = {
    scenarios: {
        baseline: { executor: 'shared-iterations', vus: 1, iterations: 1, maxDuration: '2h' },
    },
    // baseline은 판정이 아니라 관측이다. threshold를 걸면 느린 엔드포인트에서 테스트가 중단돼
    // 나머지 표가 비어버린다. SLO 판정은 4단계에서 붙인다.
    thresholds: {},
    summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'max'],
    discardResponseBodies: false,
};

function login(loginId) {
    const res = http.post(`${BASE_URL}/api/user/login`,
        JSON.stringify({ loginId, password: PASSWORD }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'setup_login' } });

    if (res.status !== 200) {
        throw new Error(`로그인 실패 (${loginId}): HTTP ${res.status} — ${res.body}`);
    }
    return res.json('data.accessToken');
}

function authHeaders(token) {
    return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
}

/**
 * 측정 대상 ID를 하드코딩하지 않고 API로 뽑는다.
 * RDS 보안그룹이 3306을 EB SG에만 열어 두어 로컬에서 DB를 직접 못 보기도 하고,
 * 하드코딩한 ID는 재적재 한 번에 조용히 틀려지기 때문이다.
 */
export function setup() {
    const readToken = login(READ_USER);
    const writeToken = login(WRITE_USER);

    const cardsRes = http.get(`${BASE_URL}/api/user-cards`, { headers: authHeaders(readToken) });
    const cards = cardsRes.json('data') || [];
    if (cards.length === 0) {
        throw new Error(`${READ_USER}에 보유 카드가 없다. 카드·혜택·리포트 API가 전부 빈 결과를 재게 되므로 중단한다.`);
    }

    const writeCardsRes = http.get(`${BASE_URL}/api/user-cards`, { headers: authHeaders(writeToken) });
    const writeCards = writeCardsRes.json('data') || [];

    const storeRes = http.get(
        `${BASE_URL}/api/store/search?latitude=${LAT}&longitude=${LNG}&radiusMeters=3000`,
        { headers: authHeaders(readToken) });
    const stores = storeRes.json('data.stores') || [];
    if (stores.length === 0) {
        throw new Error('좌표 검색이 0건이다. 좌표를 바꾸거나 반경을 넓혀야 한다.');
    }

    /*
     * 측정 유저가 실제로 거래를 갖고 있는지 확인한다.
     *
     * 이 검사가 없으면 비활성 유저(5만 중 2만)로 돌려도 테스트가 초록불로 끝나고,
     * 집계 API가 0행을 20~30ms에 반환한 값이 baseline 표에 그대로 실린다.
     * 표만 보면 "리포트 API는 이미 충분히 빠르다"로 읽혀 5단계 개선 대상에서 빠진다 —
     * 측정이 틀린 게 아니라 **측정 대상이 없었다**는 것을 표가 드러내지 못하는 게 문제다.
     * 그래서 조용히 통과시키지 않고 여기서 끊는다.
     */
    const probe = http.get(`${BASE_URL}/api/report/benefit/summary?yearMonth=${YEAR_MONTH}`,
        { headers: authHeaders(readToken) });
    const categories = probe.json('data.categories') || [];
    const received = Number(probe.json('data.totalReceivedBenefit') || 0);
    if (categories.length === 0 && received === 0) {
        throw new Error(
            `READ_USER=${READ_USER}는 ${YEAR_MONTH}에 거래가 없다(리포트 요약이 비어 있다). `
            + '활성 3만 명은 가입자 5만 명에 흩어져 있어 앞번호가 활성이라는 보장이 없다. '
            + 'READ_USER 환경변수로 활성 유저를 지정해라. 기본값 perf000100은 실측 확인됐다. '
            + '다른 유저를 찾으려면: SELECT u.login_id, COUNT(*) FROM payment_transaction pt '
            + 'JOIN user_card uc ON uc.user_card_id=pt.user_card_id JOIN users u ON u.user_id=uc.user_id '
            + `WHERE pt.paid_at >= '${YEAR_MONTH}-01' GROUP BY u.login_id ORDER BY 2 DESC LIMIT 5;`);
    }

    /*
     * 유저가 활성이어도 그 유저의 카드 전부에 거래가 있는 건 아니다. cards[0]을 그냥 쓰면
     * card_transactions · card_usage · card_summary · report_card_received가 다시 0행을 잰다.
     * 그 달에 거래가 실제로 있는 카드를 고른다.
     */
    let measuredCard = cards[0];
    let measuredCardTxCount = 0;
    for (const c of cards) {
        const r = http.get(`${BASE_URL}/api/card/${c.userCardId}/transactions?yearMonth=${YEAR_MONTH}`,
            { headers: authHeaders(readToken) });
        const n = (r.json('data.transactions.content') || []).length;
        if (n > measuredCardTxCount) {
            measuredCardTxCount = n;
            measuredCard = c;
        }
    }
    if (measuredCardTxCount === 0) {
        throw new Error(
            `READ_USER=${READ_USER}의 보유 카드 ${cards.length}장 어디에도 ${YEAR_MONTH} 거래가 없다. `
            + '다른 활성 유저를 지정하거나 YEAR_MONTH를 바꿔라.');
    }

    const ctx = {
        readToken,
        writeToken,
        userCardId: measuredCard.userCardId,
        storeId: stores[0].storeId,
        writeUserCardId: writeCards.length ? writeCards[0].userCardId : cards[0].userCardId,
        writeUserCardIds: writeCards.map((c) => c.userCardId),
    };

    console.log(`[setup] BASE_URL=${BASE_URL}`);
    console.log(`[setup] READ_USER=${READ_USER} userCardId=${ctx.userCardId} `
        + `(보유 ${cards.length}장 중 ${YEAR_MONTH} 거래 ${measuredCardTxCount}건으로 최다)`);
    console.log(`[setup] WRITE_USER=${WRITE_USER} userCardId=${ctx.writeUserCardId} (보유 ${writeCards.length}장)`);
    console.log(`[setup] storeId=${ctx.storeId} (좌표 검색 ${stores.length}건 중 첫 행)`);
    console.log(`[setup] yearMonth=${YEAR_MONTH} (앱 시계 고정값 2026-07-24 기준)`);
    return ctx;
}

function fire(ep, ctx, record) {
    // 측정 대상 요청을 쏘기 전에 필요한 상태를 맞춘다. 이 요청의 시간은 기록하지 않는다.
    if (ep.prepare) ep.prepare(ctx);

    const token = ep.useWriteToken ? ctx.writeToken : ctx.readToken;
    const headers = ep.auth === false ? { 'Content-Type': 'application/json' } : authHeaders(token);
    const url = `${BASE_URL}${ep.url(ctx)}`;
    const body = ep.body ? ep.body(ctx) : null;

    // tags.name을 고정해야 URL에 ID가 박힌 요청들이 k6 내부에서 따로 집계되지 않는다.
    const params = { headers, tags: { name: ep.name }, timeout: '120s' };
    const res = ep.method === 'GET'
        ? http.get(url, params)
        : http.request(ep.method, url, body === null ? null : JSON.stringify(body), params);

    const ok = res.status >= 200 && res.status < 300;
    if (record) {
        trends[ep.name].add(res.timings.duration);
        errors[ep.name].add(!ok);
    }
    if (!ok && record) {
        console.warn(`[${ep.name}] HTTP ${res.status} — ${String(res.body).slice(0, 300)}`);
    }
    return ok;
}

export default function (ctx) {
    for (const ep of READ_ENDPOINTS) {
        for (let i = 0; i < WARMUP; i++) fire(ep, ctx, false);
        for (let i = 0; i < READ_ITERATIONS; i++) fire(ep, ctx, true);
        console.log(`[read ] ${ep.name} 완료 (warmup ${WARMUP} + 측정 ${READ_ITERATIONS})`);
    }

    for (const ep of WRITE_ENDPOINTS) {
        // WRITE는 워밍업을 돌리지 않는다. 워밍업분도 그대로 데이터를 바꾸기 때문이다.
        for (let i = 0; i < WRITE_ITERATIONS; i++) fire(ep, ctx, true);
        console.log(`[write] ${ep.name} 완료 (측정 ${WRITE_ITERATIONS})`);
    }
}

// ── 결과 표 ────────────────────────────────────────────────────────────────

function ms(v) {
    if (v === undefined || v === null || Number.isNaN(v)) return '—';
    return v >= 1000 ? `**${(v / 1000).toFixed(2)}s**` : `${v.toFixed(0)}ms`;
}

function buildTable(data) {
    const rows = [];
    const push = (ep, kind, n) => {
        const t = data.metrics[`ep_${ep.name}`];
        const e = data.metrics[`err_${ep.name}`];
        if (!t) return;
        rows.push({
            name: ep.name,
            kind,
            method: ep.method,
            n,
            med: t.values.med,
            p95: t.values['p(95)'],
            max: t.values.max,
            errRate: e ? e.values.rate : 0,
        });
    };
    for (const ep of READ_ENDPOINTS) push(ep, 'READ', READ_ITERATIONS);
    for (const ep of WRITE_ENDPOINTS) push(ep, 'WRITE', WRITE_ITERATIONS);

    rows.sort((a, b) => b.p95 - a.p95);

    const lines = [
        '| # | 엔드포인트 | 계열 | N | p50 | p95 | max | 에러율 |',
        '|---:|---|---|---:|---:|---:|---:|---:|',
    ];
    rows.forEach((r, i) => {
        lines.push(`| ${i + 1} | \`${r.name}\` | ${r.kind} | ${r.n} | ${ms(r.med)} | ${ms(r.p95)} | ${ms(r.max)} | ${(r.errRate * 100).toFixed(1)}% |`);
    });
    return { table: lines.join('\n'), rows };
}

export function handleSummary(data) {
    const { table, rows } = buildTable(data);

    const header = [
        '# 3단계 — API별 baseline (1 VU, 부하 없음)',
        '',
        `- 대상: \`${BASE_URL}\``,
        `- READ N=${READ_ITERATIONS} (워밍업 ${WARMUP}회 별도, 기록 제외) · WRITE N=${WRITE_ITERATIONS}`,
        `- 측정 유저: READ \`${READ_USER}\` · WRITE \`${WRITE_USER}\` · yearMonth \`${YEAR_MONTH}\``,
        '',
        '> WRITE는 표본이 작다(반복하면 UNIQUE 제약에 걸리거나 데이터를 오염시킨다).',
        '> **p95를 신뢰하지 말고 p50과 max만 참고한다.**',
        '',
        `> p99는 싣지 않는다 — N=${READ_ITERATIONS}에서도 상위 1%가 2개뿐이라 보간으로 지어낸 값이 되고,`,
        '> 실측하면 항상 max 바로 밑에 붙어 max의 그림자 노릇만 한다. 꼬리는 max로 본다.',
        '',
        'p95 내림차순 정렬 — 위쪽이 개선 우선순위다.',
        '',
    ].join('\n');

    const md = `${header}${table}\n`;

    return {
        stdout: `\n${md}\n`,
        'baseline-summary.md': md,
        'baseline-summary.json': JSON.stringify({ baseUrl: BASE_URL, yearMonth: YEAR_MONTH, rows }, null, 2),
    };
}
