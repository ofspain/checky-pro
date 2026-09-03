package com.themistra.crypto.quorum;

import com.themistra.crypto.observation.FactType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Converter;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;

/**
 * The per-fact quorum outcome (L1, L2, R1-R3) - maps {@code chain.quorum_decisions} exactly as
 * shipped by T02 (see {@code V1__chain_baseline.sql}).
 *
 * <p><b>Fully immutable post-construction - no setters, no mutation methods.</b> Mirrors {@code
 * Observation}'s (T08) discipline exactly: {@code crypto_app}'s grant on this table is {@code INSERT,
 * SELECT} only, with no {@code UPDATE} and no {@code DELETE} ({@code
 * V2__crypto_app_role_and_grants.sql}). A {@code HELD} decision is therefore never flipped to {@code
 * AGREED} (or vice versa) by any code path in this class - not just a convention, a database-enforced
 * fact (R3, L2).</p>
 *
 * <p>{@code factType} reuses {@link FactType} (T08) directly rather than introducing a second,
 * parallel enum - {@code quorum_decisions.fact_type} and {@code observations.fact_type} share the
 * identical five-value vocabulary named in {@code V1__chain_baseline.sql}'s own column comments.
 * {@link FactType.DbConverter} itself is package-private to {@code observation} and cannot be
 * referenced from here (deviation forced by reality, Phase 6: neither Phase 2 nor Phase 4 anticipated
 * this accessibility conflict) - rather than widen {@code FactType.java}'s visibility, which the
 * frozen brief explicitly commits not to modify, {@link FactTypeDbConverter} below duplicates the
 * identical lowercase mapping locally within {@code quorum/}, preserving both the "no modification to
 * T08 files" commitment and case-consistency between the two tables' {@code fact_type} columns.</p>
 *
 * <p>{@code outcome} uses {@link Enumerated}{@code (STRING)}, not a custom {@code AttributeConverter}
 * like {@code FactType.DbConverter}: {@code quorum_decisions.outcome}'s own {@code CHECK} constraint
 * lists {@code 'AGREED'}, {@code 'HELD'}, {@code 'UNKNOWN_TOKEN'} - the exact uppercase {@link
 * QuorumOutcome#name()} values - so no case-conversion is needed.</p>
 */
@Entity
@Table(name = "quorum_decisions", schema = "chain")
public class QuorumDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String chain;

    @Column(name = "tx_hash", nullable = false, length = 128)
    private String txHash;

    @Convert(converter = FactTypeDbConverter.class)
    @Column(name = "fact_type", nullable = false, length = 32)
    private FactType factType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private QuorumOutcome outcome;

    @Column(name = "agreeing_count", nullable = false)
    private short agreeingCount;

    @Column(name = "provider_count", nullable = false)
    private short providerCount;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    protected QuorumDecision() {
        // JPA only
    }

    public static QuorumDecision create(String chain, String txHash, FactType factType,
                                         QuorumOutcome outcome, int agreeingCount, int providerCount,
                                         Instant decidedAt) {
        QuorumDecision decision = new QuorumDecision();
        decision.chain = chain;
        decision.txHash = txHash;
        decision.factType = factType;
        decision.outcome = outcome;
        decision.agreeingCount = (short) agreeingCount;
        decision.providerCount = (short) providerCount;
        decision.decidedAt = decidedAt;
        return decision;
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

    public FactType factType() {
        return factType;
    }

    public QuorumOutcome outcome() {
        return outcome;
    }

    public short agreeingCount() {
        return agreeingCount;
    }

    public short providerCount() {
        return providerCount;
    }

    public Instant decidedAt() {
        return decidedAt;
    }

    /** Duplicates {@code observation.FactType.DbConverter}'s lowercase mapping locally - that
     * converter is package-private to {@code observation} and unreachable from here (see class
     * Javadoc). Applied explicitly via {@code @Convert} rather than {@code autoApply}, matching the
     * original's own discipline. */
    @Converter
    static class FactTypeDbConverter implements AttributeConverter<FactType, String> {

        @Override
        public String convertToDatabaseColumn(FactType factType) {
            return factType == null ? null : factType.name().toLowerCase(Locale.ROOT);
        }

        @Override
        public FactType convertToEntityAttribute(String dbValue) {
            return dbValue == null ? null : FactType.valueOf(dbValue.toUpperCase(Locale.ROOT));
        }
    }
}
