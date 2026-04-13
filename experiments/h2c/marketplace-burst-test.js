import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const latencyTrend = new Trend('bff_latency_ms', true);
const errorRate = new Rate('error_rate');

const rampSeconds = __ENV.RAMP_SECONDS || '15';
const steadySeconds = __ENV.STEADY_SECONDS || '60';
const targetVus = Number(__ENV.TARGET_VUS || '10');
const fanout = Number(__ENV.FANOUT || '8');
const headerBytes = Number(__ENV.HEADER_BYTES || '0');
const sleepSeconds = Number(__ENV.SLEEP_SECONDS || '0.1');

export const options = {
  stages: [
    { duration: `${rampSeconds}s`, target: targetVus },
    { duration: `${steadySeconds}s`, target: targetVus },
    { duration: `${rampSeconds}s`, target: 0 },
  ],
  thresholds: {
    error_rate: ['rate==0'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:38080';
const HEADERS = { 'Content-Type': 'application/json' };

export default function () {
  const res = http.post(
    `${BASE_URL}/buyer/v1/experiments/h2c/marketplace-burst`,
    JSON.stringify({ fanout, headerBytes }),
    { headers: HEADERS }
  );
  latencyTrend.add(res.timings.duration);
  errorRate.add(res.status !== 200);
  check(res, { 'marketplace burst 200': (r) => r.status === 200 });
  sleep(sleepSeconds);
}
