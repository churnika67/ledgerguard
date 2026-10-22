package com.ledgerguard.notification.service;

import com.ledgerguard.common.event.TransferCompletedEvent;
import com.ledgerguard.common.event.TransferRejectedEvent;
import com.ledgerguard.notification.domain.Notification;
import com.ledgerguard.notification.domain.NotificationType;
import com.ledgerguard.notification.domain.ProcessedEvent;
import com.ledgerguard.notification.repository.NotificationRepository;
import com.ledgerguard.notification.repository.ProcessedEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Turns a ledger event into one or more notifications. Every method here first checks
 * {@code eventId} against {@link ProcessedEventRepository} and, in the same transaction, writes
 * the resulting notifications plus a {@link ProcessedEvent} row marking the event handled --
 * so a redelivered Kafka record is a no-op rather than a duplicate notification.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ProcessedEventRepository processedEventRepository;

    public NotificationService(NotificationRepository notificationRepository,
                                ProcessedEventRepository processedEventRepository) {
        this.notificationRepository = notificationRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    public void handleTransferCompleted(UUID eventId, TransferCompletedEvent event) {
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        String amount = event.amount() + " " + event.currency();
        notificationRepository.save(new Notification(UUID.randomUUID(), event.fromAccountId(), event.transferId(),
                NotificationType.PAYMENT_SENT, "payment sent: " + amount, Instant.now()));
        notificationRepository.save(new Notification(UUID.randomUUID(), event.toAccountId(), event.transferId(),
                NotificationType.PAYMENT_RECEIVED, "payment received: " + amount, Instant.now()));

        processedEventRepository.save(new ProcessedEvent(eventId, Instant.now()));
    }

    @Transactional
    public void handleTransferRejected(UUID eventId, TransferRejectedEvent event) {
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        notificationRepository.save(new Notification(UUID.randomUUID(), event.fromAccountId(), event.transferId(),
                NotificationType.PAYMENT_FAILED, "payment failed: " + event.reason(), Instant.now()));

        processedEventRepository.save(new ProcessedEvent(eventId, Instant.now()));
    }
}
