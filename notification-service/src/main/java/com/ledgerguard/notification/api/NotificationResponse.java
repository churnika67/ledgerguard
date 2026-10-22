package com.ledgerguard.notification.api;

import com.ledgerguard.notification.domain.Notification;
import com.ledgerguard.notification.domain.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, UUID accountId, UUID transferId, NotificationType type, String message,
                                     Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getAccountId(),
                notification.getTransferId(), notification.getType(), notification.getMessage(),
                notification.getCreatedAt());
    }
}
