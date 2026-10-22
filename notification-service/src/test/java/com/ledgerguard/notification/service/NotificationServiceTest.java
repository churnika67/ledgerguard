package com.ledgerguard.notification.service;

import com.ledgerguard.common.event.TransferCompletedEvent;
import com.ledgerguard.common.event.TransferRejectedEvent;
import com.ledgerguard.notification.repository.NotificationRepository;
import com.ledgerguard.notification.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private ProcessedEventRepository processedEventRepository;

    private NotificationService notificationService;

    @Test
    void completedEventCreatesSentAndReceivedNotificationsOnFirstDelivery() {
        notificationService = new NotificationService(notificationRepository, processedEventRepository);
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        TransferCompletedEvent event = new TransferCompletedEvent(eventId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), new BigDecimal("10.00"), "USD", Instant.now());

        notificationService.handleTransferCompleted(eventId, event);

        verify(notificationRepository, times(2)).save(any());
        verify(processedEventRepository, times(1)).save(any());
    }

    @Test
    void redeliveredCompletedEventIsSkippedEntirely() {
        notificationService = new NotificationService(notificationRepository, processedEventRepository);
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(true);
        TransferCompletedEvent event = new TransferCompletedEvent(eventId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), new BigDecimal("10.00"), "USD", Instant.now());

        notificationService.handleTransferCompleted(eventId, event);

        verify(notificationRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void rejectedEventCreatesExactlyOneFailureNotificationForTheSender() {
        notificationService = new NotificationService(notificationRepository, processedEventRepository);
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        TransferRejectedEvent event = new TransferRejectedEvent(eventId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), new BigDecimal("10.00"), "USD", "INSUFFICIENT_FUNDS", Instant.now());

        notificationService.handleTransferRejected(eventId, event);

        verify(notificationRepository, times(1)).save(any());
        verify(processedEventRepository, times(1)).save(any());
    }
}
