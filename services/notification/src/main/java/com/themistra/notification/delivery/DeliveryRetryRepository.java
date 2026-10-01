package com.themistra.notification.delivery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/**
 * Package-private, matching every sibling repository in this module ({@code DeliveryLogRepository},
 * {@code TemplateRepository}, {@code ChannelPreferenceRepository}, {@code ProcessedEventRepository}) -
 * consumed only from within {@code delivery/}, by {@code DeliveryOrchestrator} and
 * {@code RetryScheduler}.
 *
 * <p>Ordered oldest-due-first with {@code id} as a deterministic tie-break (Phase 3 Finding #10) -
 * without an explicit order, a backlog of due rows would be processed arbitrarily.</p>
 */
interface DeliveryRetryRepository extends JpaRepository<DeliveryRetry, Long> {

    List<DeliveryRetry> findByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(Instant now);
}
