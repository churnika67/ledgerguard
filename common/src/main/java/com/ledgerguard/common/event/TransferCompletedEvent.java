package com.ledgerguard.common.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Emitted once a transfer has been committed and both ledger entries written.
 * {@code eventId} is the outbox event id and doubles as the Kafka-level idempotency key
 * for consumers.
 */
public record TransferCompletedEvent(
        UUID eventId,
        UUID transferId,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency,
        Instant occurredAt
) {
    @JsonCreator
    public TransferCompletedEvent(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("transferId") UUID transferId,
            @JsonProperty("fromAccountId") UUID fromAccountId,
            @JsonProperty("toAccountId") UUID toAccountId,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currency,
            @JsonProperty("occurredAt") Instant occurredAt) {
        this.eventId = eventId;
        this.transferId = transferId;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.currency = currency;
        this.occurredAt = occurredAt;
    }
}
