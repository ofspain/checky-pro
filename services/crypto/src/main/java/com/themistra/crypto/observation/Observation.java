package com.themistra.crypto.observation;

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

/**
 * One verbatim, append-only record of what a single provider said about a single fact — the
 * defensible core of the platform (design.md §5). Maps {@code chain.observations} exactly as shipped
 * by T02 (see {@code V1__chain_baseline.sql}); this class does not, and must never, alter that shape.
 *
 * <p><b>Fully immutable post-construction — no setters, no mutation methods.</b> Unlike {@code
 * OutboxEvent} (which has {@code markPublished}), nothing about an {@code Observation} ever changes
 * after insert. This isn't just a style choice: the {@code crypto_app} runtime role's grant on this
 * table is {@code INSERT, SELECT} only, with no {@code UPDATE} and no {@code DELETE}
 * ({@code V2__crypto_app_role_and_grants.sql}) — any code path that caused Hibernate to issue an
 * {@code UPDATE} would fail at the database layer, not just violate a convention. {@code s3SnapshotKey}
 * is therefore supplied at construction time, already resolved (possibly {@code null} if the S3 write
 * failed — see {@link ObservationSnapshotStore}), never attached afterward.</p>
 *
 * <p>{@code rawResponse} is a {@code String} containing JSON, mapped the same way {@code
 * OutboxEvent.payload} is (Phase 3 Kimi Issue 1). The caller is responsible for serializing the
 * actual provider response into that JSON string before ever calling {@link ObservationLog#record};
 * this class — and the rest of this task's own scope — never touches a provider-specific Java type
 * (a {@code web3j}/{@code trident} response object, or whatever a future sidecar sends).</p>
 */
@Entity
@Table(name = "observations", schema = "chain")
public class Observation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String chain;

    @Column(name = "tx_hash", nullable = false, length = 128)
    private String txHash;

    @Column(nullable = false, length = 64)
    private String provider;

    @Convert(converter = FactType.DbConverter.class)
    @Column(name = "fact_type", nullable = false, length = 32)
    private FactType factType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", nullable = false)
    private String rawResponse;

    @Column(name = "s3_snapshot_key", length = 256)
    private String s3SnapshotKey;

    @Column(name = "observed_at", nullable = false, updatable = false)
    private Instant observedAt;

    protected Observation() {
        // JPA only
    }

    public static Observation create(String chain, String txHash, String provider, FactType factType,
                                      String rawResponseJson, String s3SnapshotKey, Instant observedAt) {
        Observation observation = new Observation();
        observation.chain = chain;
        observation.txHash = txHash;
        observation.provider = provider;
        observation.factType = factType;
        observation.rawResponse = rawResponseJson;
        observation.s3SnapshotKey = s3SnapshotKey;
        observation.observedAt = observedAt;
        return observation;
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

    public String provider() {
        return provider;
    }

    public FactType factType() {
        return factType;
    }

    public String rawResponse() {
        return rawResponse;
    }

    public String s3SnapshotKey() {
        return s3SnapshotKey;
    }

    public Instant observedAt() {
        return observedAt;
    }
}
