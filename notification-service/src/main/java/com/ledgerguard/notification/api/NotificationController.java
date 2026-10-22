package com.ledgerguard.notification.api;

import com.ledgerguard.notification.repository.NotificationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "List notifications for an account, most recent first")
    public List<NotificationResponse> list(@PathVariable UUID accountId) {
        return notificationRepository.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(NotificationResponse::from)
                .toList();
    }
}
