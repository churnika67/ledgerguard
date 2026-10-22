package com.ledgerguard.ledger.exception;

import java.util.UUID;

/** Maps to HTTP 404. */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(UUID accountId) {
        super("Account " + accountId + " was not found");
    }
}
