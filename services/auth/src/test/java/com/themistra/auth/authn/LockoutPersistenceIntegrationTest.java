package com.themistra.auth.authn;

import com.themistra.auth.TestcontainersConfiguration;
import com.themistra.auth.account.AccountService;
import com.themistra.auth.account.AccountStatus;
import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.RegisterAccountRequest;
import com.themistra.auth.audit.AuditService;
import com.themistra.auth.authn.LockoutStateMachine.AccountStatusChange;
import com.themistra.auth.authn.LockoutStateMachine.LockoutDecision;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end against real Postgres (Testcontainers) — verifies what
 * {@link LockoutServiceTest}'s mocked repository structurally cannot: that
 * {@link LockoutStateRepository}'s native queries (the {@code FOR UPDATE OF ls} lock and the
 * UUID-to-internal-id resolution) actually work against a real schema, and that the whole
 * {@code LockoutService} -> {@code AccountService} chain genuinely flips a real
 * {@code Account.status} row, not just a mock verification.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LockoutPersistenceIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private LockoutService lockoutService;

    @Autowired
    private LockoutStateRepository lockoutStateRepository;

    @Autowired
    private AuditService auditService;

    @Test
    void findAccountIdByUuidResolvesTheRealInternalId() {
        UUID accountUuid = registerAndActivate("resolve-id@example.com");

        Optional<Long> accountId = lockoutStateRepository.findAccountIdByUuid(accountUuid);

        assertThat(accountId).isPresent();
    }

    @Test
    void findByAccountUuidForUpdateReturnsEmptyForANeverFailedAccount() {
        UUID accountUuid = registerAndActivate("never-failed@example.com");

        Optional<LockoutState> row = lockoutStateRepository.findByAccountUuidForUpdate(accountUuid);

        assertThat(row).isEmpty();
    }

    @Test
    void fiveFailuresLockARealAccountAndPersistARealRow() {
        UUID accountUuid = registerAndActivate("five-failures@example.com");
        Instant now = Instant.now();

        LockoutDecision decision = null;
        for (int i = 0; i < 5; i++) {
            decision = lockoutService.recordFailedAttempt(accountUuid, now.plusSeconds(i));
        }

        assertThat(decision).isNotNull();
        assertThat(decision.statusChange()).isEqualTo(AccountStatusChange.LOCK);

        AccountResponse account = accountService.getByUuid(accountUuid);
        assertThat(account.status()).isEqualTo(AccountStatus.LOCKED);

        Optional<LockoutState> persisted = lockoutStateRepository.findByAccountUuidForUpdate(accountUuid);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getFailedAttempts()).isEqualTo(5);
        assertThat(persisted.get().getLockCount()).isEqualTo(1);

        // T40, R43: the automatic lock must be audited, matching adminUnlock's already-correct
        // pattern - this was a real gap until this task's own AccountService.lock fix.
        assertThat(auditService.list(accountUuid, Pageable.unpaged()).getContent())
                .anySatisfy(event -> assertThat(event.eventType()).isEqualTo("account.locked"));
    }

    @Test
    void successfulAttemptUnlocksARealLockedAccountAndClearsTheRealRow() {
        UUID accountUuid = registerAndActivate("real-unlock@example.com");
        Instant now = Instant.now();
        for (int i = 0; i < 5; i++) {
            lockoutService.recordFailedAttempt(accountUuid, now.plusSeconds(i));
        }
        assertThat(accountService.getByUuid(accountUuid).status()).isEqualTo(AccountStatus.LOCKED);

        Instant lockedUntil = lockoutStateRepository.findByAccountUuidForUpdate(accountUuid)
                .orElseThrow().getLockedUntil();
        LockoutDecision decision = lockoutService.recordSuccessfulAttempt(accountUuid, lockedUntil);

        assertThat(decision.statusChange()).isEqualTo(AccountStatusChange.UNLOCK);
        assertThat(accountService.getByUuid(accountUuid).status()).isEqualTo(AccountStatus.ACTIVE);
        Optional<LockoutState> persisted = lockoutStateRepository.findByAccountUuidForUpdate(accountUuid);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getFailedAttempts()).isZero();
        assertThat(persisted.get().getLockedUntil()).isNull();

        // T40, R43: the automatic unlock must also be audited (same fix as the lock side above).
        assertThat(auditService.list(accountUuid, Pageable.unpaged()).getContent())
                .anySatisfy(event -> assertThat(event.eventType()).isEqualTo("account.unlocked"));
    }

    @Test
    void concurrentFailedAttemptsDoNotLoseUpdates() throws Exception {
        // Frozen brief Required Tests / Phase 11 Gap 1: proves FOR UPDATE OF ls actually
        // serializes two concurrent transactions on the same row, rather than both reading the
        // same stale snapshot and one increment silently overwriting the other.
        UUID accountUuid = registerAndActivate("concurrent@example.com");
        Instant base = Instant.now();
        for (int i = 0; i < 3; i++) {
            lockoutService.recordFailedAttempt(accountUuid, base.plusSeconds(i));
        }

        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Void> attempt = () -> {
            bothReady.countDown();
            go.await();
            lockoutService.recordFailedAttempt(accountUuid, Instant.now());
            return null;
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> first = executor.submit(attempt);
            Future<Void> second = executor.submit(attempt);
            bothReady.await();
            go.countDown();
            first.get();
            second.get();
        } finally {
            executor.shutdown();
        }

        LockoutState persisted = lockoutStateRepository.findByAccountUuidForUpdate(accountUuid).orElseThrow();
        assertThat(persisted.getFailedAttempts()).isEqualTo(5);
    }

    @Test
    void secondLockCycleAfterExpiryDoublesLockCountOnARealAccount() {
        UUID accountUuid = registerAndActivate("second-cycle@example.com");
        Instant base = Instant.now();
        for (int i = 0; i < 5; i++) {
            lockoutService.recordFailedAttempt(accountUuid, base.plusSeconds(i));
        }
        Instant lockedUntil = lockoutStateRepository.findByAccountUuidForUpdate(accountUuid)
                .orElseThrow().getLockedUntil();

        // A failure right at expiry re-locks immediately (T11 AC7) - second cycle, doubled.
        LockoutDecision decision = lockoutService.recordFailedAttempt(accountUuid, lockedUntil);

        assertThat(decision.statusChange()).isEqualTo(AccountStatusChange.LOCK);
        assertThat(accountService.getByUuid(accountUuid).status()).isEqualTo(AccountStatus.LOCKED);
        LockoutState persisted = lockoutStateRepository.findByAccountUuidForUpdate(accountUuid).orElseThrow();
        assertThat(persisted.getLockCount()).isEqualTo(2);
    }

    @Test
    void blockedAttemptLeavesARealAccountAndRowUnchanged() {
        UUID accountUuid = registerAndActivate("blocked@example.com");
        Instant base = Instant.now();
        for (int i = 0; i < 5; i++) {
            lockoutService.recordFailedAttempt(accountUuid, base.plusSeconds(i));
        }
        LockoutState beforeBlocked = lockoutStateRepository.findByAccountUuidForUpdate(accountUuid).orElseThrow();
        int failedAttemptsBefore = beforeBlocked.getFailedAttempts();
        Instant oneInstantBeforeExpiry = beforeBlocked.getLockedUntil().minusMillis(1);

        LockoutDecision decision = lockoutService.recordFailedAttempt(accountUuid, oneInstantBeforeExpiry);

        assertThat(decision.blocked()).isTrue();
        assertThat(accountService.getByUuid(accountUuid).status()).isEqualTo(AccountStatus.LOCKED);
        LockoutState afterBlocked = lockoutStateRepository.findByAccountUuidForUpdate(accountUuid).orElseThrow();
        assertThat(afterBlocked.getFailedAttempts()).isEqualTo(failedAttemptsBefore);
    }

    @Test
    void resetLockoutOnARealLockedAccountClearsTheRowAndTransitionsToActive() {
        UUID accountUuid = registerAndActivate("real-reset@example.com");
        Instant base = Instant.now();
        for (int i = 0; i < 5; i++) {
            lockoutService.recordFailedAttempt(accountUuid, base.plusSeconds(i));
        }
        assertThat(accountService.getByUuid(accountUuid).status()).isEqualTo(AccountStatus.LOCKED);

        lockoutService.resetLockout(accountUuid);

        assertThat(accountService.getByUuid(accountUuid).status()).isEqualTo(AccountStatus.ACTIVE);
        LockoutState persisted = lockoutStateRepository.findByAccountUuidForUpdate(accountUuid).orElseThrow();
        assertThat(persisted.getFailedAttempts()).isZero();
        assertThat(persisted.getLockedUntil()).isNull();

        // T40, Kimi Phase 8 Finding 2: resetLockout also routes through the now-audited
        // AccountService.unlock(UUID, UUID) path - assert it, not just the row/status change.
        assertThat(auditService.list(accountUuid, Pageable.unpaged()).getContent())
                .anySatisfy(event -> assertThat(event.eventType()).isEqualTo("account.unlocked"));
    }

    private UUID registerAndActivate(String email) {
        AccountResponse registered = accountService.register(new RegisterAccountRequest(email, "correct-horse-battery"));
        accountService.activateEmail(registered.accountUuid(), registered.accountUuid());
        return registered.accountUuid();
    }
}
