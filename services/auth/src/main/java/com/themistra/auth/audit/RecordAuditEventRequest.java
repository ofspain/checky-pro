package com.themistra.auth.audit;

import java.util.Map;
import java.util.UUID;

/**
 * Internal service-to-service call, not an external API input — no bean validation here;
 * validation belongs at system boundaries, and every field here is caller-constructed, not
 * user-supplied. {@code rawUserAgent} is hashed by {@link AuditService}; it is never persisted
 * or logged in the clear.
 */
public record RecordAuditEventRequest(
        String eventType,
        AuditOutcome outcome,
        UUID accountUuid,
        UUID actorUuid,
        String ip,
        String rawUserAgent,
        String traceId,
        Map<String, Object> details
) {
    public RecordAuditEventRequest {
        details = details == null ? Map.of() : details;
    }
}
