package com.themistra.auth.cleanup;

import com.themistra.auth.TestcontainersConfiguration;
import com.themistra.auth.account.AccountService;
import com.themistra.auth.account.VerificationToken;
import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.RegisterAccountRequest;
import com.themistra.auth.common.Hashing;
import com.themistra.auth.token.RefreshTokenArchiveEntry;
import com.themistra.auth.token.RefreshTokenFamily;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end for the T30 cleanup job (R40), against real Postgres (Testcontainers). Lives in
 * {@code cleanup} even though it seeds {@code VerificationToken} (account) and
 * {@code RefreshTokenFamily} (token) fixtures directly via a raw {@link EntityManager} — both
 * entity classes are public, so this is a same-technique extension of the pattern
 * {@code SessionIntegrationTest}/{@code RefreshTokenFamilyIntegrationTest} already use, not a
 * repository-visibility violation (their package-private repositories are never imported here).
 *
 * <p>{@code cleanupJob.run()} is invoked directly rather than waiting for its real 2am-daily cron
 * trigger; the {@link Awaitility} wrapper (per the task statement's own instruction) still polls
 * for the resulting row-level effects, resilient to the exact call being synchronous today.</p>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CleanupIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private CleanupJob cleanupJob;

    @Autowired
    private AccountService accountService;

    @PersistenceContext
    private EntityManager entityManager;

    @Test // Named test, R40 - AC1 (expired tokens) + AC2 (old revoked families) together
    void shouldCleanupExpiredTokensAndFamilies() {
        UUID accountUuid = registerAndActivate("cleanup-test@example.com");
        Long accountId = resolveAccountId(accountUuid);
        Instant now = Instant.now();

        VerificationToken expiredToken = VerificationToken.create(
                accountId, VerificationToken.Purpose.EMAIL_VERIFY, "hash-" + UUID.randomUUID(),
                now.minusSeconds(7200), now.minusSeconds(3600));
        VerificationToken freshToken = VerificationToken.create(
                accountId, VerificationToken.Purpose.EMAIL_VERIFY, "hash-" + UUID.randomUUID(),
                now, now.plusSeconds(1800));
        entityManager.persist(expiredToken);
        entityManager.persist(freshToken);

        // AC2/AC3: revoked well past the 90-day retention, with an archive row that must cascade away.
        RefreshTokenFamily oldRevokedFamily = RefreshTokenFamily.start(
                "auth-old-" + UUID.randomUUID(), accountUuid.toString(), null,
                Hashing.sha256("old-token-" + UUID.randomUUID()), now.minus(100, ChronoUnit.DAYS));
        oldRevokedFamily.revoke("TEST_OLD", now.minus(95, ChronoUnit.DAYS));
        entityManager.persist(oldRevokedFamily);
        entityManager.persist(new RefreshTokenArchiveEntry(
                oldRevokedFamily.getFamilyId(), Hashing.sha256("old-superseded-" + UUID.randomUUID()),
                now.minus(96, ChronoUnit.DAYS)));

        // Survivor: revoked, but well within the 90-day retention.
        RefreshTokenFamily recentlyRevokedFamily = RefreshTokenFamily.start(
                "auth-recent-" + UUID.randomUUID(), accountUuid.toString(), null,
                Hashing.sha256("recent-token-" + UUID.randomUUID()), now);
        recentlyRevokedFamily.revoke("TEST_RECENT", now.minusSeconds(60));
        entityManager.persist(recentlyRevokedFamily);

        // Survivor: never revoked at all, regardless of age.
        RefreshTokenFamily neverRevokedFamily = RefreshTokenFamily.start(
                "auth-active-" + UUID.randomUUID(), accountUuid.toString(), null,
                Hashing.sha256("active-token-" + UUID.randomUUID()), now.minus(200, ChronoUnit.DAYS));
        entityManager.persist(neverRevokedFamily);

        String staleLockName = "stale-lock-" + UUID.randomUUID();
        String freshLockName = "fresh-lock-" + UUID.randomUUID();
        insertShedLockRow(staleLockName, now.minus(30, ChronoUnit.DAYS)); // AC4: older than 7-day retention
        insertShedLockRow(freshLockName, now.plusSeconds(600)); // AC4: currently-held, must never be pruned

        entityManager.flush();

        cleanupJob.run();

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            entityManager.clear();

            assertThat(entityManager.find(VerificationToken.class, expiredToken.getId())).isNull();
            assertThat(entityManager.find(VerificationToken.class, freshToken.getId())).isNotNull();

            assertThat(entityManager.find(RefreshTokenFamily.class, oldRevokedFamily.getFamilyId())).isNull();
            assertThat(entityManager.find(RefreshTokenFamily.class, recentlyRevokedFamily.getFamilyId()))
                    .isNotNull();
            assertThat(entityManager.find(RefreshTokenFamily.class, neverRevokedFamily.getFamilyId()))
                    .isNotNull();

            assertThat(countArchiveRowsFor(oldRevokedFamily.getFamilyId())).isZero();

            assertThat(shedLockRowExists(staleLockName)).isFalse();
            assertThat(shedLockRowExists(freshLockName)).isTrue();
        });
    }

    private UUID registerAndActivate(String email) {
        AccountResponse registered = accountService.register(new RegisterAccountRequest(email, PASSWORD));
        accountService.activateEmail(registered.accountUuid(), registered.accountUuid());
        return registered.accountUuid();
    }

    private Long resolveAccountId(UUID accountUuid) {
        Number id = (Number) entityManager
                .createNativeQuery("SELECT id FROM accounts WHERE account_uuid = ?")
                .setParameter(1, accountUuid)
                .getSingleResult();
        return id.longValue();
    }

    private void insertShedLockRow(String name, Instant lockUntil) {
        entityManager.createNativeQuery(
                        "INSERT INTO shedlock (name, lock_until, locked_at, locked_by) VALUES (?, ?, ?, ?)")
                .setParameter(1, name)
                .setParameter(2, lockUntil)
                .setParameter(3, Instant.now())
                .setParameter(4, "test")
                .executeUpdate();
    }

    private boolean shedLockRowExists(String name) {
        Number count = (Number) entityManager
                .createNativeQuery("SELECT count(*) FROM shedlock WHERE name = ?")
                .setParameter(1, name)
                .getSingleResult();
        return count.longValue() > 0;
    }

    private long countArchiveRowsFor(UUID familyId) {
        Number count = (Number) entityManager
                .createNativeQuery("SELECT count(*) FROM refresh_token_archive WHERE family_id = ?")
                .setParameter(1, familyId)
                .getSingleResult();
        return count.longValue();
    }
}
