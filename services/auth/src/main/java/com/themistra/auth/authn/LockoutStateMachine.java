package com.themistra.auth.authn;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Pure brute-force lockout decision logic (R16-R19, L4). Stateless and immutable — a single
 * instance is safely shared across concurrent requests; all per-request state flows through
 * {@link #evaluate} parameters and return values only.
 *
 * <p>This class performs no persistence and never calls {@code Account.lock()}/{@code unlock()}
 * itself; {@link LockoutDecision#statusChange()} tells the caller (the future {@code
 * LockoutService}) what to do. It also never imports {@code Account} or any {@code account}
 * entity, keeping the {@code authn}/{@code account} module boundary clean (L12).
 *
 * <p>"Rolling 30-minute window" is implemented as an inactivity-decay rule computed solely from
 * {@code lastFailedAt}: a failure resets the count only if more than {@code decayWindow} has
 * elapsed since the previous failure. This is the adopted interpretation of R17/R19 — the
 * persisted {@code lockout_state} schema has no window-start column, so a true sliding window over
 * full failure history is out of scope.
 *
 * <p>Reaching the attempt threshold does not itself reset {@code failedAttempts} or {@code
 * lastFailedAt} — R18 gates that reset on a successful login, and R19's decay rule keeps applying
 * afterward. Consequently, a failed attempt evaluated shortly after {@code lockedUntil} passes
 * (still within {@code decayWindow} of the failure that caused the lock) re-locks immediately with
 * the duration doubled again. This is intentional escalating behavior (human-approved), not a
 * defect.
 *
 * <p>Whenever a call clears a previously non-null {@code lockedUntil} back to {@code null} — a
 * successful attempt, or a failed attempt that does not re-lock — {@link
 * LockoutDecision#statusChange()} reports {@code UNLOCK} so the caller keeps {@code Account.status}
 * in sync with {@code lockout_state.locked_until} (human-approved, Phase 9).
 */
public final class LockoutStateMachine {

    private final int maxAttempts;
    private final Duration decayWindow;
    private final Duration baseLockDuration;

    public LockoutStateMachine(int maxAttempts, Duration decayWindow, Duration baseLockDuration) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        Objects.requireNonNull(decayWindow, "decayWindow must not be null");
        Objects.requireNonNull(baseLockDuration, "baseLockDuration must not be null");
        if (!decayWindow.isPositive()) {
            throw new IllegalArgumentException("decayWindow must be positive");
        }
        if (!baseLockDuration.isPositive()) {
            throw new IllegalArgumentException("baseLockDuration must be positive");
        }
        this.maxAttempts = maxAttempts;
        this.decayWindow = decayWindow;
        this.baseLockDuration = baseLockDuration;
    }

    /**
     * @return the next-state decision for the given snapshot, moment, and attempt outcome. When
     * the account is currently blocked ({@code now} before {@code snapshot.lockedUntil()}), the
     * outcome is disregarded entirely and every field of the returned decision equals the input
     * snapshot unchanged.
     */
    public LockoutDecision evaluate(LockoutSnapshot snapshot, Instant now, LockoutAttemptOutcome outcome) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");

        if (snapshot.lockedUntil() != null && now.isBefore(snapshot.lockedUntil())) {
            return blockedNoOp(snapshot);
        }
        return switch (outcome) {
            case FAILURE -> applyFailure(snapshot, now);
            case SUCCESS -> applySuccess(snapshot);
        };
    }

    /**
     * @return the zeroed decision used for admin unlock (R20) and password-reset unlock —
     * unconditional, independent of any snapshot.
     */
    public LockoutDecision reset() {
        return new LockoutDecision(0, null, null, 0, false, AccountStatusChange.UNLOCK);
    }

    private LockoutDecision blockedNoOp(LockoutSnapshot snapshot) {
        return new LockoutDecision(
                snapshot.failedAttempts(), snapshot.lastFailedAt(), snapshot.lockedUntil(),
                snapshot.lockCount(), true, AccountStatusChange.NONE);
    }

    private LockoutDecision applyFailure(LockoutSnapshot snapshot, Instant now) {
        int failedAttempts = decayed(snapshot, now) ? 1 : snapshot.failedAttempts() + 1;

        if (failedAttempts < maxAttempts) {
            AccountStatusChange statusChange =
                    snapshot.lockedUntil() != null ? AccountStatusChange.UNLOCK : AccountStatusChange.NONE;
            return new LockoutDecision(
                    failedAttempts, now, null, snapshot.lockCount(),
                    false, statusChange);
        }

        int lockCountBeforeThisLock = snapshot.lockCount();
        Instant lockedUntil = now.plus(effectiveLockDuration(lockCountBeforeThisLock));
        return new LockoutDecision(
                failedAttempts, now, lockedUntil, lockCountBeforeThisLock + 1,
                false, AccountStatusChange.LOCK);
    }

    private LockoutDecision applySuccess(LockoutSnapshot snapshot) {
        AccountStatusChange statusChange =
                snapshot.lockedUntil() != null ? AccountStatusChange.UNLOCK : AccountStatusChange.NONE;
        return new LockoutDecision(0, null, null, 0, false, statusChange);
    }

    private boolean decayed(LockoutSnapshot snapshot, Instant now) {
        return snapshot.lastFailedAt() != null
                && Duration.between(snapshot.lastFailedAt(), now).compareTo(decayWindow) > 0;
    }

    private Duration effectiveLockDuration(int lockCountBeforeThisLock) {
        // No cap on lockCount per L4; extreme values are a documented, accepted theoretical
        // limit (Phase 4/9 disposition), not something this method guards against.
        return baseLockDuration.multipliedBy(1L << lockCountBeforeThisLock);
    }

    /**
     * Input snapshot mirroring the {@code lockout_state} row's nullable/non-null columns exactly.
     * {@code lastFailedAt}/{@code lockedUntil} are null for a never-failed / never-locked account.
     *
     * @throws IllegalArgumentException if {@code failedAttempts} or {@code lockCount} is negative.
     */
    public record LockoutSnapshot(int failedAttempts, Instant lastFailedAt, Instant lockedUntil, int lockCount) {

        public LockoutSnapshot {
            if (failedAttempts < 0) {
                throw new IllegalArgumentException("failedAttempts must not be negative");
            }
            if (lockCount < 0) {
                throw new IllegalArgumentException("lockCount must not be negative");
            }
        }
    }

    /** Which transition {@link #evaluate} applies. */
    public enum LockoutAttemptOutcome {
        FAILURE,
        SUCCESS
    }

    /** Caller-facing signal for whether {@code Account.lock()}/{@code unlock()} should run. */
    public enum AccountStatusChange {
        LOCK,
        UNLOCK,
        NONE
    }

    /** Post-transition state, plus whether the attempt was blocked and any status-change signal. */
    public record LockoutDecision(
            int failedAttempts,
            Instant lastFailedAt,
            Instant lockedUntil,
            int lockCount,
            boolean blocked,
            AccountStatusChange statusChange) {
    }
}
