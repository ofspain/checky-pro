package com.themistra.notification.inapp;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain JUnit, no Spring context, no Docker.
 */
class InappNotificationTest {

    @Test
    void toViewMapsEveryFieldExceptId() {
        UUID notificationUuid = UUID.randomUUID();
        UUID accountUuid = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-03-01T00:00:00Z");
        InappNotification notification = new InappNotification(
                notificationUuid, accountUuid, "SECURITY", "title", "body", "https://example.com/link", createdAt);

        InappNotification.View view = notification.toView();

        assertThat(view.notificationUuid()).isEqualTo(notificationUuid);
        assertThat(view.category()).isEqualTo("SECURITY");
        assertThat(view.title()).isEqualTo("title");
        assertThat(view.body()).isEqualTo("body");
        assertThat(view.link()).isEqualTo("https://example.com/link");
        assertThat(view.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void toViewHandlesANullLink() {
        InappNotification notification = new InappNotification(
                UUID.randomUUID(), UUID.randomUUID(), "SECURITY", "title", "body", null,
                Instant.parse("2026-03-01T00:00:00Z"));

        assertThat(notification.toView().link()).isNull();
    }

    @Test
    void constructorAlwaysLeavesReadAtNull() {
        InappNotification notification = new InappNotification(
                UUID.randomUUID(), UUID.randomUUID(), "SECURITY", "title", "body", null,
                Instant.parse("2026-03-01T00:00:00Z"));

        assertThat(notification.getReadAt()).isNull();
    }

    @Test
    void constructorAlwaysSetsAttemptIndependentFieldsCorrectly() {
        UUID accountUuid = UUID.randomUUID();
        InappNotification notification = new InappNotification(
                UUID.randomUUID(), accountUuid, "PAYMENT", "title", "body", null,
                Instant.parse("2026-03-01T00:00:00Z"));

        assertThat(notification.getAccountUuid()).isEqualTo(accountUuid);
        assertThat(notification.getCategory()).isEqualTo("PAYMENT");
        assertThat(notification.getId()).isNull();
    }
}
