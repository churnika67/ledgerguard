package com.ledgerguard.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerguard.common.event.LedgerEventType;
import com.ledgerguard.common.event.TransferCompletedEvent;
import com.ledgerguard.notification.NotificationIntegrationTestBase;
import com.ledgerguard.notification.domain.Notification;
import com.ledgerguard.notification.repository.NotificationRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the consumer side of the outbox + Kafka pipeline is exactly-once in effect: even when
 * the same event is delivered twice -- simulating the outbox publisher resending an
 * unacknowledged record, or Kafka redelivering after a rebalance -- exactly one notification
 * per recipient is created, never two.
 */
class TransferEventE2ETest extends NotificationIntegrationTestBase {

    private static final String TOPIC = "ledger.transfers";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void exactlyOneNotificationPerCompletedTransferEvenWhenThePublisherSendsTheEventTwice() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        TransferCompletedEvent event = new TransferCompletedEvent(eventId, transferId, fromAccountId, toAccountId,
                new BigDecimal("50.00"), "USD", Instant.now());
        String payload = objectMapper.writeValueAsString(event);

        // Simulate the outbox publisher sending the exact same event twice (e.g. it never saw
        // the ack for the first send) by publishing it twice with the same key and headers.
        publish(eventId, transferId, payload);
        publish(eventId, transferId, payload);

        List<Notification> forTransfer = awaitNotificationsForTransfer(transferId, 2);

        assertThat(forTransfer).hasSize(2);
        assertThat(forTransfer).extracting(n -> n.getAccountId())
                .containsExactlyInAnyOrder(fromAccountId, toAccountId);

        // Give any (incorrect) second round of processing a chance to show up before asserting
        // there really is no duplicate -- reprocessing the redelivered copy should upsert the
        // same two document ids, not insert two more.
        Thread.sleep(2000);
        assertThat(notificationRepository.findAll().stream().filter(n -> n.getTransferId().equals(transferId)))
                .hasSize(2);
    }

    private void publish(UUID eventId, UUID transferId, String payload) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, transferId.toString(), payload);
        record.headers().add(new RecordHeader("eventType",
                LedgerEventType.TRANSFER_COMPLETED.name().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("eventId", eventId.toString().getBytes(StandardCharsets.UTF_8)));
        kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
    }

    private List<Notification> awaitNotificationsForTransfer(UUID transferId, int expectedCount)
            throws TimeoutException, InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(deadline)) {
            List<Notification> found = notificationRepository.findAll().stream()
                    .filter(n -> n.getTransferId().equals(transferId))
                    .toList();
            if (found.size() >= expectedCount) {
                return found;
            }
            Thread.sleep(200);
        }
        throw new TimeoutException("Notifications for transfer " + transferId + " did not arrive in time");
    }
}
