package com.themistra.notification.inapp;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Reads a recipient's unread in-app notifications (R17). Added in the open-issues sweep
 * (2026-10-20, FU-1 layering): {@link InappReadController} used to call
 * {@link InappNotificationRepository} itself; a controller now always goes through a service.
 */
@Service
public class InappNotificationReader {

    private final InappNotificationRepository repository;

    InappNotificationReader(InappNotificationRepository repository) {
        this.repository = repository;
    }

    /** Newest first, only {@code read_at IS NULL}, only this account's rows. */
    @Transactional(readOnly = true)
    public List<InappNotification.View> unreadFor(UUID accountUuid) {
        Objects.requireNonNull(accountUuid, "accountUuid must not be null");
        return repository.findByAccountUuidAndReadAtIsNullOrderByCreatedAtDesc(accountUuid).stream()
                .map(InappNotification::toView)
                .toList();
    }
}
