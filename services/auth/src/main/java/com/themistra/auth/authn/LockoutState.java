package com.themistra.auth.authn;

import com.themistra.auth.authn.LockoutStateMachine.LockoutDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Maps {@code lockout_state} exactly (`V1__auth_baseline_schema.sql:114-120`): {@code account_id}
 * is both the primary key and the foreign key to {@code accounts.id} — no surrogate id, no
 * {@code @GeneratedValue}. Never imports {@code Account} (L12) — {@code account_id} is a plain
 * {@code Long}, resolved from a UUID by {@link LockoutStateRepository}'s native queries.
 */
@Entity
@Table(name = "lockout_state")
public class LockoutState {

    @Id
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "last_failed_at")
    private Instant lastFailedAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "lock_count", nullable = false)
    private int lockCount;

    protected LockoutState() {
        // JPA only
    }

    /** Builds a brand-new row from a resolved internal account id and a machine decision. */
    static LockoutState of(Long accountId, LockoutDecision decision) {
        LockoutState state = new LockoutState();
        state.accountId = accountId;
        state.applyDecision(decision);
        return state;
    }

    /** Overwrites all four mutable fields from a fresh machine decision, in place. */
    void applyDecision(LockoutDecision decision) {
        this.failedAttempts = decision.failedAttempts();
        this.lastFailedAt = decision.lastFailedAt();
        this.lockedUntil = decision.lockedUntil();
        this.lockCount = decision.lockCount();
    }

    public Long getAccountId() {
        return accountId;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getLastFailedAt() {
        return lastFailedAt;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public int getLockCount() {
        return lockCount;
    }
}
