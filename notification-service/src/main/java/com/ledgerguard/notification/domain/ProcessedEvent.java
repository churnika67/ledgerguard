package com.ledgerguard.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Marks one outbox event id as already handled. Written in the same transaction as the
 * notifications it produced, so a redelivered Kafka record (consumer restart, rebalance, or the
 * publisher legitimately resending an unacknowledged send) is recognized and skipped instead of
 * creating duplicate notifications.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    private UUID eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
        // JPA
    }

    public ProcessedEvent(UUID eventId, Instant processedAt) {
        this.eventId = eventId;
        this.processedAt = processedAt;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
