package com.themistra.auth.authn;

import com.themistra.auth.account.AccountService;
import com.themistra.auth.authn.LockoutStateMachine.AccountStatusChange;
import com.themistra.auth.authn.LockoutStateMachine.LockoutDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LockoutService} — R16-R19, L4, L12. Plain JUnit + Mockito, no Spring
 * context, mocking {@link LockoutStateRepository} and {@link AccountService}. {@link
 * LockoutStateMachine} itself is real and unmocked — its correctness is T11's own 22-test suite;
 * these tests verify the persistence/wiring layer around it, not the decision rules again.
 */
@ExtendWith(MockitoExtension.class)
class LockoutServiceTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final UUID ACCOUNT_UUID = UUID.randomUUID();
    private static final Long ACCOUNT_ID = 42L;

    @Mock
    private LockoutStateRepository repository;

    @Mock
    private AccountService accountService;

    private LockoutService service;

    @BeforeEach
    void setUp() {
        service = new LockoutService(repository, new LockoutProperties(5, 30, 15), accountService);
    }

    @Test
    void shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes() {
        LockoutState fourFailures = existingRow(4, T0, null, 0);
        when(repository.findByAccountUuidForUpdate(ACCOUNT_UUID)).thenReturn(Optional.of(fourFailures));

        LockoutDecision decision = service.recordFailedAttempt(ACCOUNT_UUID, T0.plusSeconds(60));

        assertThat(decision.failedAttempts()).isEqualTo(5);
        assertThat(decision.statusChange()).isEqualTo(AccountStatusChange.LOCK);
        assertThat(fourFailures.getFailedAttempts()).isEqualTo(5);
        assertThat(fourFailures.getLastFailedAt()).isEqualTo(T0.plusSeconds(60));
        assertThat(fourFailures.getLockedUntil()).isEqualTo(T0.plusSeconds(60).plus(Duration.ofMinutes(15)));
        verify(repository).save(fourFailures);
        verify(accountService).lock(ACCOUNT_UUID);
        verify(accountService, never()).unlock(any());
    }

    @Test
    void nonLockingFailureStillPersistsUpdatedCounters() {
        LockoutState threeFailures = existingRow(3, T0, null, 0);
        when(repository.findByAccountUuidForUpdate(ACCOUNT_UUID)).thenReturn(Optional.of(threeFailures));

        LockoutDecision decision = service.recordFailedAttempt(ACCOUNT_UUID, T0.plusSeconds(60));

        assertThat(decision.statusChange()).isEqualTo(AccountStatusChange.NONE);
        assertThat(decision.failedAttempts()).isEqualTo(4);
        assertThat(threeFailures.getFailedAttempts()).isEqualTo(4);
        verify(repository).save(threeFailures);
        verifyNoInteractions(accountService);
    }

    @Test
    void shouldResetLockoutCounterOnSuccessfulLogin() {
        Instant lockedUntil = T0.plus(Duration.ofMinutes(15));
        LockoutState locked = existingRow(5, T0, lockedUntil, 1);
        when(repository.findByAccountUuidForUpdate(ACCOUNT_UUID)).thenReturn(Optional.of(locked));

        LockoutDecision decision = service.recordSuccessfulAttempt(ACCOUNT_UUID, lockedUntil);

        assertThat(decision.failedAttempts()).isZero();
        assertThat(decision.statusChange()).isEqualTo(AccountStatusChange.UNLOCK);
        assertThat(locked.getFailedAttempts()).isZero();
        assertThat(locked.getLockedUntil()).isNull();
        verify(repository).save(locked);
        verify(accountService).unlock(ACCOUNT_UUID);
    }

    @Test
    void missingRowOnFailureCreatesNewRowViaResolvedAccountId() {
        when(repository.findByAccountUuidForUpdate(ACCOUNT_UUID)).thenReturn(Optional.empty());
        when(repository.findAccountIdByUuid(ACCOUNT_UUID)).thenReturn(Optional.of(ACCOUNT_ID));

        LockoutDecision decision = service.recordFailedAttempt(ACCOUNT_UUID, T0);

        assertThat(decision.failedAttempts()).isEqualTo(1);
        ArgumentCaptor<LockoutState> captor = ArgumentCaptor.forClass(LockoutState.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(captor.getValue().getFailedAttempts()).isEqualTo(1);
    }

    @Test
    void missingRowOnFailureForNonexistentAccountIsANoOp() {
        // Phase 9 Finding B: a failed accountId resolution no-ops instead of throwing, matching
        // recordSuccessfulAttempt's own missing-row behavior.
        when(repository.findByAccountUuidForUpdate(ACCOUNT_UUID)).thenReturn(Optional.empty());
        when(repository.findAccountIdByUuid(ACCOUNT_UUID)).thenReturn(Optional.empty());

        LockoutDecision decision = service.recordFailedAttempt(ACCOUNT_UUID, T0);

        assertThat(decision.failedAttempts()).isEqualTo(1);
        verify(repository, never()).save(any());
        verifyNoInteractions(accountService);
    }

    @Test
    void missingRowOnSuccessIsANoOp() {
        when(repository.findByAccountUuidForUpdate(ACCOUNT_UUID)).thenReturn(Optional.empty());

        LockoutDecision decision = service.recordSuccessfulAttempt(ACCOUNT_UUID, T0);

        assertThat(decision.failedAttempts()).isZero();
        assertThat(decision.statusChange()).isEqualTo(AccountStatusChange.NONE);
        verify(repository, never()).save(any());
        verify(repository, never()).findAccountIdByUuid(any());
        verifyNoInteractions(accountService);
    }

    @Test
    void blockedAttemptWritesNothingAndCallsNothing() {
        Instant lockedUntil = T0.plus(Duration.ofMinutes(15));
        LockoutState locked = existingRow(5, T0, lockedUntil, 1);
        when(repository.findByAccountUuidForUpdate(ACCOUNT_UUID)).thenReturn(Optional.of(locked));

        LockoutDecision decision = service.recordFailedAttempt(ACCOUNT_UUID, lockedUntil.minusMillis(1));

        assertThat(decision.blocked()).isTrue();
        verify(repository, never()).save(any());
        verifyNoInteractions(accountService);
    }

    @Test
    void reLockWhileAccountStatusStillLockedDoesNotThrowAndStillLocksAgain() {
        // T11 AC7 / T12 Phase 3/9 Finding 2: a failure evaluated exactly at lockedUntil (still
        // LOCKED from Account's perspective until AccountService.lock's guard runs) must not
        // throw, and must re-lock with doubled duration.
        Instant lockedUntil = T0.plus(Duration.ofMinutes(15));
        LockoutState locked = existingRow(5, T0, lockedUntil, 1);
        when(repository.findByAccountUuidForUpdate(ACCOUNT_UUID)).thenReturn(Optional.of(locked));

        LockoutDecision decision = assertDoesNotThrowAndReturn(
                () -> service.recordFailedAttempt(ACCOUNT_UUID, lockedUntil));

        assertThat(decision.blocked()).isFalse();
        assertThat(decision.failedAttempts()).isEqualTo(6);
        assertThat(decision.statusChange()).isEqualTo(AccountStatusChange.LOCK);
        assertThat(locked.getLockedUntil()).isEqualTo(lockedUntil.plus(Duration.ofMinutes(30)));
        verify(accountService).lock(ACCOUNT_UUID);
    }

    @Test
    void postUnlockDecaySignalsUnlockAndClearsLockedUntil() {
        // T11 Phase 9 fix: a failure evaluated well after lockedUntil (past the decay window
        // since lastFailedAt) decays instead of re-locking, and signals UNLOCK - a FAILURE
        // outcome routing to accountService.unlock(...), not just SUCCESS.
        Instant lockedUntil = T0.plus(Duration.ofMinutes(15));
        LockoutState locked = existingRow(5, T0, lockedUntil, 1);
        when(repository.findByAccountUuidForUpdate(ACCOUNT_UUID)).thenReturn(Optional.of(locked));
        Instant wellAfterExpiry = T0.plus(Duration.ofMinutes(46));

        LockoutDecision decision = service.recordFailedAttempt(ACCOUNT_UUID, wellAfterExpiry);

        assertThat(decision.statusChange()).isEqualTo(AccountStatusChange.UNLOCK);
        assertThat(decision.lockedUntil()).isNull();
        assertThat(locked.getLockedUntil()).isNull();
        verify(accountService).unlock(ACCOUNT_UUID);
        verify(accountService, never()).lock(any());
    }

    @Test
    void resetLockoutZeroesAnExistingLockedRowAndUnlocks() {
        LockoutState locked = existingRow(5, T0, T0.plus(Duration.ofMinutes(15)), 1);
        when(repository.findByAccountUuidForUpdate(ACCOUNT_UUID)).thenReturn(Optional.of(locked));

        LockoutDecision decision = service.resetLockout(ACCOUNT_UUID);

        assertThat(decision.failedAttempts()).isZero();
        assertThat(locked.getFailedAttempts()).isZero();
        assertThat(locked.getLockedUntil()).isNull();
        verify(repository).save(locked);
        verify(accountService).unlock(ACCOUNT_UUID);
    }

    @Test
    void resetLockoutOnAlreadyCleanAccountIsHarmless() {
        when(repository.findByAccountUuidForUpdate(ACCOUNT_UUID)).thenReturn(Optional.empty());

        assertThatCode(() -> service.resetLockout(ACCOUNT_UUID)).doesNotThrowAnyException();

        verify(repository, never()).save(any());
        verify(accountService).unlock(ACCOUNT_UUID);
    }

    @Test
    void recordFailedAttemptRejectsNullAccountUuidOrNow() {
        assertThatThrownBy(() -> service.recordFailedAttempt(null, T0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.recordFailedAttempt(ACCOUNT_UUID, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void recordSuccessfulAttemptRejectsNullAccountUuidOrNow() {
        assertThatThrownBy(() -> service.recordSuccessfulAttempt(null, T0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.recordSuccessfulAttempt(ACCOUNT_UUID, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void resetLockoutRejectsNullAccountUuid() {
        assertThatThrownBy(() -> service.resetLockout(null)).isInstanceOf(NullPointerException.class);
    }

    private static LockoutState existingRow(int failedAttempts, Instant lastFailedAt, Instant lockedUntil, int lockCount) {
        return LockoutState.of(ACCOUNT_ID,
                new LockoutDecision(failedAttempts, lastFailedAt, lockedUntil, lockCount, false, AccountStatusChange.NONE));
    }

    private static LockoutDecision assertDoesNotThrowAndReturn(java.util.function.Supplier<LockoutDecision> call) {
        LockoutDecision[] result = new LockoutDecision[1];
        assertThatCode(() -> result[0] = call.get()).doesNotThrowAnyException();
        return result[0];
    }
}
