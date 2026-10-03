// Realistic traffic for the demo app. Manually run with:
//
//   docker compose run --rm k6 run /scripts/load.js                    # default "load" profile (~8 min)
//   docker compose run --rm k6 run -e PROFILE=spike /scripts/load.js   # overloads the shipping queue
//   docker compose run --rm k6 run -e PROFILE=smoke /scripts/load.js   # 1 min sanity check
//
// Uses an k6's arrival-rate executor: k6 starts N iterations per second regardless of how slow the app is (an "open model"), 
// like real users - so latency problems show up as latency, not as a silently reduced request rate.
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Define different load profiles (stage) for the test scenarios.
const PROFILES = {
  smoke: [
    { duration: '1m', target: 5 },
  ],
  load: [
    { duration: '1m', target: 10 },
    { duration: '1m', target: 20 },
    { duration: '5m', target: 20 },
    { duration: '1m', target: 0 },
  ],
  // ~60 iterations/s -> ~30 orders/s, but the shipping worker handles ~15/s:
  // orders_queue_size grows and OrderQueueBacklog fires.
  spike: [
    { duration: '1m', target: 10 },
    { duration: '30s', target: 60 },
    { duration: '3m', target: 60 },
    { duration: '1m', target: 0 },
  ],
};

// k6's standard way to configure a test inside the script
export const options = {
  scenarios: {
    shop: {
      executor: 'ramping-arrival-rate', // k6's open model executor
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: 20,
      maxVUs: 200,
      stages: PROFILES[__ENV.PROFILE || 'load'], // k6's defined load profiles
    },
  },
  // k6's own pass/fail criteria (client side view of the same RED signals).
  thresholds: {
    http_req_failed: ['rate<0.10'],
    http_req_duration: ['p(95)<800'],
  },
};

//-----------------------  EXECUTED CODE BY K6 -----------------------------

const PRODUCTS = ['keyboard', 'mouse', 'monitor', 'headset', 'laptop'];
const CHANNELS = ['web', 'web', 'web', 'mobile', 'mobile', 'partner']; // web is the busiest channel
const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

let lastOrderId = 0; // per VU

function pick(list) {
  return list[Math.floor(Math.random() * list.length)];
}

function createOrder() {
  const body = {
    product: pick(PRODUCTS),
    quantity: 1 + Math.floor(Math.random() * 3),
    channel: pick(CHANNELS),
  };
  const res = http.post(`${BASE_URL}/api/orders`, JSON.stringify(body), JSON_HEADERS);
  check(res, { 'order created': (r) => r.status === 201 });
  if (res.status === 201) {
    lastOrderId = res.json('id');
  }
}

// One iteration of a virtual user: the code k6 auto runs again and again during the test.
export default function () {
  const roll = Math.random();

  if (roll < 0.50) {
    createOrder();
  } else if (roll < 0.75) {
    // Unknown ids produce a few legitimate 404s (client errors, not server errors).
    const id = lastOrderId > 0 ? Math.max(1, lastOrderId - Math.floor(Math.random() * 50)) : 1;
    const res = http.get(`${BASE_URL}/api/orders/${id}`, { tags: { name: 'GET /api/orders/{id}' } });
    check(res, { 'order read': (r) => r.status === 200 || r.status === 404 });
  } else if (roll < 0.90) {
    http.get(`${BASE_URL}/api/orders?limit=20`, { tags: { name: 'GET /api/orders' } });
  } else if (roll < 0.97) {
    http.get(`${BASE_URL}/api/orders/stats`);
  } else if (roll < 0.985) {
    // Invalid payload -> 400
    http.post(`${BASE_URL}/api/orders`, JSON.stringify({ product: '', quantity: 0, channel: 'fax' }), JSON_HEADERS);
  } else {
    // Unknown product -> 404
    http.post(`${BASE_URL}/api/orders`, JSON.stringify({ product: 'toaster', quantity: 1, channel: 'web' }), JSON_HEADERS);
  }
}
