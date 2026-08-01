package com.themistra.auth.authn;

import com.themistra.auth.TestcontainersConfiguration;
import com.themistra.auth.account.AccountService;
import com.themistra.auth.account.AccountStatus;
import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.RegisterAccountRequest;
import com.themistra.auth.authn.LockoutStateMachine.AccountStatusChange;
import com.themistra.auth.authn.LockoutStateMachine.LockoutDecision;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

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
    }

    private UUID registerAndActivate(String email) {
        AccountResponse registered = accountService.register(new RegisterAccountRequest(email, "correct-horse-battery"));
        accountService.activateEmail(registered.accountUuid(), registered.accountUuid());
        return registered.accountUuid();
    }
}
