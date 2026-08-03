package com.themistra.auth.mfa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A TOTP (or, later, WEBAUTHN) enrollment (R22/R23, L12). {@code accountId} is a plain column,
 * never a JPA relation to {@code Account} — this module never imports that entity. The DB's own
 * {@code UNIQUE(account_id, type)} constraint is what actually enforces "one enrollment per
 * account per type" (confirmed or not); {@link MfaEnrollmentRepository#findByAccountIdAndType}
 * only makes that state queryable before a caller attempts an insert that would violate it.
 */
@Entity
@Table(name = "mfa_enrollments")
public class MfaEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16, updatable = false)
    private Type type;

    @Column(name = "secret_encrypted", nullable = false, updatable = false)
    private byte[] secretEncrypted;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MfaEnrollment() {
        // JPA only
    }

    /** Timestamps are supplied by the caller, sourced from the injected {@code Clock} — never
     * {@code Instant.now()} inline and never a {@code @PrePersist} lifecycle callback. */
    public static MfaEnrollment create(Long accountId, Type type, byte[] secretEncrypted, Instant createdAt) {
        MfaEnrollment enrollment = new MfaEnrollment();
        enrollment.accountId = accountId;
        enrollment.type = type;
        enrollment.secretEncrypted = secretEncrypted;
        enrollment.createdAt = createdAt;
        return enrollment;
    }

    /**
     * Confirms the enrollment. Not a single-use redemption race the way {@code RecoveryCode}'s
     * consumption is (a one-shot user action, not attacker-repeatable) — a plain guarded mutator
     * is appropriate here, unlike {@code RecoveryCode.usedAt}'s atomic repository-only update.
     *
     * @throws IllegalStateException if this enrollment is already confirmed
     */
    public void confirm(Instant confirmedAt) {
        if (this.confirmedAt != null) {
            throw new IllegalStateException("MfaEnrollment " + id + " is already confirmed");
        }
        this.confirmedAt = confirmedAt;
    }

    /** Records that this enrollment's TOTP code was just used to authenticate. */
    public void recordUse(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public enum Type {
        TOTP
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Type getType() {
        return type;
    }

    public byte[] getSecretEncrypted() {
        return secretEncrypted;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
