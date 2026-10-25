package com.ledgerguard.notification.api;

import com.ledgerguard.notification.repository.NotificationRepository;
import com.ledgerguard.notification.service.NotificationStreamRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final NotificationStreamRegistry streamRegistry;

    public NotificationController(NotificationRepository notificationRepository,
                                   NotificationStreamRegistry streamRegistry) {
        this.notificationRepository = notificationRepository;
        this.streamRegistry = streamRegistry;
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "List notifications for an account, most recent first")
    public List<NotificationResponse> list(@PathVariable UUID accountId) {
        return notificationRepository.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @GetMapping(path = "/{accountId}/stream", produces = "text/event-stream")
    @Operation(summary = "Subscribe to live notifications for an account via Server-Sent Events")
    public SseEmitter stream(@PathVariable UUID accountId) {
        return streamRegistry.register(accountId);
    }
}
