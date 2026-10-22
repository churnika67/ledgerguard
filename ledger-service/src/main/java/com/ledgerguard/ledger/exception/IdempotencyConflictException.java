package com.ledgerguard.ledger.exception;

/** Same Idempotency-Key reused with a different request body. Maps to HTTP 409. */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency-Key '" + idempotencyKey + "' was already used with a different request body");
    }
}
