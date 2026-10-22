package com.ledgerguard.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Both services deserialize these records independently (ledger-service writes them to the
 * outbox payload, notification-service reads them back off Kafka), so a round trip through the
 * same JSON shape they'll actually see on the wire is worth covering here once, centrally.
 */
class EventSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void transferCompletedEventRoundTripsThroughJson() throws Exception {
        TransferCompletedEvent original = new TransferCompletedEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("12.50"), "USD", Instant.now());

        String json = objectMapper.writeValueAsString(original);
        TransferCompletedEvent roundTripped = objectMapper.readValue(json, TransferCompletedEvent.class);

        assertThat(roundTripped).isEqualTo(original);
    }

    @Test
    void transferRejectedEventRoundTripsThroughJson() throws Exception {
        TransferRejectedEvent original = new TransferRejectedEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("12.50"), "USD", "INSUFFICIENT_FUNDS",
                Instant.now());

        String json = objectMapper.writeValueAsString(original);
        TransferRejectedEvent roundTripped = objectMapper.readValue(json, TransferRejectedEvent.class);

        assertThat(roundTripped).isEqualTo(original);
    }
}
