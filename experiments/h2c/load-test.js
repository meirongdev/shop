// experiments/h2c/load-test.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const latencyTrend = new Trend('bff_latency_ms', true);
const errorRate = new Rate('error_rate');

export const options = {
  stages: [
    { duration: '15s', target: 10 },  // ramp-up
    { duration: '60s', target: 10 },  // steady state
    { duration: '15s', target: 0  },  // ramp-down
  ],
  // Only enforce zero request errors. Latency/throughput are reported in the
  // summary and documented in FINDINGS.md; they should not make k6 exit non-zero.
  thresholds: {
    error_rate: ['rate==0'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:38080';
const HEADERS = { 'Content-Type': 'application/json' };

export default function () {
  const marketplaceRes = http.post(
    `${BASE_URL}/buyer/v1/marketplace/list`,
    JSON.stringify({}),
    { headers: HEADERS }
  );
  latencyTrend.add(marketplaceRes.timings.duration, { endpoint: 'marketplace' });
  errorRate.add(marketplaceRes.status !== 200);
  check(marketplaceRes, { 'marketplace 200': (r) => r.status === 200 });

  const categoryRes = http.post(
    `${BASE_URL}/buyer/v1/category/list`,
    JSON.stringify({}),
    { headers: HEADERS }
  );
  latencyTrend.add(categoryRes.timings.duration, { endpoint: 'category' });
  errorRate.add(categoryRes.status !== 200);
  check(categoryRes, { 'category 200': (r) => r.status === 200 });

  sleep(0.1);
}
