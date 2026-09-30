package com.themistra.notification.inapp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * {@code public}, unlike every sibling repository in this module ({@code TemplateRepository},
 * {@code ChannelPreferenceRepository}, {@code ProcessedEventRepository},
 * {@code DeliveryLogRepository} - all package-private, consumed only from within their own
 * package). This is a distinct case, not an inconsistency: {@code InAppChannel} (package
 * {@code channel}) is a genuine, non-test, production consumer needing direct write access, and no
 * same-package wrapper class (mirroring {@code ContactProjectionUpdater}'s own role for
 * {@code ContactProjectionRepository}) was authorized for this task. This is the first repository
 * in this module with a real cross-package consumer.
 */
public interface InappNotificationRepository extends JpaRepository<InappNotification, Long> {

    List<InappNotification> findByAccountUuidAndReadAtIsNullOrderByCreatedAtDesc(UUID accountUuid);
}
