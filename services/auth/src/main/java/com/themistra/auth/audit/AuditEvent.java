package com.themistra.auth.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only by convention and by DB grant (target-design §15: the service's DB role has
 * INSERT + SELECT only on auth_audit — no UPDATE/DELETE). Nothing in this codebase ever
 * mutates a saved row.
 */
@Entity
@Table(name = "auth_audit")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 16)
    private AuditOutcome outcome;

    @Column(name = "account_uuid")
    private UUID accountUuid;

    @Column(name = "actor_uuid")
    private UUID actorUuid;

    @Column(name = "ip", length = 45)
    private String ip;

    /** CHAR(64), not VARCHAR - JdbcTypeCode.CHAR matches how Postgres reports this column's type
     * (bpchar) so Hibernate's schema validation accepts it. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "user_agent_hash", length = 64, columnDefinition = "char(64)")
    private String userAgentHash;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details")
    private String details;

    protected AuditEvent() {
        // JPA only
    }

    public static AuditEvent record(Instant occurredAt, String eventType, AuditOutcome outcome,
                                    UUID accountUuid, UUID actorUuid, String ip,
                                    String userAgentHash, String traceId, String detailsJson) {
        AuditEvent event = new AuditEvent();
        event.occurredAt = occurredAt;
        event.eventType = eventType;
        event.outcome = outcome;
        event.accountUuid = accountUuid;
        event.actorUuid = actorUuid;
        event.ip = ip;
        event.userAgentHash = userAgentHash;
        event.traceId = traceId;
        event.details = detailsJson;
        return event;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getEventType() {
        return eventType;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public UUID getAccountUuid() {
        return accountUuid;
    }

    public UUID getActorUuid() {
        return actorUuid;
    }

    public String getIp() {
        return ip;
    }

    public String getUserAgentHash() {
        return userAgentHash;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getDetails() {
        return details;
    }
}
