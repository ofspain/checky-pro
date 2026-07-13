package com.themistra.auth.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.auth.common.Hashing;
import com.themistra.auth.events.OutboxPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * The only sanctioned way any module writes to the audit trail (target-design §15). Every call
 * both appends the durable row (INSERT-only at the DB grant level) and mirrors a reduced view
 * to Kafka via the outbox, in the same transaction as the caller's own state change.
 */
@Service
public class AuditService {

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

    @Transactional
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
