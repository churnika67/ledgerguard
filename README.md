# LedgerGuard

An event-driven payments ledger, built as a set of Spring Boot microservices behind a Spring
Cloud Gateway, whose one job is to **never double-charge, lose, or create money -- even under
heavy concurrency.** Every transfer is double-entry, every write is idempotent, and every
invariant is independently checkable at any time via `GET /reconciliation`. A chaos test throws
10,000 concurrent transfers from 200 virtual threads at 50 accounts and asserts the books still
balance, for both of the two supported locking strategies.

**Live API (via the gateway):** https://ledgerguard-api-gateway.onrender.com/api/ledger
**Live API (ledger-service directly):** https://ledgerguard-ledger-service.onrender.com
**Live API (notification-service directly):** https://ledgerguard-notification-service.onrender.com
**Swagger UI:** https://ledgerguard-ledger-service.onrender.com/swagger-ui.html
**Live web app:** https://ledgerguard-web.vercel.app (demo users: `alice`/`password123`, or
`admin`/`admin123`)
**Live admin console:** https://ledgerguard-admin-console.vercel.app (ADMIN only --
`admin`/`admin123`)

> **Free tier:** the service sleeps when idle, so the first request after a while can take
> ~1-3 minutes to wake up; it's fast on every request after that. The database is a free
> [Neon](https://neon.tech) Postgres (serverless, no 30-day expiry, unlike Render's free Postgres
> plan) -- connected via env vars only, nothing database-specific is hardcoded or committed.
>
> The live deploy runs ledger-service only, with Kafka disabled (`KAFKA_ENABLED=false`) -- Render
> has no managed Kafka, and this keeps the demo free and dependency-light. The full event-driven
> pipeline (outbox -> Kafka -> idempotent notification consumer -> dead-letter topic) is real,
> tested code, not a stub; it's proven by the automated test suite and by running
> `docker compose up` locally, which brings up both services plus Kafka in KRaft mode. See
> [docs/design-spec.md](docs/design-spec.md) for how it all fits together.
>
> **Known gap in the live demo:** notification-service is deployed and reachable through the
> gateway (listing notifications and the live SSE stream both work against its MongoDB Atlas
> free tier), but it has nothing to consume, because the live ledger-service runs with
> `KAFKA_ENABLED=false` (see above) -- Render has no managed Kafka, so no transfer events are
> ever published for it to react to in this environment. Login, accounts, and sending money
> (including the idempotency-key replay demo) all work fully live. The full outbox -> Kafka ->
> notification pipeline, and the live SSE push once a notification is created, are both proven by
> the automated test suite and by `docker compose up` locally end-to-end.
>
> Redis caching (`GET /accounts/{id}`) and the gateway's per-user rate limiting both run against
> a real [Upstash](https://upstash.com) Redis instance in production, free tier -- same
> env-vars-only wiring as the database.

## What's here

- **ledger-service** -- accounts, transfers, double-entry ledger, reconciliation, JWT auth,
  the transactional outbox, Redis-backed caching on `GET /accounts/{id}`.
- **notification-service** -- consumes `ledger.transfers` off Kafka, idempotently, stores
  notifications in MongoDB, and serves both a one-shot list and a live Server-Sent Events stream
  per account.
- **api-gateway** -- a Spring Cloud Gateway in front of both services: validates the JWT before
  forwarding, enforces a per-user Redis-backed rate limit, and routes `/api/ledger/**` and
  `/api/notifications/**` to the right backend. Both frontends talk to this, not to the backend
  services directly. See [docs/interview-notes.md](docs/interview-notes.md) for how a request
  flows through it.
- **common** -- the Kafka event DTOs shared by the two backend services.
- **web** -- a Next.js 14 customer app (login, accounts, send money, notifications). Talks to
  the gateway through its own server-side API routes (a Backend-for-Frontend layer), never
  directly from the browser. See interview-notes.md for how that's wired up.
- **admin-console** -- an Angular 17 back-office SPA (reconciliation dashboard, a
  sortable/searchable/paginated accounts table, a filterable transfer audit log, and a
  chaos-test panel that drives real concurrent load against the backend and charts the result).
  Unlike `web`, it's a pure client-side app with no server of its own, so it calls the gateway
  directly from the browser -- see interview-notes.md for why that means it stores its JWT
  differently than the Next.js app does.

## How to run it

### With Docker Compose (everything: gateway, both services, Postgres, MongoDB, Kafka, Redis)

```bash
docker compose up --build
```

- api-gateway: http://localhost:8082 (`/api/ledger/**`, `/api/notifications/**` -- this is what
  both frontends point at)
- ledger-service: http://localhost:8090 directly (Swagger at `/swagger-ui.html`)
- notification-service: http://localhost:8081 directly
- Postgres: localhost:5544
- MongoDB: localhost:27018
- Kafka: localhost:9092
- Redis: localhost:6399

### The web app

```bash
cd web
cp .env.local.example .env.local   # points GATEWAY_URL at api-gateway, not the services directly
npm install
npm run dev                        # http://localhost:3000
```

Tests: `npm test` (Jest + React Testing Library), `npm run test:e2e` (Playwright; needs the
backend running too).

### The admin console

```bash
cd admin-console
npm install
npm start                          # http://localhost:4200 (ADMIN only -- admin/admin123)
```

Points at `http://localhost:8082/api/ledger` (the local gateway) in dev by default (see
`src/environments/environment.ts`); the production build points at the live gateway on Render.
Tests: `npm test` (Jasmine/Karma), `npx playwright test` (needs the backend running too).

### Just the tests

```bash
mvn verify
```

Runs the full suite, including Testcontainers-backed integration tests, the security tests, and
the chaos test (both locking strategies) -- Docker must be running. The benchmark tests are
tagged out of this by default (they're a one-time measurement, not a correctness gate); run them
explicitly with:

```bash
mvn test -Dtest=PessimisticBenchmarkTest,OptimisticBenchmarkTest -Dsurefire.excludedGroups=
```

## curl walkthrough

```bash
BASE=http://localhost:8082/api/ledger   # or https://ledgerguard-api-gateway.onrender.com/api/ledger live --
                                         # goes through the gateway, so it's also rate-limited per user

# 1. Get a token (demo users: alice/password123 = USER, admin/admin123 = ADMIN)
TOKEN=$(curl -s -X POST "$BASE/auth/token" \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password123"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")

# 2. Open two accounts
FROM=$(curl -s -X POST "$BASE/accounts" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"ownerName":"alice","currency":"USD","openingBalance":"100.00"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
TO=$(curl -s -X POST "$BASE/accounts" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"ownerName":"alice","currency":"USD","openingBalance":"0.00"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")

# 3. Transfer, with an Idempotency-Key
curl -s -X POST "$BASE/transfers" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-key-1" \
  -d "{\"fromAccountId\":\"$FROM\",\"toAccountId\":\"$TO\",\"amount\":\"25.00\",\"currency\":\"USD\"}"
# -> 201, status COMPLETED

# 4. Replay the exact same request: same result, no new money movement, 200 instead of 201
curl -s -X POST "$BASE/transfers" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-key-1" \
  -d "{\"fromAccountId\":\"$FROM\",\"toAccountId\":\"$TO\",\"amount\":\"25.00\",\"currency\":\"USD\"}"

# 5. Reuse the key with a DIFFERENT body: 409
curl -s -o /dev/null -w "%{http_code}\n" -X POST "$BASE/transfers" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-key-1" \
  -d "{\"fromAccountId\":\"$FROM\",\"toAccountId\":\"$TO\",\"amount\":\"99.00\",\"currency\":\"USD\"}"

# 6. Reconciliation (ADMIN only)
ADMIN_TOKEN=$(curl -s -X POST "$BASE/auth/token" -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")
curl -s "$BASE/reconciliation" -H "Authorization: Bearer $ADMIN_TOKEN"
```

## Benchmark

20 accounts, 3,000 transfers, 50 concurrent virtual threads, fresh Idempotency-Keys throughout
(isolates steady-state transfer cost from idempotency/rejection overhead). Full write-up and
analysis in [docs/benchmark.md](docs/benchmark.md).

| Strategy    | Throughput (tps) | p95 latency | Retries | Conflicts | Retry-exhausted |
|-------------|------------------:|-------------:|--------:|----------:|------------------:|
| PESSIMISTIC |             603.5 |       185 ms |       0 |         0 |                 0 |
| OPTIMISTIC  |             132.0 |     1,987 ms |   6,265 |     6,349 |                85 |

## What I learned

Writing the chaos and benchmark tests surfaced two real concurrency bugs that unit tests alone
never would have:

1. **Ledger entries were inserted before the transfer row they reference.** Fine on H2, but
   Postgres enforces the foreign key immediately, so the very first real-Postgres integration
   test failed with a constraint violation. Fix: write the transfer row first.
2. **Optimistic locking can deadlock, not just version-conflict.** Two transfers updating the
   same pair of accounts in opposite order can deadlock in Postgres, because optimistic locking
   takes no explicit locks and therefore has no fixed acquisition order (unlike pessimistic
   locking, which always locks accounts in ascending id order for exactly this reason). The
   retry loop only caught `ObjectOptimisticLockingFailureException` at first, so a
   deadlock-aborted transaction silently vanished instead of being retried. Catching Spring's
   broader `ConcurrencyFailureException` (the common superclass of both) fixed it -- caught by
   the chaos test's row-count assertions before it ever reached a benchmark or production.

More generally: idempotency is easy to get right for the sequential case and easy to get subtly
wrong for the concurrent one. The two-concurrent-requests-same-new-key race (two clients racing
to use a brand-new `Idempotency-Key` for the first time) only shows up under genuine concurrency,
and the fix (catch the database's own unique-constraint violation and resolve to the winner's
result) is a different code path from the simple "look it up first" replay check.

## Architecture, data model, and design rationale

See [docs/design-spec.md](docs/design-spec.md) for the full write-up: requirements, data model
(with an ER diagram), the double-entry and idempotency guarantees, both locking strategies and
why the deadlock-ordering rule matters, the outbox/Kafka/idempotent-consumer/DLT pipeline, and
the security model and failure-mode table.
