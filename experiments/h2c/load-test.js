// experiments/h2c/load-test.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const latencyTrend = new Trend('bff_latency_ms', true);
const errorRate = new Rate('error_rate');

export const options = {
  stages: [
    { duration: '15s', target: 30 },  // ramp-up
    { duration: '60s', target: 30 },  // steady state
    { duration: '15s', target: 0  },  // ramp-down
  ],
  thresholds: {
    bff_latency_ms: ['p(95)<500'],
    error_rate: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:30080';

export default function () {
  const marketplaceRes = http.post(
    `${BASE_URL}/api/buyer/marketplace/list`,
    JSON.stringify({}),
    { headers: { 'Content-Type': 'application/json' } }
  );
  latencyTrend.add(marketplaceRes.timings.duration, { endpoint: 'marketplace' });
  errorRate.add(marketplaceRes.status !== 200);
  check(marketplaceRes, { 'marketplace 200': (r) => r.status === 200 });

  const categoryRes = http.post(
    `${BASE_URL}/api/buyer/category/list`,
    JSON.stringify({}),
    { headers: { 'Content-Type': 'application/json' } }
  );
  latencyTrend.add(categoryRes.timings.duration, { endpoint: 'category' });
  errorRate.add(categoryRes.status !== 200);
  check(categoryRes, { 'category 200': (r) => r.status === 200 });

  sleep(0.1);
}
