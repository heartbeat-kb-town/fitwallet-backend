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

/** 서울 강남역. 272만 건 중 밀도가 높아 거리 계산 후 남는 행이 많다. */
const LAT = 37.4979;
const LNG = 127.0276;

/** 계획 문서 §3의 SLO 표. 측정값으로 조정하지 않는다. */
const SLO_SIMPLE = 200;
const SLO_SEARCH = 300;
const SLO_AGG = 500;

/**
 * 활성 유저 목록. extract-active-users.sh가 만든다.
 * SharedArray는 VU 전체가 메모리 한 벌을 공유한다 — VU마다 복사하면 100배가 된다.
 */
const users = new SharedArray('active users', function () {
    return open(USER_FILE).split('\n').map((s) => s.trim()).filter(Boolean);
});

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
            // 1 VU에서 이미 SLO의 7.3배다. 부하에서 실패하는 것은 새 정보가 아니다.
            { name: 'store_search_coords',   slo: SLO_SEARCH, url: () => `/api/store/search?latitude=${LAT}&longitude=${LNG}&radiusMeters=3000` },
            { name: 'store_search_keyword',  slo: SLO_SEARCH, url: () => `/api/store/search?keyword=%EC%8A%A4%ED%83%80%EB%B2%85%EC%8A%A4&latitude=${LAT}&longitude=${LNG}` },
            { name: 'store_search_category', slo: SLO_SEARCH, url: () => `/api/store/search?categoryId=1&latitude=${LAT}&longitude=${LNG}&radiusMeters=3000` },
        ],
    },
    {
        screen: '결제 전',
        calls: [
            { name: 'benefit_expected', slo: SLO_AGG, url: (u) => `/api/benefit/expected?storeId=${u.storeId}&amount=15000` },
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
for (const c of ALL_CALLS) {
    thresholds[`http_req_duration{name:${c.name},phase:steady}`] = [`p(95)<${c.slo}`];
}

export const options = {
    scenarios: {
        warmup: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [{ target: VUS, duration: RAMP }],
            gracefulRampDown: '0s',
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
    summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'max'],
    // 응답 본문을 남긴다. 500이 났을 때 CannotGetJdbcConnection인지 봐야
    // 커넥션 풀 고갈을 확인할 수 있다(§설계 5). 응답이 작아 메모리 부담이 없다.
    discardResponseBodies: false,
};

export function setup() {
    return { pool: [] };
}

export default function () {
    // Task 4에서 구현한다.
}
