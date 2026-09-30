package com.themistra.notification.inapp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Package-private, matching every sibling repository in this module ({@code TemplateRepository},
 * {@code ChannelPreferenceRepository}, {@code ProcessedEventRepository},
 * {@code DeliveryLogRepository}). This was briefly {@code public} at Phase 6 to let
 * {@code InAppChannel} (package {@code channel}) call it directly - a genuine L11 violation
 * (Kimi Phase 8 Finding #1, self-review Finding #1), fixed at Phase 9 by introducing
 * {@link InappNotificationAppender} as the sanctioned same-package gateway. Consumed only from
 * within this package now (this repository itself, plus {@code InappReadController}).
 */
interface InappNotificationRepository extends JpaRepository<InappNotification, Long> {

    List<InappNotification> findByAccountUuidAndReadAtIsNullOrderByCreatedAtDesc(UUID accountUuid);
}
