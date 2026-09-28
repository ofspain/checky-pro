package com.themistra.notification.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.notification.consumer.dto.EmailRequestedEvent;
import com.themistra.notification.consumer.dto.UserLifecycleEvent;
import com.themistra.notification.preference.ContactProjectionUpdater;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * The first real {@code @KafkaListener} in this codebase - consumes {@code auth.email.requested}
 * and {@code auth.user.lifecycle}, dedupes via {@link IdempotencyGuard} (L1, T04), refreshes the
 * recipient projection via {@link ContactProjectionUpdater} (T05), then hands off to
 * {@link NotificationDispatcher} (the seam a later task implements for real).
 *
 * <p>Both listener methods consume the raw Kafka value as {@code String} - mirrors
 * {@code services/auth}'s own producer-side {@code KafkaTemplate<String, String>} convention
 * exactly, no Spring-Kafka type-mapped JSON deserializer - then deserialize via Spring Boot's own
 * autoconfigured {@link ObjectMapper} (already registers {@code jackson-datatype-jsr310}
 * transitively, so {@code Instant} fields deserialize correctly with no extra configuration).</p>
 *
 * <p>Both methods are {@code @Transactional} (Kimi Phase 3 Finding #3): dedupe, projection-refresh,
 * and dispatch run in one atomic transaction, so a future failing {@code dispatch} rolls back the
 * idempotency record too - a failed delivery attempt is correctly retried on redelivery, not
 * silently marked processed.</p>
 *
 * <p>Idempotency key format (Kimi Phase 3 Finding #1, frozen brief AC7): {@code accountUuid + ":"
 * + purpose/eventType + ":" + occurredAt}, where {@code occurredAt} is the deserialized
 * {@code Instant}'s own {@code toString()} (ISO-8601 UTC) - stable and byte-identical across
 * redeliveries of the same payload, payload-only (never Kafka's own offset/partition, which isn't
 * stable across rebalance).</p>
 */
@Component
public class AuthEventConsumer {

    private final ObjectMapper objectMapper;
    private final IdempotencyGuard idempotencyGuard;
    private final ContactProjectionUpdater contactProjectionUpdater;
    private final NotificationDispatcher notificationDispatcher;

    public AuthEventConsumer(ObjectMapper objectMapper, IdempotencyGuard idempotencyGuard,
                              ContactProjectionUpdater contactProjectionUpdater,
                              NotificationDispatcher notificationDispatcher) {
        this.objectMapper = objectMapper;
        this.idempotencyGuard = idempotencyGuard;
        this.contactProjectionUpdater = contactProjectionUpdater;
        this.notificationDispatcher = notificationDispatcher;
    }

    /**
     * Unknown {@code purpose} values (the contract's own {@code purpose} field is deliberately an
     * open string, not a closed enum) are dedup-recorded and projection-refreshed but not
     * dispatched (Kimi Phase 3 Finding #2) - R1/R2 only cover {@code verify_email}/
     * {@code password_reset}; handing an unrecognized kind to a downstream task not yet equipped
     * to reject it would be worse than simply not dispatching it.
     */
    @KafkaListener(topics = "auth.email.requested", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onEmailRequested(String rawJson) throws JsonProcessingException {
        EmailRequestedEvent event = objectMapper.readValue(rawJson, EmailRequestedEvent.class);
        String eventKey = event.accountUuid() + ":" + event.purpose() + ":" + event.occurredAt();
        if (!idempotencyGuard.recordIfNew(eventKey, event.purpose())) {
            return;
        }

        contactProjectionUpdater.upsertEmail(event.accountUuid(), event.email(), event.occurredAt());

        String notificationKind = switch (event.purpose()) {
            case "verify_email" -> "verify_email";
            case "password_reset" -> "password_reset";
            default -> null;
        };
        if (notificationKind == null) {
            return;
        }
        notificationDispatcher.dispatch(event.accountUuid(), notificationKind, Map.of("token", event.token()));
    }

    /**
     * Only {@code eventType = "user.registered"} is dispatched (R6). The other 5 known values
     * ({@code user.suspended}, {@code user.reinstated}, {@code user.deleted}, {@code user.locked},
     * {@code user.unlocked}) are dedup-recorded and projection-refreshed but not dispatched -
     * {@code eventType}, not {@code status}, is the real, unambiguous signal for this distinction
     * (Kimi Phase 3 Finding #1 predecessor: {@code status=ACTIVE} alone cannot tell
     * {@code user.registered} apart from {@code user.reinstated}/{@code user.unlocked}).
     */
    @KafkaListener(topics = "auth.user.lifecycle", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onUserLifecycle(String rawJson) throws JsonProcessingException {
        UserLifecycleEvent event = objectMapper.readValue(rawJson, UserLifecycleEvent.class);
        String eventKey = event.accountUuid() + ":" + event.eventType() + ":" + event.occurredAt();
        if (!idempotencyGuard.recordIfNew(eventKey, event.eventType())) {
            return;
        }

        contactProjectionUpdater.upsertEmail(event.accountUuid(), event.email(), event.occurredAt());

        if (!"user.registered".equals(event.eventType())) {
            return;
        }
        notificationDispatcher.dispatch(event.accountUuid(), "user.registered", Map.of());
    }
}
