package com.ledgerguard.common.event;

/** Discriminator for outbox payloads, carried as a Kafka header and stored on the outbox row. */
public enum LedgerEventType {
    TRANSFER_COMPLETED,
    TRANSFER_REJECTED
}
