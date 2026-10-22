# Benchmark: PESSIMISTIC vs OPTIMISTIC locking

## Setup

- 20 accounts, each opened with a balance of 1,000,000.00 USD
- 3,000 transfers of 1.00 USD each, between randomly chosen distinct account pairs
- 50 concurrent virtual threads (a fixed-size `Executors.newFixedThreadPool(50, Thread.ofVirtual().factory())`)
- Fresh Idempotency-Key per transfer (no replays) -- this benchmark isolates steady-state
  transfer cost; idempotency-replay overhead and rejection-path behavior are already covered by
  the chaos test
- Postgres 16 (Testcontainers), run locally on an Apple Silicon Mac
- Source: `ledger-service/src/test/java/com/ledgerguard/ledger/benchmark/` (tagged `benchmark`,
  excluded from the default `mvn test`/`mvn verify` run; execute with
  `mvn test -Dtest=PessimisticBenchmarkTest,OptimisticBenchmarkTest -Dsurefire.excludedGroups=`)

These are small, single-machine numbers meant to compare the two strategies against each other,
not to represent production capacity.

## Results

| Strategy    | Transfers | Throughput (transfers/sec) | p95 latency | Retries | Conflicts | Failed (retry-exhausted) |
|-------------|----------:|----------------------------:|-------------:|--------:|----------:|--------------------------:|
| PESSIMISTIC |     3,000 |                        603.5 |       185 ms |       0 |         0 |                         0 |
| OPTIMISTIC  |     3,000 |                        132.0 |     1,987 ms |   6,265 |     6,349 |                        85 |

## Why the gap

With only 20 accounts and 50 concurrent writers, almost every transfer collides with another
one touching the same account. The two strategies handle that collision completely differently:

- **PESSIMISTIC** takes `SELECT ... FOR UPDATE` on both accounts (ascending id order, so it never
  deadlocks) before doing any work. A colliding transfer simply queues behind the lock holder and
  proceeds the instant it's released -- no wasted work, no retries. This is why it posts zero
  retries and the lowest, most consistent latency here.
- **OPTIMISTIC** takes no locks up front. Under this much contention, two transfers routinely
  load the same account "simultaneously," both compute a new balance, and one loses the race when
  it tries to commit (`@Version` conflict) or the two deadlock against each other in Postgres
  (two transactions updating the same pair of accounts in opposite order -- see
  [design-spec.md](design-spec.md#locking-strategies)). Every such collision costs a full
  round trip before the retry loop (exponential backoff with jitter, up to 10 attempts) even
  starts again, which is why both latency and the retry/conflict counts balloon, and why 85
  transfers (2.8%) exhausted all 10 attempts and gave up entirely (surfaced to the caller as a
  503, safe to retry with the same Idempotency-Key since nothing was persisted).

## What this means in practice

OPTIMISTIC is the right default when accounts are rarely contended (many accounts, transfers
spread across them, or mostly non-overlapping pairs) -- it avoids taking any lock at all in the
common case. PESSIMISTIC wins when a small number of "hot" accounts see heavy concurrent traffic,
exactly the scenario this benchmark constructs. The chaos test (50 accounts, 200 threads, 10,000
transfers) sits closer to the optimistic-friendly end of that spectrum and both strategies pass
it; this benchmark deliberately dials contention up further to make the difference visible.

## What I learned

Pushing both strategies through the same workload surfaced a real bug before this benchmark was
even the goal: under OPTIMISTIC locking, two transfers updating the same pair of accounts in
opposite order can deadlock in Postgres, not just collide on `@Version`. The first version of
`OptimisticTransferExecutor` only retried on `ObjectOptimisticLockingFailureException` and let a
deadlock-aborted transaction's exception propagate uncaught, silently dropping the transfer
instead of retrying it. Catching Spring's broader `ConcurrencyFailureException` (the common
superclass of both) in the retry loop fixed it -- see the chaos test's commit history for how it
was caught by the chaos test asserting `transfers` and `ledger_entries` row counts matched
exactly.
