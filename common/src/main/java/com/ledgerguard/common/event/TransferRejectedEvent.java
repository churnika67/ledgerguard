package com.ledgerguard.common.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when a transfer request is rejected (insufficient funds, currency mismatch,
 * same-account transfer). No money ever moved for this transfer.
 */
public record TransferRejectedEvent(
        UUID eventId,
        UUID transferId,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency,
        String reason,
        Instant occurredAt
) {
    @JsonCreator
    public TransferRejectedEvent(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("transferId") UUID transferId,
            @JsonProperty("fromAccountId") UUID fromAccountId,
            @JsonProperty("toAccountId") UUID toAccountId,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currency,
            @JsonProperty("reason") String reason,
            @JsonProperty("occurredAt") Instant occurredAt) {
        this.eventId = eventId;
        this.transferId = transferId;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.currency = currency;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }
}
