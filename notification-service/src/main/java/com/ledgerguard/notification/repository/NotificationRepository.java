package com.ledgerguard.notification.repository;

import com.ledgerguard.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
}
