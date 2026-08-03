package com.themistra.auth.mfa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A single-use, hashed TOTP recovery code (R23, L6). Only {@link #codeHash} (a SHA-256 hex
 * digest) is ever persisted — the raw code is generated and returned exactly once by task 18's
 * service, never stored.
 *
 * <p>Deliberately has no mutator for {@code usedAt}: redemption happens only through
 * {@link RecoveryCodeRepository#markUsed}'s atomic conditional update, never by loading this
 * entity, setting a field, and saving — that read-modify-write shape is exactly the race a
 * single-use code must not allow. Mirrors {@code VerificationToken}'s identical reasoning for its
 * own {@code usedAt} column.</p>
 */
@Entity
@Table(name = "recovery_codes")
public class RecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(name = "code_hash", nullable = false, length = 64, updatable = false)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RecoveryCode() {
        // JPA only
    }

    /** Timestamps are supplied by the caller, sourced from the injected {@code Clock} — never
     * {@code Instant.now()} inline and never a {@code @PrePersist} lifecycle callback. */
    public static RecoveryCode create(Long accountId, String codeHash, Instant createdAt) {
        RecoveryCode code = new RecoveryCode();
        code.accountId = accountId;
        code.codeHash = codeHash;
        code.createdAt = createdAt;
        return code;
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
