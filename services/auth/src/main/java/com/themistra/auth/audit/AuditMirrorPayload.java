package com.themistra.auth.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * The Kafka mirror body for auth.security.audit (target-design §12). Deliberately narrower than
 * the stored row: IP and the hashed user agent stay in the database only — downstream consumers
 * (Notification, the Phase 2+ intelligence engine) get the security-relevant facts, not the
 * forensic detail, minimizing what leaves the service by default.
 */
public record AuditMirrorPayload(
        String eventType,
        AuditOutcome outcome,
        UUID accountUuid,
        UUID actorUuid,
        Instant occurredAt
) {
}
