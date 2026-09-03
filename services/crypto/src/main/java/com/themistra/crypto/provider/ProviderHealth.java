package com.themistra.crypto.provider;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Per-provider health state (R5) - maps {@code chain.provider_health} exactly as shipped by T02
 * (see {@code V1__chain_baseline.sql}). One row per {@code (chain, provider)} (the DB's own
 * {@code UNIQUE (chain, provider)} constraint), **update-in-place** - unlike every other entity built
 * so far in this service ({@code OutboxEvent}, {@code Observation}, {@code QuorumDecision}, all
 * append-only), this is the first whose own table is genuinely mutable.
 *
 * <p>No raw setters. Three narrow, named mutators instead: {@link #markHealthy}, {@link
 * #markUnhealthy}, {@link #recordDisagreement}. {@code healthy} is the sole authoritative
 * current-state flag; {@code lastOkAt}/{@code lastDisagreementAt} are independent historical "last
 * occurrence" markers (matching their own column names) that are never cleared by a mutator other
 * than the one that directly updates them - in particular, {@link #markHealthy} does NOT clear {@code
 * lastDisagreementAt} on recovery (Phase 3 Kimi Issue 10): a dashboard reading {@code
 * lastDisagreementAt} sees the most recent disagreement ever observed, not a "currently disagreeing"
 * signal - that signal is {@code healthy} alone.</p>
 */
@Entity
@Table(name = "provider_health", schema = "chain")
public class ProviderHealth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String chain;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(nullable = false)
    private boolean healthy;

    @Column(name = "last_ok_at")
    private Instant lastOkAt;

    @Column(name = "last_disagreement_at")
    private Instant lastDisagreementAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProviderHealth() {
        // JPA only
    }

    /** New provider health row, healthy by default (matching the column's own {@code DEFAULT TRUE}). */
    public static ProviderHealth create(String chain, String provider, Instant now) {
        ProviderHealth health = new ProviderHealth();
        health.chain = chain;
        health.provider = provider;
        health.healthy = true;
        health.updatedAt = now;
        return health;
    }

    public void markHealthy(Instant now) {
        healthy = true;
        lastOkAt = now;
        updatedAt = now;
    }

    public void markUnhealthy(Instant now) {
        healthy = false;
        updatedAt = now;
    }

    public void recordDisagreement(Instant now) {
        lastDisagreementAt = now;
        updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public String chain() {
        return chain;
    }

    public String provider() {
        return provider;
    }

    public boolean healthy() {
        return healthy;
    }

    public Instant lastOkAt() {
        return lastOkAt;
    }

    public Instant lastDisagreementAt() {
        return lastDisagreementAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
