package com.themistra.auth.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.auth.audit.dto.AuditEventResponse;
import com.themistra.auth.common.Hashing;
import com.themistra.auth.events.OutboxPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The only sanctioned way any module writes to the audit trail (target-design §15). Every call
 * both appends the durable row (INSERT-only at the DB grant level) and mirrors a reduced view
 * to Kafka via the outbox, in the same transaction as the caller's own state change.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int SCHEMA_VERSION = 1;

    private final AuditEventRepository repository;
    private final OutboxPublisher outboxPublisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditService(AuditEventRepository repository, OutboxPublisher outboxPublisher,
                        ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.outboxPublisher = outboxPublisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * {@code REQUIRES_NEW} (T18 Phase 9 finding, changed from the default {@code REQUIRED}):
     * every caller that records a FAILURE outcome and then throws within the same transaction
     * (introduced by {@code MfaService}'s confirm/disable/verifyRecoveryCode failure paths) would
     * otherwise have this audit row silently rolled back along with the exception that follows
     * it — defeating the entire point of a failure audit. Committing independently means an audit
     * row can now persist even if the caller's own transaction subsequently rolls back for an
     * unrelated reason; accepted trade-off (a slightly-early-committed audit event is preferable
     * to a required-by-spec failure record that never exists at all).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(RecordAuditEventRequest request) {
        Instant now = clock.instant();
        String userAgentHash = request.rawUserAgent() == null
                ? null
                : Hashing.sha256(request.rawUserAgent());

        AuditEvent event = AuditEvent.record(
                now, request.eventType(), request.outcome(),
                request.accountUuid(), request.actorUuid(), request.ip(),
                userAgentHash, request.traceId(), serialize(request));
        repository.save(event);

        outboxPublisher.publish(
                "audit",
                partitionKey(request.accountUuid()),
                "security." + request.eventType(),
                SCHEMA_VERSION,
                new AuditMirrorPayload(
                        request.eventType(), request.outcome(),
                        request.accountUuid(), request.actorUuid(), now));
    }

    @Transactional(readOnly = true)
    public Page<AuditEventResponse> list(UUID accountUuidFilter, Pageable pageable) {
        Page<AuditEvent> page = accountUuidFilter != null
                ? repository.findByAccountUuid(accountUuidFilter, pageable)
                : repository.findAll(pageable);
        return page.map(this::toResponse);
    }

    private AuditEventResponse toResponse(AuditEvent event) {
        return new AuditEventResponse(
                event.getId(), event.getOccurredAt(), event.getEventType(), event.getOutcome(),
                event.getAccountUuid(), event.getActorUuid(), event.getIp(),
                event.getUserAgentHash(), event.getTraceId(), deserializeDetails(event));
    }

    private Map<String, Object> deserializeDetails(AuditEvent event) {
        if (event.getDetails() == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(event.getDetails(), new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException e) {
            // a previously-written row failing to parse is a bug worth knowing about, but must
            // never break the audit browser itself
            log.error("Failed to deserialize audit details for event {}", event.getId(), e);
            return Map.of();
        }
    }

    private String partitionKey(UUID accountUuid) {
        // account-scoped events keep one user's audit trail ordered; account-less (system)
        // events have no natural key, so partitioning doesn't matter for them
        return accountUuid != null ? accountUuid.toString() : UUID.randomUUID().toString();
    }

    private String serialize(RecordAuditEventRequest request) {
        try {
            return objectMapper.writeValueAsString(request.details());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize audit details for " + request.eventType(), e);
        }
    }
}
