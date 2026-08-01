package com.themistra.auth.authn;

import com.themistra.auth.authn.LockoutStateMachine.AccountStatusChange;
import com.themistra.auth.authn.LockoutStateMachine.LockoutAttemptOutcome;
import com.themistra.auth.authn.LockoutStateMachine.LockoutDecision;
import com.themistra.auth.authn.LockoutStateMachine.LockoutSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link LockoutStateMachine} — R16-R19, L4. Plain JUnit, no Mockito (no
 * collaborators), no Spring context, per {@code agents.md}. All timestamps are fixed offsets from
 * {@link #T0} — no {@code Instant.now()} anywhere in this file.
 */
class LockoutStateMachineTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private final LockoutStateMachine machine =
            new LockoutStateMachine(5, Duration.ofMinutes(30), Duration.ofMinutes(15));

    @Test
    void shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes() {
        LockoutSnapshot snapshot = new LockoutSnapshot(0, null, null, 0);

        for (int i = 1; i <= 4; i++) {
            Instant now = T0.plus(Duration.ofMinutes(i - 1));
            LockoutDecision decision = machine.evaluate(snapshot, now, LockoutAttemptOutcome.FAILURE);
            assertThat(decision.failedAttempts()).isEqualTo(i);
            assertThat(decision.blocked()).isFalse();
            assertThat(decision.lockedUntil()).isNull();
            assertThat(decision.statusChange()).isEqualTo(AccountStatusChange.NONE);
            snapshot = new LockoutSnapshot(decision.failedAttempts(), decision.lastFailedAt(),
                    decision.lockedUntil(), decision.lockCount());
        }

        Instant fifthFailureAt = T0.plus(Duration.ofMinutes(4));
        LockoutDecision fifth = machine.evaluate(snapshot, fifthFailureAt, LockoutAttemptOutcome.FAILURE);

        assertThat(fifth.failedAttempts()).isEqualTo(5);
        assertThat(fifth.lockedUntil()).isEqualTo(fifthFailureAt.plus(Duration.ofMinutes(15)));
        assertThat(fifth.lockCount()).isEqualTo(1);
        assertThat(fifth.statusChange()).isEqualTo(AccountStatusChange.LOCK);
        assertThat(fifth.blocked()).isFalse();
    }

    @Test
    void shouldResetLockoutCounterOnSuccessfulLogin() {
        LockoutSnapshot snapshot = new LockoutSnapshot(3, T0, null, 0);

        LockoutDecision decision = machine.evaluate(snapshot, T0.plusSeconds(10), LockoutAttemptOutcome.SUCCESS);

        assertThat(decision.failedAttempts()).isZero();
        assertThat(decision.lastFailedAt()).isNull();
        assertThat(decision.lockedUntil()).isNull();
        assertThat(decision.lockCount()).isZero();
        assertThat(decision.blocked()).isFalse();
        assertThat(decision.statusChange()).isEqualTo(AccountStatusChange.NONE);
    }

    @Test
    void fourthFailureWithinWindowDoesNotLock() {
        LockoutSnapshot snapshot = new LockoutSnapshot(3, T0, null, 0);

        LockoutDecision decision = machine.evaluate(snapshot, T0.plus(Duration.ofMinutes(1)), LockoutAttemptOutcome.FAILURE);

        assertThat(decision.failedAttempts()).isEqualTo(4);
        assertThat(decision.lockedUntil()).isNull();
        assertThat(decision.lockCount()).isZero();
        assertThat(decision.statusChange()).isEqualTo(AccountStatusChange.NONE);
    }

    @Test
    void fifthFailureExactlyAtThirtyMinuteBoundaryStillLocksWithoutPrematureDecay() {
        LockoutSnapshot snapshot = new LockoutSnapshot(4, T0, null, 0);
        Instant now = T0.plus(Duration.ofMinutes(30));

        LockoutDecision decision = machine.evaluate(snapshot, now, LockoutAttemptOutcome.FAILURE);

        assertThat(decision.failedAttempts()).isEqualTo(5);
        assertThat(decision.lockedUntil()).isEqualTo(now.plus(Duration.ofMinutes(15)));
        assertThat(decision.lockCount()).isEqualTo(1);
        assertThat(decision.statusChange()).isEqualTo(AccountStatusChange.LOCK);
    }

    @Test
    void failureJustPastThirtyMinuteBoundaryDecaysInsteadOfLocking() {
        LockoutSnapshot snapshot = new LockoutSnapshot(4, T0, null, 0);
        Instant now = T0.plus(Duration.ofMinutes(30)).plusSeconds(1);

        LockoutDecision decision = machine.evaluate(snapshot, now, LockoutAttemptOutcome.FAILURE);

        assertThat(decision.failedAttempts()).isEqualTo(1);
        assertThat(decision.lastFailedAt()).isEqualTo(now);
        assertThat(decision.lockedUntil()).isNull();
        assertThat(decision.statusChange()).isEqualTo(AccountStatusChange.NONE);
    }

    @Test
    void secondLockDoublesDurationToThirtyMinutesAndIncrementsLockCount() {
        LockoutSnapshot snapshot = new LockoutSnapshot(4, T0, null, 1);
        Instant now = T0.plus(Duration.ofMinutes(1));

        LockoutDecision decision = machine.evaluate(snapshot, now, LockoutAttemptOutcome.FAILURE);

        assertThat(decision.lockedUntil()).isEqualTo(now.plus(Duration.ofMinutes(30)));
        assertThat(decision.lockCount()).isEqualTo(2);
        assertThat(decision.statusChange()).isEqualTo(AccountStatusChange.LOCK);
    }

    @Test
    void thirdLockDoublesDurationToSixtyMinutesAndIncrementsLockCount() {
        LockoutSnapshot snapshot = new LockoutSnapshot(4, T0, null, 2);
        Instant now = T0.plus(Duration.ofMinutes(1));

        LockoutDecision decision = machine.evaluate(snapshot, now, LockoutAttemptOutcome.FAILURE);

        assertThat(decision.lockedUntil()).isEqualTo(now.plus(Duration.ofMinutes(60)));
        assertThat(decision.lockCount()).isEqualTo(3);
        assertThat(decision.statusChange()).isEqualTo(AccountStatusChange.LOCK);
    }

    @Test
    void successAtOrAfterLockedUntilIsPermittedAndResetsCountersWithUnlockSignal() {
        Instant lockedUntil = T0.plus(Duration.ofMinutes(15));
        LockoutSnapshot snapshot = new LockoutSnapshot(5, T0, lockedUntil, 1);

        LockoutDecision decision = machine.evaluate(snapshot, lockedUntil, LockoutAttemptOutcome.SUCCESS);

        assertThat(decision.blocked()).isFalse();
        assertThat(decision.failedAttempts()).isZero();
        assertThat(decision.lastFailedAt()).isNull();
        assertThat(decision.lockedUntil()).isNull();
        assertThat(decision.lockCount()).isZero();
        assertThat(decision.statusChange()).isEqualTo(AccountStatusChange.UNLOCK);
    }

    @Test
    void attemptOneInstantBeforeLockedUntilIsBlockedRegardlessOfOutcome() {
        Instant lockedUntil = T0.plus(Duration.ofMinutes(15));
        LockoutSnapshot snapshot = new LockoutSnapshot(5, T0, lockedUntil, 1);
        Instant now = lockedUntil.minusMillis(1);

        LockoutDecision failureDecision = machine.evaluate(snapshot, now, LockoutAttemptOutcome.FAILURE);
        assertBlockedNoOp(snapshot, failureDecision);

        LockoutDecision successDecision = machine.evaluate(snapshot, now, LockoutAttemptOutcome.SUCCESS);
        assertBlockedNoOp(snapshot, successDecision);
    }

    private static void assertBlockedNoOp(LockoutSnapshot snapshot, LockoutDecision decision) {
        assertThat(decision.blocked()).isTrue();
        assertThat(decision.failedAttempts()).isEqualTo(snapshot.failedAttempts());
        assertThat(decision.lastFailedAt()).isEqualTo(snapshot.lastFailedAt());
        assertThat(decision.lockedUntil()).isEqualTo(snapshot.lockedUntil());
        assertThat(decision.lockCount()).isEqualTo(snapshot.lockCount());
        assertThat(decision.statusChange()).isEqualTo(AccountStatusChange.NONE);
    }

    @Test
    void failedAttemptImmediatelyAfterLockExpiryReLocksWithDoubledDuration() {
        // Human-approved escalating behavior (frozen brief Finding 2 / AC7): failedAttempts is
        // never reset by locking itself, so a failure landing within decayWindow of the
        // lock-triggering failure re-locks immediately with lockCount doubled again.
        Instant lockedUntil = T0.plus(Duration.ofMinutes(15));
        LockoutSnapshot snapshot = new LockoutSnapshot(5, T0, lockedUntil, 1);

        LockoutDecision decision = machine.evaluate(snapshot, lockedUntil, LockoutAttemptOutcome.FAILURE);

        assertThat(decision.blocked()).isFalse();
        assertThat(decision.failedAttempts()).isEqualTo(6);
        assertThat(decision.lockedUntil()).isEqualTo(lockedUntil.plus(Duration.ofMinutes(30)));
        assertThat(decision.lockCount()).isEqualTo(2);
        assertThat(decision.statusChange()).isEqualTo(AccountStatusChange.LOCK);
    }

    @Test
    void failedAttemptWellAfterLockExpiryDecaysAndSignalsUnlockWithoutRelocking() {
        Instant lockedUntil = T0.plus(Duration.ofMinutes(15));
        LockoutSnapshot snapshot = new LockoutSnapshot(5, T0, lockedUntil, 1);
        Instant now = T0.plus(Duration.ofMinutes(46));

        LockoutDecision decision = machine.evaluate(snapshot, now, LockoutAttemptOutcome.FAILURE);

        assertThat(decision.blocked()).isFalse();
        assertThat(decision.failedAttempts()).isEqualTo(1);
        assertThat(decision.lastFailedAt()).isEqualTo(now);
        assertThat(decision.lockedUntil()).isNull();
        assertThat(decision.lockCount()).isEqualTo(1);
        assertThat(decision.statusChange()).isEqualTo(AccountStatusChange.UNLOCK);
    }

    @Test
    void resetAlwaysReturnsZeroedDecisionWithUnlockSignal() {
        LockoutDecision decision = machine.reset();

        assertThat(decision.failedAttempts()).isZero();
        assertThat(decision.lastFailedAt()).isNull();
        assertThat(decision.lockedUntil()).isNull();
        assertThat(decision.lockCount()).isZero();
        assertThat(decision.blocked()).isFalse();
        assertThat(decision.statusChange()).isEqualTo(AccountStatusChange.UNLOCK);
    }

    @Test
    void negativeFailedAttemptsInSnapshotThrows() {
        assertThatThrownBy(() -> new LockoutSnapshot(-1, null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeLockCountInSnapshotThrows() {
        assertThatThrownBy(() -> new LockoutSnapshot(0, null, null, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void evaluateRejectsNullSnapshotNowOrOutcome() {
        LockoutSnapshot snapshot = new LockoutSnapshot(0, null, null, 0);

        assertThatThrownBy(() -> machine.evaluate(null, T0, LockoutAttemptOutcome.FAILURE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> machine.evaluate(snapshot, null, LockoutAttemptOutcome.FAILURE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> machine.evaluate(snapshot, T0, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorRejectsNonPositiveMaxAttempts() {
        assertThatThrownBy(() -> new LockoutStateMachine(0, Duration.ofMinutes(30), Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LockoutStateMachine(-1, Duration.ofMinutes(30), Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorRejectsNonPositiveDecayWindow() {
        assertThatThrownBy(() -> new LockoutStateMachine(5, Duration.ZERO, Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LockoutStateMachine(5, Duration.ofMinutes(-1), Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorRejectsNonPositiveBaseLockDuration() {
        assertThatThrownBy(() -> new LockoutStateMachine(5, Duration.ofMinutes(30), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LockoutStateMachine(5, Duration.ofMinutes(30), Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
