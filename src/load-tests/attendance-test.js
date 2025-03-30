import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    stress: {
      executor: 'constant-arrival-rate',
      rate: 50,
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 10,
      maxVUs: 50,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  const trendsRes = http.get(
    'http://localhost:8081/api/attendance/trends?start=2024-03-01&end=2024-03-31'
  );
  check(trendsRes, { 'trends status 200': (r) => r.status === 200 });

  const payload = JSON.stringify({
    employeeId: 1,
    date: "2024-03-30",
    status: "PRESENT"
  });
  const postRes = http.post(
    'http://localhost:8081/api/attendance',
    payload,
    { headers: { 'Content-Type': 'application/json' } }
  );
  check(postRes, { 'post status 201': (r) => r.status === 201 });

  sleep(0.5);
}