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
