package com.themistra.payment.verification;

import com.themistra.payment.payment.PaymentState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Maps {@code verification_records} in {@code V1__payments_baseline.sql} exactly (immutable,
 * T02) — the append-only audit trail for every payment state movement (L7-style: {@code
 * payment_app} has {@code INSERT}+{@code SELECT} only, no {@code UPDATE}/{@code DELETE}). No
 * {@code Payment} entity exists yet (task 11's own scope), so {@code paymentId} is a plain
 * {@code Long}, not a JPA relationship — mirrors {@code Invoice}'s own established precedent for
 * not-yet-modeled associations.
 *
 * <p><b>{@code fromState} is nullable; {@code toState} is not (T10, Phase 3/4 Finding 1).</b> A
 * {@link VerificationRecordService#recordIgnored} row stores {@code fromState = null},
 * {@code toState = <the unchanged current state>} — an explicit, unambiguous "no real transition
 * occurred" signal, rather than a redundant {@code fromState == toState} self-transition that
 * would carry the same information less clearly.</p>
 *
 * <p><b>{@code detail} (JSONB) uses Hibernate 6's native {@code @JdbcTypeCode(SqlTypes.JSON)}</b>
 * — no additional dependency; the first JSONB-mapped column in this codebase.</p>
 *
 * <p><b>{@code occurredAt} (Phase 3 finding 7's own identical precedent, {@code Invoice}/{@code
 * OutboxEvent}).</b> {@link VerificationRecordService#record}/{@link
 * VerificationRecordService#recordIgnored} always set it explicitly from an injected
 * {@link java.time.Clock}; {@link #onCreate()} below is a last-resort {@code Instant.now()}
 * fallback only, for the same reason those two entities have one: a JPA entity cannot have an
 * injected {@code Clock}.</p>
 */
@Entity
@Table(name = "verification_records")
public class VerificationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", length = 16)
    private PaymentState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false, length = 16)
    private PaymentState toState;

    @Column(name = "trigger_event_key", length = 200)
    private String triggerEventKey;

    @Column(name = "confirmations")
    private Integer confirmations;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail")
    private Map<String, Object> detail;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected VerificationRecord() {
        // JPA only
    }

    public static VerificationRecord create(Long paymentId, PaymentState fromState, PaymentState toState,
                                             String triggerEventKey, Integer confirmations,
                                             Map<String, Object> detail, Instant occurredAt) {
        VerificationRecord record = new VerificationRecord();
        record.paymentId = paymentId;
        record.fromState = fromState;
        record.toState = toState;
        record.triggerEventKey = triggerEventKey;
        record.confirmations = confirmations;
        record.detail = detail;
        record.occurredAt = occurredAt;
        return record;
    }

    @PrePersist
    void onCreate() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public PaymentState getFromState() {
        return fromState;
    }

    public PaymentState getToState() {
        return toState;
    }

    public String getTriggerEventKey() {
        return triggerEventKey;
    }

    public Integer getConfirmations() {
        return confirmations;
    }

    public Map<String, Object> getDetail() {
        return detail;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
