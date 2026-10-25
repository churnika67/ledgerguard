package com.ledgerguard.notification.service;

import com.ledgerguard.common.event.TransferCompletedEvent;
import com.ledgerguard.common.event.TransferRejectedEvent;
import com.ledgerguard.notification.domain.Notification;
import com.ledgerguard.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    // Hand-rolled rather than @Mock: NotificationStreamRegistry is a concrete class, and
    // Mockito's inline mock maker needs bytecode retransformation that doesn't yet support every
    // JDK build -- a plain subclass sidesteps that entirely and is just as easy to assert on.
    private final RecordingStreamRegistry streamRegistry = new RecordingStreamRegistry();

    private NotificationService notificationService;

    @Test
    void completedEventCreatesSentAndReceivedNotifications() {
        notificationService = new NotificationService(notificationRepository, streamRegistry);
        UUID eventId = UUID.randomUUID();
        TransferCompletedEvent event = new TransferCompletedEvent(eventId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), new BigDecimal("10.00"), "USD", Instant.now());

        notificationService.handleTransferCompleted(eventId, event);

        verify(notificationRepository, times(2)).save(any());
        assertThat(streamRegistry.published).hasSize(2);
    }

    @Test
    void redeliveredCompletedEventUpsertsTheSameTwoDocumentsRatherThanCreatingNewOnes() {
        notificationService = new NotificationService(notificationRepository, streamRegistry);
        UUID eventId = UUID.randomUUID();
        TransferCompletedEvent event = new TransferCompletedEvent(eventId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), new BigDecimal("10.00"), "USD", Instant.now());

        notificationService.handleTransferCompleted(eventId, event);
        notificationService.handleTransferCompleted(eventId, event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(4)).save(captor.capture());

        assertThat(captor.getAllValues().get(0).getId()).isEqualTo(captor.getAllValues().get(2).getId());
        assertThat(captor.getAllValues().get(1).getId()).isEqualTo(captor.getAllValues().get(3).getId());
    }

    @Test
    void rejectedEventCreatesExactlyOneFailureNotificationForTheSender() {
        notificationService = new NotificationService(notificationRepository, streamRegistry);
        UUID eventId = UUID.randomUUID();
        TransferRejectedEvent event = new TransferRejectedEvent(eventId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), new BigDecimal("10.00"), "USD", "INSUFFICIENT_FUNDS", Instant.now());

        notificationService.handleTransferRejected(eventId, event);

        verify(notificationRepository, times(1)).save(any());
        assertThat(streamRegistry.published).hasSize(1);
    }

    private static final class RecordingStreamRegistry extends NotificationStreamRegistry {
        final List<Object> published = new ArrayList<>();

        @Override
        public void publish(UUID accountId, Object payload) {
            published.add(payload);
        }
    }
}
