package com.ledgerguard.ledger.exception;

import java.util.UUID;

/** Maps to HTTP 404. */
public class TransferNotFoundException extends RuntimeException {

    public TransferNotFoundException(UUID transferId) {
        super("Transfer " + transferId + " was not found");
    }
}
