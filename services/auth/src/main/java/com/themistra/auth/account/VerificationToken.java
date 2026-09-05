package com.themistra.auth.account;

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

/**
 * A single-use, hashed, TTL'd token for email verification or password reset (R3–R5, L5). Only
 * {@link #tokenHash} is ever persisted — the raw token is generated and returned exactly once by
 * {@link VerificationTokenService#issue}, never stored.
 *
 * <p>Deliberately has no mutator for {@code usedAt}: redemption happens only through
 * {@link VerificationTokenRepository}'s atomic conditional update, never by loading this entity,
 * setting a field, and saving — that read-modify-write shape is exactly the race a single-use
 * token must not allow.</p>
 */
@Entity
@Table(name = "verification_tokens")
public class VerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 32, updatable = false)
    private Purpose purpose;

    /** CHAR(64), not VARCHAR - JdbcTypeCode.CHAR matches how Postgres reports this column's type
     * (bpchar) so Hibernate's schema validation accepts it. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, unique = true, updatable = false, length = 64,
            columnDefinition = "char(64)")
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VerificationToken() {
        // JPA only
    }

    /** Timestamps are supplied by the caller, sourced from the injected {@code Clock} — never
     * {@code Instant.now()} inline and never a {@code @PrePersist} lifecycle callback. */
    public static VerificationToken create(Long accountId, Purpose purpose, String tokenHash,
                                            Instant createdAt, Instant expiresAt) {
        VerificationToken token = new VerificationToken();
        token.accountId = accountId;
        token.purpose = purpose;
        token.tokenHash = tokenHash;
        token.createdAt = createdAt;
        token.expiresAt = expiresAt;
        return token;
    }

    public enum Purpose {
        EMAIL_VERIFY,
        PASSWORD_RESET
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Purpose getPurpose() {
        return purpose;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
