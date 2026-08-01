package com.themistra.auth.authn;

import com.themistra.auth.account.AccountService;
import com.themistra.auth.authn.LockoutStateMachine.AccountStatusChange;
import com.themistra.auth.authn.LockoutStateMachine.LockoutAttemptOutcome;
import com.themistra.auth.authn.LockoutStateMachine.LockoutDecision;
import com.themistra.auth.authn.LockoutStateMachine.LockoutSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Makes {@link LockoutStateMachine} (T11, pure decision logic) real: loads/persists
 * {@code lockout_state} under a pessimistic row lock and applies the resulting decision by
 * calling {@link AccountService#lock(UUID)} / {@link AccountService#unlock(UUID)} — the only
 * sanctioned path from this module to the {@code Account} entity (L12; this class never imports
 * {@code Account}).
 *
 * <p>Callers (T13) are responsible for only invoking this service for an account whose status is
 * {@code ACTIVE}, or {@code LOCKED} with {@code locked_until} at or before the evaluation
 * instant — this class cannot check {@code Account.status} itself. Not {@code
 * PENDING_VERIFICATION}, {@code SUSPENDED}, or {@code DELETED} (T11/T12 boundary clarification,
 * Phase 4 Finding 5 — supersedes T11's own frozen brief text, which was narrower than the
 * behavior T11 itself already tests).</p>
 */
@Service
public class LockoutService {

    private final LockoutStateRepository repository;
    private final AccountService accountService;
    private final LockoutStateMachine machine;

    public LockoutService(LockoutStateRepository repository, LockoutProperties properties,
                          AccountService accountService) {
        this.repository = repository;
        this.accountService = accountService;
        this.machine = new LockoutStateMachine(
                properties.maxAttempts(),
                Duration.ofMinutes(properties.windowMinutes()),
                Duration.ofMinutes(properties.baseLockMinutes()));
    }

    /**
     * Records a failed login attempt (R16/R17/R19). A missing row is treated as a fresh,
     * never-failed account and created on this call. A blocked attempt (still within an active
     * lock) writes nothing and calls {@link AccountService} for nothing — the outcome is
     * disregarded entirely, matching {@link LockoutStateMachine#evaluate}'s own contract.
     */
    @Transactional
    public LockoutDecision recordFailedAttempt(UUID accountUuid, Instant now) {
        Objects.requireNonNull(accountUuid, "accountUuid must not be null");
        Objects.requireNonNull(now, "now must not be null");

        Optional<LockoutState> existing = repository.findByAccountUuidForUpdate(accountUuid);
        LockoutDecision decision = machine.evaluate(toSnapshot(existing), now, LockoutAttemptOutcome.FAILURE);

        if (decision.blocked()) {
            return decision;
        }
        persistNewOrUpdated(existing, accountUuid, decision);
        applyStatusChange(decision.statusChange(), accountUuid);
        return decision;
    }

    /**
     * Records a successful login attempt (R18). A missing row is a no-op — there are no counters
     * to reset, so nothing is inserted. A blocked attempt behaves exactly as in
     * {@link #recordFailedAttempt}.
     *
     * <p>Invariant this relies on: a {@code LOCKED} account always has a {@code lockout_state}
     * row (only this service ever locks one, and only after writing the row first). If that
     * invariant is ever violated by external data corruption, a successful login would find no
     * row, no-op, and leave the account {@code LOCKED} with no way to self-heal through this
     * method — an operator-facing data-integrity scenario, not a state this service repairs.</p>
     */
    @Transactional
    public LockoutDecision recordSuccessfulAttempt(UUID accountUuid, Instant now) {
        Objects.requireNonNull(accountUuid, "accountUuid must not be null");
        Objects.requireNonNull(now, "now must not be null");

        Optional<LockoutState> existing = repository.findByAccountUuidForUpdate(accountUuid);
        LockoutDecision decision = machine.evaluate(toSnapshot(existing), now, LockoutAttemptOutcome.SUCCESS);

        if (decision.blocked() || existing.isEmpty()) {
            return decision;
        }
        persistNewOrUpdated(existing, accountUuid, decision);
        applyStatusChange(decision.statusChange(), accountUuid);
        return decision;
    }

    /**
     * Unconditionally clears any lockout state for the account (R20 admin unlock, and any future
     * password-reset-driven unlock) — for T14's future use; not called by anything in this task.
     * A missing row has nothing to persist, but {@link AccountService#unlock(UUID)} is still
     * called (safe: it is itself a guarded no-op unless the account is currently {@code LOCKED}).
     */
    @Transactional
    public LockoutDecision resetLockout(UUID accountUuid) {
        Objects.requireNonNull(accountUuid, "accountUuid must not be null");

        Optional<LockoutState> existing = repository.findByAccountUuidForUpdate(accountUuid);
        LockoutDecision decision = machine.reset();

        existing.ifPresent(state -> {
            state.applyDecision(decision);
            repository.save(state);
        });
        applyStatusChange(decision.statusChange(), accountUuid);
        return decision;
    }

    /**
     * Point-in-time check: is this account currently within an active lock? Read-only, no row
     * lock — for T13's pre-authentication gate (deciding whether to let a login attempt reach
     * password verification at all), not part of a read-evaluate-write cycle. Returns {@code
     * false} for a missing row (no active interval recorded means no lock, consistent with R18);
     * a {@code LOCKED} {@code Account.status} with a missing row is the same documented,
     * operator-facing data-integrity scenario {@link #recordSuccessfulAttempt} already carries —
     * this method does not repair it, it just isn't blocked by it either.
     */
    @Transactional(readOnly = true)
    public boolean isCurrentlyLocked(UUID accountUuid, Instant now) {
        Objects.requireNonNull(accountUuid, "accountUuid must not be null");
        Objects.requireNonNull(now, "now must not be null");

        return repository.findByAccountUuid(accountUuid)
                .map(LockoutState::getLockedUntil)
                .map(now::isBefore)
                .orElse(false);
    }

    private LockoutSnapshot toSnapshot(Optional<LockoutState> existing) {
        return existing
                .map(state -> new LockoutSnapshot(
                        state.getFailedAttempts(), state.getLastFailedAt(),
                        state.getLockedUntil(), state.getLockCount()))
                .orElseGet(() -> new LockoutSnapshot(0, null, null, 0));
    }

    private void applyStatusChange(AccountStatusChange statusChange, UUID accountUuid) {
        switch (statusChange) {
            case LOCK -> accountService.lock(accountUuid);
            case UNLOCK -> accountService.unlock(accountUuid);
            case NONE -> {
                // no Account-side transition required
            }
        }
    }

    /**
     * A missing {@code accountId} resolution (the UUID doesn't correspond to any real account) is
     * treated as a silent no-op, exactly like {@link #recordSuccessfulAttempt}'s missing-row case
     * — this service trusts its caller's precondition (T13 only invokes it for accounts it has
     * already resolved) rather than surfacing its own existence error. A structurally impossible
     * case in practice: the only decision reachable when {@code existing} is empty is a low
     * failed-attempt count with {@code statusChange = NONE} (a first-ever failure can never
     * itself reach the lock threshold), so even skipping persistence here has no observable
     * effect beyond the returned decision.
     */
    private void persistNewOrUpdated(Optional<LockoutState> existing, UUID accountUuid, LockoutDecision decision) {
        if (existing.isPresent()) {
            LockoutState state = existing.get();
            state.applyDecision(decision);
            repository.save(state);
            return;
        }
        repository.findAccountIdByUuid(accountUuid)
                .ifPresent(accountId -> repository.save(LockoutState.of(accountId, decision)));
    }
}
