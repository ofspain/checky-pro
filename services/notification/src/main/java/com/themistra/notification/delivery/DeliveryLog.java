package com.themistra.notification.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per delivery attempt on one channel - maps onto {@code notifications.delivery_log},
 * already migrated and already granted {@code INSERT, SELECT} to {@code notification_app} (T02).
 * Append-only (L3): a retry adds a new row, never overwrites a prior attempt - unlike every other
 * entity in this module, this one is genuinely constructed and persisted by application code, not
 * read-only.
 *
 * <p>{@code attempt} is a real constructor parameter (T14) - the original attempt is always
 * {@code 1}; a replay (see {@code DeliveryOrchestrator.replay}) passes the real, incremented
 * number. Was hardcoded to {@code 1} before T14, per this class's own now-resolved Javadoc note.</p>
 */
@Entity
@Table(name = "delivery_log", schema = "notifications")
public class DeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_uuid")
    private UUID accountUuid;

    @Column(name = "recipient")
    private String recipient;

    @Column(name = "channel", nullable = false)
    private String channel;

    @Column(name = "source_event_key", nullable = false)
    private String sourceEventKey;

    @Column(name = "template_name")
    private String templateName;

    @Column(name = "template_version")
    private Integer templateVersion;

    @Column(name = "attempt", nullable = false)
    private short attempt;

    @Column(name = "outcome", nullable = false)
    private String outcome;

    @Column(name = "error_detail")
    private String errorDetail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DeliveryLog() {
        // JPA only
    }

    public DeliveryLog(UUID accountUuid, String recipient, String channel, String sourceEventKey,
                        String templateName, Integer templateVersion, short attempt, String outcome,
                        String errorDetail, Instant createdAt) {
        this.accountUuid = accountUuid;
        this.recipient = recipient;
        this.channel = channel;
        this.sourceEventKey = sourceEventKey;
        this.templateName = templateName;
        this.templateVersion = templateVersion;
        this.attempt = attempt;
        this.outcome = outcome;
        this.errorDetail = errorDetail;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getAccountUuid() {
        return accountUuid;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getChannel() {
        return channel;
    }

    public String getSourceEventKey() {
        return sourceEventKey;
    }

    public String getTemplateName() {
        return templateName;
    }

    public Integer getTemplateVersion() {
        return templateVersion;
    }

    public short getAttempt() {
        return attempt;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getErrorDetail() {
        return errorDetail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
