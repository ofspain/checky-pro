package com.themistra.auth.audit.dto;

import com.themistra.auth.audit.AuditOutcome;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Admin/compliance-facing view of an audit row. userAgentHash is included (it's already a
 * one-way hash, not raw PII); the details map is the deserialized form of the stored JSON.
 */
public record AuditEventResponse(
        Long id,
        Instant occurredAt,
        String eventType,
        AuditOutcome outcome,
        UUID accountUuid,
        UUID actorUuid,
        String ip,
        String userAgentHash,
        String traceId,
        Map<String, Object> details
) {
}
