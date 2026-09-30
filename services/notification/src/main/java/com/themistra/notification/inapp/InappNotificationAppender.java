package com.themistra.notification.inapp;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

/**
 * The single, sanctioned gateway into this module's own persistence and live-push machinery
 * (Kimi Phase 8 Finding #1, self-review Finding #1) - {@code InAppChannel} (package {@code channel})
 * depends only on this class, never on {@link InappNotification}/{@link InappNotificationRepository}/
 * {@link InappStreamRegistry} directly. Mirrors {@code ContactProjectionUpdater}'s own established
 * role for {@code ContactProjectionRepository} exactly: a public, same-package service returning/
 * accepting only primitives across the module boundary, never leaking the entity type itself (L11 -
 * "no feature module imports another feature module's entity"). Folding the live-push call in here
 * too (Kimi Phase 8 Finding #9) means {@code InAppChannel} depends on exactly one {@code inapp}
 * collaborator, not two.
 */
@Component
public class InappNotificationAppender {

    private final InappNotificationRepository repository;
    private final InappStreamRegistry streamRegistry;

    public InappNotificationAppender(InappNotificationRepository repository, InappStreamRegistry streamRegistry) {
        this.repository = repository;
        this.streamRegistry = streamRegistry;
    }

    /**
     * {@code @Transactional} (default {@code REQUIRED}, Kimi Phase 3 Finding #6) - joins
     * {@code DeliveryOrchestrator.dispatch}'s own already-open transaction (T11), mirroring
     * {@code ContactProjectionUpdater.upsertEmail}'s own established "explicit
     * {@code @Transactional} on every write-path method" precedent (T05). The live push is deferred
     * until <strong>after</strong> that transaction commits - a client can never receive a live push
     * for a row {@code InappReadController}'s own unread endpoint can't yet see.
     */
    @Transactional
    public void appendAndPush(UUID accountUuid, String category, String title, String body, Instant createdAt) {
        InappNotification notification = new InappNotification(
                UUID.randomUUID(), accountUuid, category, title, body, null, createdAt);
        repository.save(notification);

        pushAfterCommit(accountUuid, notification.toView());
    }

    private void pushAfterCommit(UUID accountUuid, InappNotification.View view) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    streamRegistry.push(accountUuid, "notification", view);
                }
            });
        } else {
            streamRegistry.push(accountUuid, "notification", view);
        }
    }
}
