package com.ledgerguard.ledger.exception;

/**
 * The optimistic executor hit {@code ledger.optimistic-retry.max-attempts} version conflicts
 * without a clean commit. Nothing was persisted, so the caller can safely retry with the same
 * Idempotency-Key. Maps to HTTP 503.
 */
public class RetryExhaustedException extends RuntimeException {

    public RetryExhaustedException(int attempts) {
        super("Gave up after " + attempts + " optimistic-locking attempts due to contention");
    }
}
