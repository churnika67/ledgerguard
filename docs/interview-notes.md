# Interview notes

Plain-English, one-paragraph answers to the things this project actually demonstrates. Written so
I can explain them out loud without re-reading the code first.

## How idempotency prevents double charges

Every transfer request carries a client-generated `Idempotency-Key` header. Before doing
anything, the service looks up whether that key has been used before. If it has, and the request
body is the same, it just returns the original result -- no new database write, no new money
movement. If the key exists but the body is *different*, that's a client bug (reusing a key for a
different request), so it's rejected with a 409 rather than guessing which one the client meant.
The subtle part is the race: if two requests with the same *brand-new* key arrive at the same
instant (e.g. a mobile app retrying a timed-out request while the original is still in flight),
both might pass the "have I seen this key before?" check before either has committed. The database
itself breaks the tie with a `UNIQUE` constraint on the key column -- whichever request's `INSERT`
commits first wins, and the loser's insert fails with a constraint violation that the code catches
and turns into "read back the winner's result and return that," so the caller never sees an error
and money never moves twice.

## Pessimistic vs optimistic locking, and what the benchmark showed

Both strategies guarantee the same correctness; they differ in how they pay for it under
contention. Pessimistic locking grabs a `SELECT ... FOR UPDATE` row lock on both accounts *before*
touching anything, so a colliding transfer just waits its turn -- no wasted work. Optimistic
locking takes no lock at all; it reads the account, does the math, and only finds out there was a
conflict when it tries to commit (via a `@Version` column), at which point it has to throw the
work away and retry from scratch. My benchmark (20 accounts, 3,000 transfers, 50 threads -- small
and deliberately contention-heavy) made the tradeoff concrete: pessimistic did 603.5 transfers/sec
at p95 185ms with zero retries; optimistic did 132.0 transfers/sec at p95 1,987ms with 6,265
retries and 85 transfers that gave up entirely after exhausting their retry budget. Under *low*
contention (most transfers don't touch the same account), optimistic would win because it never
pays any locking cost in the common case -- this benchmark just dials contention up far enough to
show where it breaks down.

## Why ascending-id lock ordering prevents deadlocks

A deadlock happens when two transactions each hold a lock the other one needs, and neither will
let go. If transfer A→B locks A first then B, and at the same moment transfer B→A locks B first
then A, they can each grab their "first" lock and then block forever waiting for the other's
"second" lock -- classic deadlock. The fix is to never let the *direction* of the transfer decide
lock order: always lock the two accounts in the same fixed order regardless of which one is
logically "from" or "to" (I use ascending UUID order). Now both of those transfers try to lock A
first -- one of them simply gets there first, acquires *both* locks, finishes, and releases them;
the other was never holding a lock the first one needed, so there's nothing to deadlock on.

## Why the transactional outbox pattern

The problem it solves: you want "the transfer happened" and "an event about it was published" to
be atomic, but a database commit and a Kafka publish are two separate systems with no shared
transaction. If you publish first and the DB write fails, you've announced something that didn't
happen; if you commit first and the publish fails, nothing ever finds out it happened. The outbox
pattern sidesteps this by writing the event as a plain row in the *same* database transaction as
the business change -- so either both happen or neither does, because it's one transaction in one
database. A separate background process then reads unpublished outbox rows and sends them to
Kafka, marking them published only after the broker acknowledges. That process can crash between
"Kafka acked" and "marked published," which means an event might get sent twice -- but that's a
far easier problem (handle on the consumer side) than losing an event or publishing a lie.

## How the idempotent consumer + dead-letter topic works

Because the outbox publisher only guarantees *at-least-once* delivery (see above), and Kafka
itself redelivers messages after a consumer rebalance, the consumer has to be able to see the same
event twice and do nothing the second time. I do this by giving every event a stable id (the
outbox row's own id, carried as a Kafka header) and keeping a table of ids already processed.
Before creating any notifications, the consumer checks that table; if the id is already there,
it's a no-op. The notifications plus the "mark processed" row are written in one transaction, so
that check-and-mark is itself atomic -- there's no window where two concurrent deliveries of the
same event both pass the check. Separately, if processing a message *fails* for a real reason
(not a duplicate -- an actual bug or outage), it's retried a few times with backoff, and if it
still fails, it's routed to a dead-letter topic instead of blocking that Kafka partition forever
for every message behind it.

## Server Components vs Client Components, and why the split matters

Next.js's App Router gives every page a choice: render on the server, or render in the browser.
Server Components (the default) run once on the server per request -- they can read the httpOnly
session cookie directly and call the backend, and what gets sent to the browser is already-filled
HTML. `/accounts` and `/accounts/[id]` are Server Components for exactly this reason: the page
shows real data on the very first paint, with no loading spinner, and the JWT inside the cookie
never has to be exposed to any JavaScript running in the browser. Anything that needs to react to
clicks, typing, or polling -- forms, the "retry" button, the notifications list -- has to be a
Client Component (marked `"use client"` at the top of the file), because only client-side React
has event handlers and state. The two meet in the middle: a Server Component fetches the initial
data and passes it as a prop into a Client Component, which then takes over for interactivity.

## The Backend-for-Frontend (BFF) proxy pattern

This project's three requirements look contradictory at first: the JWT lives in an httpOnly
cookie (so client-side JavaScript literally cannot read it), the browser needs to call an API to
fetch accounts and send transfers, and CORS needs to be configured on ledger-service for the web
origin. The resolution is that the browser never calls ledger-service directly. Client-side code
calls *relative* routes under `web/src/app/api/**` -- these are Next.js Route Handlers, which run
on the server, not in the browser. A route handler reads the JWT out of the httpOnly cookie (which
server-side code *can* read) and makes the real call to ledger-service itself, attaching
`Authorization: Bearer <token>` server-to-server. That server-to-server hop isn't a cross-origin
browser request at all, so CORS doesn't even apply to it -- CORS was added to ledger-service
separately, for the case where something *does* call it directly from a browser (Swagger UI being
the obvious example). This whole shape -- a thin server layer in front of the real backend, doing
auth and reshaping requests for its one frontend -- is what "Backend-for-Frontend" means.

## Proving idempotency with a UI, not just a unit test

The backend already guarantees that resending a transfer with the same `Idempotency-Key` returns
the original result instead of moving money twice (see above). The `/send` page makes that
guarantee visible: submitting the form generates a fresh `crypto.randomUUID()` as the key for a
*new* transfer, but a "Retry same request" button resends the exact same key and body again. The
response that comes back carries the identical `transferId` as the first attempt, and the UI
flags it with a note that it was a replay. It's the same mechanism as the backend's own test suite
proves, just demonstrated by clicking a button instead of reading an assertion.

## Why money is a string on the way in, and formatted on the way out

`BigDecimal` fields in Java (account balances, transfer amounts) serialize to JSON as bare numeric
literals, e.g. `100.0`. `openapi-typescript` correctly types that as a JS `number` -- but a JS
number has no concept of "trailing zeros," so `100.00` becomes the number `100`, and naively
rendering it would show "100" instead of "100.00" in the UI. Two separate fixes were needed: for
*display*, every balance or amount is passed through a `formatMoney()` helper (`toFixed(2)`)
before being rendered; for *requests* (opening a new account, sending a transfer), the amount is
sent as a JSON *string* ("25.00") rather than a JS number, using hand-written request types instead
of the generated ones -- Jackson on the Java side happily coerces a JSON string into a `BigDecimal`
with full precision, so nothing on the backend had to change.

## Standalone components and a functional route guard

The Angular admin console (Step 2) uses no `NgModule` at all -- every component declares its own
`imports` array of exactly the pieces it needs (just `MatTableModule` for a table-heavy page,
nothing more), and the app is wired up with a handful of top-level `provide*()` calls in
`app.config.ts` instead of a root module. Route protection follows the same shift: `adminGuard`
is a plain function (`CanActivateFn`), not a class implementing `CanActivate`. It reads two
things off the decoded JWT via `AuthService`: is there a token at all, and does it carry
`ROLE_ADMIN`. A `USER`-role account (like the customer app's `alice`) can still get a valid JWT
from the same `/auth/token` endpoint -- the backend doesn't know or care which frontend is
asking -- but the guard sends them straight back to `/login` with an explanation, because
*having* a token and being *allowed into this console* are different checks.

## Why the admin console keeps its JWT in localStorage, unlike the customer app

The Next.js customer app never lets the browser's own JavaScript touch the JWT -- it sits in an
httpOnly cookie, and a server-side Backend-for-Frontend layer is what actually calls
ledger-service. The Angular admin console has no server of its own; it's a pure single-page app
that gets served as static files and calls ledger-service directly from the browser. That means
there's no server-side place to stash an httpOnly cookie, so the token has to live somewhere
client-side JavaScript can read it in order to attach `Authorization: Bearer <token>` to each
request -- `localStorage` is the standard choice for this kind of SPA. This is a real, acceptable
security tradeoff for an internal admin tool (and is why CORS matters here in a way it doesn't
for the customer app: the browser is calling ledger-service's origin directly, so
`ledger.web.cors-allowed-origins` has to explicitly list `http://localhost:4200` and the deployed
console's URL, or the browser blocks the request before it ever reaches the backend).

## Signals instead of manual subscriptions for shared state

`AuthService` holds the JWT in a `signal`, and derives `isAdmin`, `isAuthenticated`, and
`username` from it with `computed()`. Any component template that reads `auth.isAdmin()`
automatically re-renders when the underlying token signal changes -- logging in or out updates
the nav bar, the route guard's next check, and anything else reading those signals, with no
`.subscribe()`/`.unsubscribe()` pairs to manage and no risk of a component reading stale state
because it forgot to re-subscribe. This is the same idea as the Next.js app's React Query cache
invalidation (Step 1), just Angular's own reactivity primitive instead of a library's.

## Turning a JUnit chaos test into an on-demand ops action

The backend already had a chaos test (50 accounts, 10,000 transfers, 200 virtual threads,
asserting the three invariants still hold) -- but it only ran as part of `mvn verify`, on a fixed
workload, producing output only visible in a test log. `POST /admin/chaos-runs` takes the same
proven workload shape (random from/to/amount, a pool of virtual threads, retried idempotency-key
replays) and makes it parameterized, bounded (max 5,000 transfers, so nobody can accidentally
launch something unbounded against the live database), and asynchronous -- it kicks off the run
on its own virtual thread and returns immediately with a run id, which the admin console then
polls every 500ms via `GET /admin/chaos-runs/{id}` until it's done. The retries/conflicts counts
come from the same Micrometer counters the benchmark tests already used, read as a before/after
delta for just that run. The result is the same correctness exercise the automated test suite
already proves, just something a non-technical operator can trigger from a browser and watch
complete, with a chart of the outcome.

## How JWT auth flows through the gateway

*(To fill in once the API gateway from Step 3 exists.)*

## Why the Redis cache eviction is safe

*(To fill in once Redis caching from Step 3 exists.)*

## The 2 concurrency bugs the tests caught, and how they were fixed

**Bug 1 -- ledger entries inserted before their transfer row.** The code wrote the two
`LedgerEntry` rows (debit/credit) and *then* the `Transfer` row they point to via a foreign key.
That's backwards, but it never showed up until the first test ran against a real Postgres
database (earlier tests were pure Mockito, with no real foreign-key enforcement) -- Postgres
rejected the insert because the row it referenced didn't exist yet. Fix: write the transfer row
first, then the entries.

**Bug 2 -- optimistic locking can deadlock, not just version-conflict.** The retry loop for
optimistic locking only caught `ObjectOptimisticLockingFailureException` (the exception you get
from a stale `@Version`). But optimistic locking takes no explicit locks, so it has no fixed
acquisition order the way pessimistic locking does -- which means two transfers updating the same
pair of accounts in opposite order can trigger an actual Postgres deadlock, a *different*
exception (`CannotAcquireLockException`). That exception wasn't being retried; it propagated out
and silently dropped the transfer instead of trying again. This surfaced as a failed row-count
assertion in the chaos test (fewer committed transfers than expected), not as an obvious crash --
the kind of bug that's easy to miss without a test that actually hammers the system with real
concurrency. Fix: catch Spring's `ConcurrencyFailureException`, the common superclass of both
exception types, so either kind of conflict gets retried the same way.
