package com.ledgerguard.ledger.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Ships unpublished {@link OutboxEvent} rows to the {@code ledger.transfers} topic, keyed by
 * transfer id so all events for one transfer land on the same partition. {@code publishedAt} is
 * stamped only after the Kafka broker acknowledges the record, so a crash between "sent" and
 * "marked published" simply causes the next poll to resend it; the idempotent producer
 * (enable.idempotence=true, acks=all) guarantees that resend is never duplicated as a distinct
 * record within its own producer session, and the notification-service's idempotent consumer
 * handles any redelivery beyond that.
 */
@Component
@ConditionalOnProperty(prefix = "ledger.kafka", name = "enabled", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final int batchSize;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository, KafkaTemplate<String, String> kafkaTemplate,
                            @Value("${ledger.outbox.publish-topic}") String topic,
                            @Value("${ledger.outbox.batch-size}") int batchSize) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${ledger.outbox.poll-interval-ms}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outboxEventRepository.findUnpublished(PageRequest.of(0, batchSize));
        for (OutboxEvent event : batch) {
            publishOne(event);
        }
    }

    private void publishOne(OutboxEvent event) {
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, event.getAggregateId().toString(),
                    event.getPayload());
            record.headers().add(new RecordHeader("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8)));
            record.headers().add(new RecordHeader("eventId", event.getId().toString().getBytes(StandardCharsets.UTF_8)));

            kafkaTemplate.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            event.markPublished(Instant.now());
            outboxEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Failed to publish outbox event {}, will retry on the next poll", event.getId(), e);
        }
    }
}
