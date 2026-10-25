package com.ledgerguard.notification.repository;

import com.ledgerguard.notification.domain.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends MongoRepository<Notification, UUID> {

    List<Notification> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
}
