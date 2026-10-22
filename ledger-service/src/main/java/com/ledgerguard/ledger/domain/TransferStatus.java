package com.ledgerguard.ledger.domain;

/** Terminal outcome of a transfer request. Transfers are never left pending. */
public enum TransferStatus {
    COMPLETED,
    REJECTED
}
