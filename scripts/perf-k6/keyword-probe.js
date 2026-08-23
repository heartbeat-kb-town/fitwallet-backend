/**
 * 검색어 갈래 전용 프로브 — 어떤 (검색어 × 좌표)가 100ms를 넘는지 행 단위로 찍는다.
 *
 * `baseline.js`는 엔드포인트별 요약을 내지만, "어느 조합이 느린가"는 알려주지 않는다.
 * 이 스크립트는 `GET /api/store/search?keyword=&latitude=&longitude=` **하나만** 1 VU로
 * 반복하고 행마다 결과를 stdout에 찍는다.
 *
 * 실행:
 *   k6 run scripts/perf-k6/keyword-probe.js                    # 실빈도 2,000행
 *   MODE=sweep k6 run scripts/perf-k6/keyword-probe.js         # 층화 스윕 (검색어 × 밀도)
 *
 * 산출물: stdout의 `ROW,...` 줄. 실행 후 로그에서 뽑아 CSV로 쓴다 —
 * **k6의 handleSummary는 VU 변수를 볼 수 없어** 파일로 직접 쓸 방법이 없다.
 *
 *   grep '^ROW,' run.log > row-latency.csv
 *
 * ──────────────────────────────────────────────────────────────────────────
 * 왜 반경을 안 보내는가
 *
 * `load.js`의 `store_search_keyword`가 그렇게 보내기 때문이다. 반경이 없으면 서비스가
 * `findStoresByKeyword`를 타고 계단식 [300, 1000, 3000, 10000] 뒤 전국 FULLTEXT로
 * 떨어진다. 반경을 보내면 `findStoresByCascadingRadius`(좌표 갈래와 같은 경로)가 되어
 * **재려는 대상이 달라진다.**
 *
 * `farthest`(5건 중 가장 먼 거리)를 함께 찍는 이유는 사다리가 몇 단에서 멈췄는지가
 * 거기서 드러나기 때문이다 — 300 미만이면 1단, 10000 근처면 끝까지 올라간 것이고,
 * 그보다 크면 전국 FULLTEXT로 떨어진 것이다.
 *
 * ⚠️ 이 API는 `search_history`에 upsert한다(ON DUPLICATE KEY UPDATE searched_at = NOW()).
 *    프로브 유저 1명 × 검색어 종수만큼만 늘어나고 그 뒤로는 갱신뿐이라 분포를 흔들지
 *    않지만, 측정 유저와 섞이지 않게 전용 유저를 쓴다.
 * ──────────────────────────────────────────────────────────────────────────
 */

import http from 'k6/http';
import { Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const BASE_URL = (__ENV.BASE_URL || 'http://fitwallet-backend-prod.ap-northeast-2.elasticbeanstalk.com')
    .replace(/\/$/, '');
const PROBE_USER = __ENV.PROBE_USER || 'perf000100';
const PASSWORD = __ENV.PERF_PASSWORD || '11112222';
const SCENARIO_FILE = __ENV.SCENARIO_FILE || './scenarios-load.csv';
const SELECTIVITY_FILE = __ENV.SELECTIVITY_FILE || './keyword-selectivity.csv';

/** `real` = 실빈도 표본 그대로, `sweep` = 검색어 × 밀도 전 조합 한 번씩. */
const MODE = __ENV.MODE || 'real';
const WARMUP = Number(__ENV.WARMUP || 20);
const SLO_MS = Number(__ENV.SLO_MS || 100);

const rows = new SharedArray('rows', function () {
    const parse = (path) => open(path).split('\n').map((s) => s.trim()).filter(Boolean);

    const sel = {};
    for (const line of parse(SELECTIVITY_FILE).slice(1)) {
        const c = line.split(',');
        sel[c[0]] = Number(c[1]);
    }

    const scenarios = parse(SCENARIO_FILE).slice(1).map(function (line) {
        const c = line.split(',');
        return { lat: c[0], lng: c[1], tier: Number(c[2]), keyword: c[3] };
    });

    if (MODE !== 'sweep') {
        return scenarios.map((s) => ({ ...s, sel: sel[s.keyword] || 0 }));
    }

    /*
     * 층화 스윕 — 검색어 전 종류 × 밀도 7단계.
     *
     * 좌표는 시나리오 CSV에서 그 밀도 단계의 것을 골라 돌려 쓴다. 단계마다 좌표를 하나로
     * 고정하면 그 좌표의 지역 특성이 결과 전체에 실리므로, 검색어마다 다음 좌표로 넘긴다.
     */
    const byTier = {};
    for (const s of scenarios) {
        (byTier[s.tier] = byTier[s.tier] || []).push(s);
    }
    const keywords = Object.keys(sel).sort();
    const out = [];
    let n = 0;
    for (const keyword of keywords) {
        for (const tier of Object.keys(byTier).map(Number).sort()) {
            const pool = byTier[tier];
            const base = pool[n % pool.length];
            out.push({ lat: base.lat, lng: base.lng, tier, keyword, sel: sel[keyword] || 0 });
            n += 1;
        }
    }
    return out;
});

const latency = new Trend('keyword_search_ms', true);

export const options = {
    scenarios: {
        probe: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: Number(__ENV.ROWS || rows.length) + WARMUP,
            maxDuration: __ENV.MAX_DURATION || '30m',
        },
    },
    // 임계값은 걸지 않는다 — 이 스크립트는 합격/불합격이 아니라 분포를 보는 도구다.
    thresholds: {},
};

export function setup() {
    const res = http.post(`${BASE_URL}/api/user/login`,
        JSON.stringify({ loginId: PROBE_USER, password: PASSWORD }),
        { headers: { 'Content-Type': 'application/json' } });
    if (res.status !== 200) {
        throw new Error(`로그인 실패 (${PROBE_USER}): HTTP ${res.status} — ${res.body}`);
    }
    console.log(`[setup] MODE=${MODE} rows=${rows.length} WARMUP=${WARMUP} SLO=${SLO_MS}ms`);
    console.log('ROW,idx,tier,keyword,selectivity,ms,n,farthest,status');
    return { token: res.json('data.accessToken') };
}

export default function (data) {
    const i = __ITER;
    const row = rows[i % rows.length];
    const url = `${BASE_URL}/api/store/search`
        + `?keyword=${encodeURIComponent(row.keyword)}&latitude=${row.lat}&longitude=${row.lng}`;

    const res = http.get(url, {
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${data.token}` },
        timeout: '60s',
        tags: { name: 'store_search_keyword' },
    });

    // 예열 구간은 기록하지 않는다. 첫 요청들이 JIT·버퍼 풀을 데우는 몫을 표에 싣지 않는다.
    if (i < WARMUP) {
        return;
    }

    let n = 0;
    let farthest = '';
    if (res.status === 200) {
        const stores = res.json('data.stores') || [];
        n = stores.length;
        if (n > 0) {
            farthest = stores[n - 1].distanceMeters;
        }
    }
    const ms = res.timings.duration;
    latency.add(ms);

    // CSV 한 줄. 검색어에 쉼표가 들어갈 수 있어 따옴표로 감싼다.
    console.log(`ROW,${i - WARMUP},${row.tier},"${row.keyword}",${row.sel},`
        + `${ms.toFixed(1)},${n},${farthest},${res.status}`);
}
