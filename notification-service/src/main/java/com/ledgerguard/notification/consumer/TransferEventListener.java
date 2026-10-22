package com.ledgerguard.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerguard.common.event.LedgerEventType;
import com.ledgerguard.common.event.TransferCompletedEvent;
import com.ledgerguard.common.event.TransferRejectedEvent;
import com.ledgerguard.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes {@code ledger.transfers}, turning each event into notifications via
 * {@link NotificationService}. Failures are retried with backoff on auto-created retry topics;
 * once attempts are exhausted the record lands on {@code ledger.transfers.DLT} and is logged by
 * {@link #handleDlt}.
 */
@Component
@ConditionalOnProperty(prefix = "ledger.kafka", name = "enabled", matchIfMissing = true)
public class TransferEventListener {

    private static final Logger log = LoggerFactory.getLogger(TransferEventListener.class);

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public TransferEventListener(NotificationService notificationService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 500, multiplier = 2.0),
            dltTopicSuffix = ".DLT",
            autoCreateTopics = "true")
    @KafkaListener(topics = "ledger.transfers", groupId = "notifications")
    public void onTransferEvent(@Payload String payload,
                                 @Header("eventType") byte[] eventTypeHeader,
                                 @Header("eventId") byte[] eventIdHeader) throws Exception {
        String eventType = new String(eventTypeHeader);
        UUID eventId = UUID.fromString(new String(eventIdHeader));

        if (LedgerEventType.TRANSFER_COMPLETED.name().equals(eventType)) {
            notificationService.handleTransferCompleted(eventId, objectMapper.readValue(payload,
                    TransferCompletedEvent.class));
        } else if (LedgerEventType.TRANSFER_REJECTED.name().equals(eventType)) {
            notificationService.handleTransferRejected(eventId, objectMapper.readValue(payload,
                    TransferRejectedEvent.class));
        } else {
            log.warn("Ignoring ledger.transfers record with unknown eventType header: {}", eventType);
        }
    }

    @DltHandler
    public void handleDlt(@Payload String payload, @Header("eventId") byte[] eventIdHeader) {
        log.error("Event {} exhausted retries and landed on the dead-letter topic. Payload: {}",
                new String(eventIdHeader), payload);
    }
}
