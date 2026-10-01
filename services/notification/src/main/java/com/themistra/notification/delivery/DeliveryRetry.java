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
 * One scheduled retry for one channel/event - maps onto {@code notifications.delivery_retry}
 * (T14, L7, R12/R13). The <strong>one genuinely mutable entity in this module</strong>: every
 * sibling ({@code DeliveryLog}, {@code InappNotification}, {@code ProcessedEvent}) is an
 * append-only or read-only record of something that already happened, but this row is a live
 * scheduling queue entry - {@code RetryScheduler} mutates it in place via {@link #reschedule} on a
 * transient failure, and deletes it entirely once resolved (sent, suppressed, permanently failed,
 * or exhausted).
 *
 * <p>{@code eventDataJson} carries the same raw event data (including a one-time verification/
 * reset token, where applicable) the original Kafka event and rendered message already did - not a
 * new class of exposure, but a new persistence surface with a bounded lifetime (deleted once this
 * row resolves). {@link #toString()} is deliberately left as {@code Object}'s own default (no field
 * interpolation) so a careless future {@code log.info("{}", retry)} can never print it.</p>
 */
@Entity
@Table(name = "delivery_retry", schema = "notifications")
public class DeliveryRetry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_event_key", nullable = false)
    private String sourceEventKey;

    @Column(name = "account_uuid")
    private UUID accountUuid;

    @Column(name = "channel", nullable = false)
    private String channel;

    @Column(name = "notification_kind", nullable = false)
    private String notificationKind;

    @Column(name = "event_data_json", nullable = false)
    private String eventDataJson;

    @Column(name = "attempt", nullable = false)
    private short attempt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DeliveryRetry() {
        // JPA only
    }

    public DeliveryRetry(String sourceEventKey, UUID accountUuid, String channel, String notificationKind,
                          String eventDataJson, short attempt, Instant nextAttemptAt, Instant createdAt) {
        this.sourceEventKey = sourceEventKey;
        this.accountUuid = accountUuid;
        this.channel = channel;
        this.notificationKind = notificationKind;
        this.eventDataJson = eventDataJson;
        this.attempt = attempt;
        this.nextAttemptAt = nextAttemptAt;
        this.createdAt = createdAt;
    }

    /** The one deliberate mutator in this module (Phase 3 Finding #5's own pinned semantics) -
     * called only by {@code RetryScheduler} on a {@code TRANSIENT_FAILURE} outcome. The instance
     * this mutates is typically detached (fetched by {@code sweep()} outside this method's own
     * transaction) - the caller must still call {@code DeliveryRetryRepository.save(...)} (a merge)
     * afterward; dirty-checking alone does not persist a change to a detached entity. Confirmed by a
     * real test failure before {@code RetryScheduler} added that explicit save, not assumed safe. */
    public void reschedule(short attempt, Instant nextAttemptAt) {
        this.attempt = attempt;
        this.nextAttemptAt = nextAttemptAt;
    }

    public Long getId() {
        return id;
    }

    public String getSourceEventKey() {
        return sourceEventKey;
    }

    public UUID getAccountUuid() {
        return accountUuid;
    }

    public String getChannel() {
        return channel;
    }

    public String getNotificationKind() {
        return notificationKind;
    }

    public String getEventDataJson() {
        return eventDataJson;
    }

    public short getAttempt() {
        return attempt;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
