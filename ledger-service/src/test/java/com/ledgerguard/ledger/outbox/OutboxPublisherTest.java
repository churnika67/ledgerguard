package com.ledgerguard.ledger.outbox;

import com.ledgerguard.ledger.KafkaIntegrationTestBase;
import com.ledgerguard.ledger.domain.Account;
import com.ledgerguard.ledger.service.AccountService;
import com.ledgerguard.ledger.service.TransferRequest;
import com.ledgerguard.ledger.service.TransferResult;
import com.ledgerguard.ledger.service.TransferService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// This test drives OutboxPublisher.publishPending() by hand to assert on its effects
// deterministically. The real @Scheduled poll (every 500ms in application.yml) would otherwise
// race it: a tick landing between the test's own calls can publish the same event first,
// which doesn't change the end state but can flip which call the assertions see it after.
@TestPropertySource(properties = "ledger.outbox.poll-interval-ms=600000")
class OutboxPublisherTest extends KafkaIntegrationTestBase {

    @Autowired
    private AccountService accountService;
    @Autowired
    private TransferService transferService;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private OutboxPublisher outboxPublisher;

    private KafkaConsumer<String, String> consumer;

    @AfterEach
    void closeConsumer() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    void publishesExactlyOnceEvenWhenThePublisherRunsTwice() {
        Account from = accountService.open("outbox-owner-1", "USD", new BigDecimal("100.00"));
        Account to = accountService.open("outbox-owner-2", "USD", new BigDecimal("0.00"));

        TransferResult result = transferService.submit(
                new TransferRequest(from.getId(), to.getId(), new BigDecimal("40.00"), "USD"),
                "outbox-test-" + UUID.randomUUID());

        Optional<OutboxEvent> beforePublish = findByAggregateId(result.transferId());
        assertThat(beforePublish).isPresent();
        assertThat(beforePublish.get().isPublished()).isFalse();

        outboxPublisher.publishPending();

        Optional<OutboxEvent> afterPublish = findByAggregateId(result.transferId());
        assertThat(afterPublish).isPresent();
        assertThat(afterPublish.get().isPublished()).isTrue();

        consumer = buildConsumer();
        consumer.subscribe(List.of("ledger.transfers"));
        List<ConsumerRecord<String, String>> firstPoll = drain(consumer);
        assertThat(firstPoll).hasSize(1);
        assertThat(firstPoll.get(0).key()).isEqualTo(result.transferId().toString());
        assertThat(firstPoll.get(0).value()).contains(result.transferId().toString());

        // Running the publisher again must not resend an already-published event.
        outboxPublisher.publishPending();
        List<ConsumerRecord<String, String>> secondPoll = drain(consumer);
        assertThat(secondPoll).isEmpty();
    }

    private Optional<OutboxEvent> findByAggregateId(UUID aggregateId) {
        return outboxEventRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(aggregateId))
                .findFirst();
    }

    private KafkaConsumer<String, String> buildConsumer() {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }

    private List<ConsumerRecord<String, String>> drain(KafkaConsumer<String, String> consumer) {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
        return java.util.stream.StreamSupport.stream(records.records("ledger.transfers").spliterator(), false)
                .toList();
    }
}
