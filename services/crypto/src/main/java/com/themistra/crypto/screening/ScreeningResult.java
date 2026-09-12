package com.themistra.crypto.screening;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

/**
 * One counterparty screening attempt (L12, R21) - maps {@code chain.screening_results} exactly as
 * shipped by T02 (see {@code V1__chain_baseline.sql}). Append-only, matching every other audit-trail
 * table in this service ({@code observations}, {@code attestations}, {@code token_allowlist}): no
 * setters, {@code protected} no-arg constructor, public static {@link #create}. The
 * {@code crypto_app} runtime role's grant on this table is {@code INSERT, SELECT} only
 * ({@code V9__crypto_app_screening_results_grant.sql}) - a re-screen is always a new row, never a
 * revision of a prior one.
 *
 * <p>{@code rawResponse} is a nullable {@code String} containing JSON, mapped the same way {@code
 * Observation.rawResponse} is ({@code @JdbcTypeCode(SqlTypes.JSON)} over a pre-serialized
 * {@code String}) - unlike {@code Observation}'s column, this one is nullable (the DDL's own
 * {@code raw_response JSONB} carries no {@code NOT NULL}), since a stub implementation with no real
 * vendor response has nothing to record verbatim.</p>
 */
@Entity
@Table(name = "screening_results", schema = "chain")
public class ScreeningResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String chain;

    @Column(nullable = false, length = 128)
    private String address;

    @Column(name = "tx_hash", length = 128)
    private String txHash;

    @Convert(converter = ScreeningOutcome.DbConverter.class)
    @Column(nullable = false, length = 16)
    private ScreeningOutcome outcome;

    @Column(nullable = false, length = 64)
    private String provider;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response")
    private String rawResponse;

    @Column(name = "screened_at", nullable = false, updatable = false)
    private Instant screenedAt;

    protected ScreeningResult() {
        // JPA only
    }

    public static ScreeningResult create(String chain, String address, String txHash,
                                          ScreeningOutcome outcome, String provider, String rawResponse,
                                          Instant screenedAt) {
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(screenedAt, "screenedAt");
        ScreeningResult result = new ScreeningResult();
        result.chain = chain;
        result.address = address;
        result.txHash = txHash;
        result.outcome = outcome;
        result.provider = provider;
        result.rawResponse = rawResponse;
        result.screenedAt = screenedAt;
        return result;
    }

    public Long id() {
        return id;
    }

    public String chain() {
        return chain;
    }

    public String address() {
        return address;
    }

    public String txHash() {
        return txHash;
    }

    public ScreeningOutcome outcome() {
        return outcome;
    }

    public String provider() {
        return provider;
    }

    public String rawResponse() {
        return rawResponse;
    }

    public Instant screenedAt() {
        return screenedAt;
    }
}
