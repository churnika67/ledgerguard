package com.ledgerguard.ledger.domain;

/** Business reasons a transfer can be rejected for. Stored as text on {@link Transfer}. */
public enum RejectReason {
    INSUFFICIENT_FUNDS,
    SAME_ACCOUNT,
    CURRENCY_MISMATCH
}
