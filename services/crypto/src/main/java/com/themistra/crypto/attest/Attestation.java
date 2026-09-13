package com.themistra.crypto.attest;

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
 * One attest request's outcome (R20) - maps {@code chain.attestations} exactly as shipped by T02 (see
 * {@code V1__chain_baseline.sql}). Append-only, matching every other {@code INSERT, SELECT}-only table
 * in this service: no setters, {@code protected} no-arg constructor, public static {@link #create}.
 *
 * <p>{@code kmsKeyId}/{@code signedAt} are nullable, matching the DDL's own nullability - only a
 * {@link AttestOutcome#SIGNED} row ever has both populated; {@link AttestOutcome#BLOCKED}/{@link
 * AttestOutcome#REFUSED} rows leave both {@code null}.</p>
 *
 * <p>{@code receiptDigest}'s {@code @JdbcTypeCode(SqlTypes.CHAR)} is load-bearing, not decorative - the
 * DDL declares {@code receipt_digest CHAR(64)} (fixed-length, {@code bpchar}), and Hibernate's default
 * mapping for a {@code String} column is {@code VARCHAR}; without this override, Hibernate's own schema
 * *validation* (not creation - Flyway already owns the real schema) fails at context startup with a
 * type mismatch. A first attempt using {@code columnDefinition = "CHAR(64)"} instead did not fix this -
 * that attribute only affects schema *generation*, not the SQL type code Hibernate's validator itself
 * expects - discovered by actually running the integration test twice, not assumed either time.</p>
 */
@Entity
@Table(name = "attestations", schema = "chain")
public class Attestation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String chain;

    @Column(name = "tx_hash", nullable = false, length = 128)
    private String txHash;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "receipt_digest", nullable = false, length = 64)
    private String receiptDigest;

    @Convert(converter = AttestOutcome.DbConverter.class)
    @Column(nullable = false, length = 16)
    private AttestOutcome outcome;

    @Column(name = "kms_key_id", length = 256)
    private String kmsKeyId;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Attestation() {
        // JPA only
    }

    public static Attestation create(String chain, String txHash, String receiptDigest,
                                      AttestOutcome outcome, String kmsKeyId, Instant signedAt,
                                      Instant createdAt) {
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(txHash, "txHash");
        Objects.requireNonNull(receiptDigest, "receiptDigest");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(createdAt, "createdAt");
        Attestation attestation = new Attestation();
        attestation.chain = chain;
        attestation.txHash = txHash;
        attestation.receiptDigest = receiptDigest;
        attestation.outcome = outcome;
        attestation.kmsKeyId = kmsKeyId;
        attestation.signedAt = signedAt;
        attestation.createdAt = createdAt;
        return attestation;
    }

    public Long id() {
        return id;
    }

    public String chain() {
        return chain;
    }

    public String txHash() {
        return txHash;
    }

    public String receiptDigest() {
        return receiptDigest;
    }

    public AttestOutcome outcome() {
        return outcome;
    }

    public String kmsKeyId() {
        return kmsKeyId;
    }

    public Instant signedAt() {
        return signedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
