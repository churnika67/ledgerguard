package com.ledgerguard.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Holds one open {@link SseEmitter} per currently-connected client per account, so a notification
 * can be pushed to the browser the instant it's created instead of the client having to poll.
 *
 * <p>Purely in-memory, scoped to this one instance -- fine for this single-instance demo
 * deployment. A multi-instance deployment would need to fan published notifications out to every
 * instance (e.g. via Redis pub/sub) since a client's long-lived connection only ever lands on one
 * instance at a time.
 */
@Component
public class NotificationStreamRegistry {

    private static final Logger log = LoggerFactory.getLogger(NotificationStreamRegistry.class);

    private final ConcurrentHashMap<UUID, Set<SseEmitter>> emittersByAccount = new ConcurrentHashMap<>();

    public SseEmitter register(UUID accountId) {
        SseEmitter emitter = new SseEmitter(0L);
        Set<SseEmitter> emitters = emittersByAccount.computeIfAbsent(accountId, id -> new CopyOnWriteArraySet<>());
        emitters.add(emitter);

        Runnable deregister = () -> emitters.remove(emitter);
        emitter.onCompletion(deregister);
        emitter.onTimeout(deregister);
        emitter.onError(ex -> deregister.run());

        return emitter;
    }

    public void publish(UUID accountId, Object payload) {
        Set<SseEmitter> emitters = emittersByAccount.get(accountId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(payload));
            } catch (IOException e) {
                log.debug("Dropping a stale SSE connection for account {}", accountId);
                emitters.remove(emitter);
            }
        }
    }
}
