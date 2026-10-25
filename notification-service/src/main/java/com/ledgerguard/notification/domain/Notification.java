package com.ledgerguard.notification.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * A "payment sent/received" or "payment failed" message surfaced for one account.
 *
 * <p>The id is not a random UUID -- see {@link com.ledgerguard.notification.service.NotificationService}
 * for why it's deterministically derived from the source event, which is what makes a redelivered
 * Kafka record an idempotent no-op rather than a duplicate.
 */
@Document(collection = "notifications")
public class Notification {

    @Id
    private UUID id;

    @Indexed
    private UUID accountId;

    private UUID transferId;

    private NotificationType type;

    private String message;

    private Instant createdAt;

    protected Notification() {
        // Spring Data
    }

    public Notification(UUID id, UUID accountId, UUID transferId, NotificationType type, String message,
                         Instant createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.transferId = transferId;
        this.type = type;
        this.message = message;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
