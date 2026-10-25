package com.ledgerguard.notification.service;

import com.ledgerguard.common.event.TransferCompletedEvent;
import com.ledgerguard.common.event.TransferRejectedEvent;
import com.ledgerguard.notification.api.NotificationResponse;
import com.ledgerguard.notification.domain.Notification;
import com.ledgerguard.notification.domain.NotificationType;
import com.ledgerguard.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Turns a ledger event into one or more notifications, and pushes each one to any open SSE
 * stream for its account via {@link NotificationStreamRegistry}.
 *
 * <p>Each notification's id is deterministically derived from {@code (eventId, role)} rather
 * than randomly generated. That is what makes a redelivered Kafka record (a consumer restart, a
 * rebalance, or the outbox publisher resending an unacknowledged send) safe without a separate
 * "already processed" table or a transaction wrapping multiple writes: {@code save()} on a
 * document whose id already exists upserts it, so reprocessing the same event overwrites the
 * same document with the same content instead of inserting a second one. The old relational
 * version needed a dedicated {@code processed_events} table plus a transaction around both
 * writes to get this guarantee -- MongoDB's per-document atomicity gives it here for free, at
 * the cost of the write being an overwrite rather than a true no-op.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationStreamRegistry streamRegistry;

    public NotificationService(NotificationRepository notificationRepository,
                                NotificationStreamRegistry streamRegistry) {
        this.notificationRepository = notificationRepository;
        this.streamRegistry = streamRegistry;
    }

    public void handleTransferCompleted(UUID eventId, TransferCompletedEvent event) {
        String amount = event.amount() + " " + event.currency();
        save(notificationId(eventId, "SENT"), event.fromAccountId(), event.transferId(),
                NotificationType.PAYMENT_SENT, "payment sent: " + amount);
        save(notificationId(eventId, "RECEIVED"), event.toAccountId(), event.transferId(),
                NotificationType.PAYMENT_RECEIVED, "payment received: " + amount);
    }

    public void handleTransferRejected(UUID eventId, TransferRejectedEvent event) {
        save(notificationId(eventId, "FAILED"), event.fromAccountId(), event.transferId(),
                NotificationType.PAYMENT_FAILED, "payment failed: " + event.reason());
    }

    private void save(UUID id, UUID accountId, UUID transferId, NotificationType type, String message) {
        Notification notification = new Notification(id, accountId, transferId, type, message, Instant.now());
        notificationRepository.save(notification);
        streamRegistry.publish(accountId, NotificationResponse.from(notification));
    }

    private static UUID notificationId(UUID eventId, String role) {
        return UUID.nameUUIDFromBytes((eventId + ":" + role).getBytes(StandardCharsets.UTF_8));
    }
}
