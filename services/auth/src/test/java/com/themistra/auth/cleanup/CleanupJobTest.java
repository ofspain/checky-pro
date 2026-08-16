package com.themistra.auth.cleanup;

import com.themistra.auth.account.VerificationTokenService;
import com.themistra.auth.token.RefreshTokenTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CleanupJob} — T30, R40. Plain JUnit + Mockito, fixed {@link Clock}, all
 * three collaborators mocked so each step's failure-isolation and cutoff-derivation can be proven
 * independently of any real database (Testcontainers-backed proof is
 * {@code CleanupIntegrationTest}).
 */
@ExtendWith(MockitoExtension.class)
class CleanupJobTest {

    private static final Instant NOW = Instant.parse("2026-08-16T02:00:00Z");
    private static final int TOKEN_RETENTION_DAYS = 7;
    private static final int FAMILY_RETENTION_DAYS = 90;

    @Mock
    private VerificationTokenService verificationTokenService;

    @Mock
    private RefreshTokenTracker refreshTokenTracker;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private CleanupJob job;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        CleanupProperties properties =
                new CleanupProperties("0 2 * * *", TOKEN_RETENTION_DAYS, FAMILY_RETENTION_DAYS);
        job = new CleanupJob(verificationTokenService, refreshTokenTracker, jdbcTemplate, properties, fixedClock);
    }

    @Test
    void runDeletesExpiredTokensUsingCurrentInstantAsCutoff() {
        job.run();

        verify(verificationTokenService).deleteExpiredTokens(NOW);
    }

    @Test
    void runDeletesOldRevokedFamiliesUsingFamilyRetentionDaysCutoff() {
        job.run();

        verify(refreshTokenTracker).deleteRevokedFamiliesOlderThan(NOW.minus(FAMILY_RETENTION_DAYS, ChronoUnit.DAYS));
    }

    @Test
    void runDeletesStaleShedLockRowsUsingTokenRetentionDaysCutoffAndTheSafetyGuardPredicate() {
        job.run();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), cutoffCaptor.capture());

        assertThat(sqlCaptor.getValue())
                .contains("DELETE FROM shedlock")
                .contains("lock_until < ?")
                .contains("lock_until < now()"); // D4's safety guard: never prune a currently-held lock
        assertThat(cutoffCaptor.getValue()).isEqualTo(NOW.minus(TOKEN_RETENTION_DAYS, ChronoUnit.DAYS));
    }

    @Test // D2/AC7 - a failure in the token-deletion step must not prevent the other two
    void runContinuesPastAFailureInDeleteExpiredTokens() {
        when(verificationTokenService.deleteExpiredTokens(any())).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> job.run()).doesNotThrowAnyException();

        verify(refreshTokenTracker).deleteRevokedFamiliesOlderThan(any());
        verify(jdbcTemplate).update(any(String.class), any(Instant.class));
    }

    @Test // D2/AC7 - a failure in the family-deletion step must not prevent the other two
    void runContinuesPastAFailureInDeleteOldRevokedFamilies() {
        when(refreshTokenTracker.deleteRevokedFamiliesOlderThan(any())).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> job.run()).doesNotThrowAnyException();

        verify(verificationTokenService).deleteExpiredTokens(any());
        verify(jdbcTemplate).update(any(String.class), any(Instant.class));
    }

    @Test // D2/AC7 - a failure in the ShedLock-row-deletion step must not roll back or hide the
          // fact the other two already ran (both precede it in run()'s own sequence)
    void runContinuesPastAFailureInDeleteStaleShedLockRows() {
        when(jdbcTemplate.update(any(String.class), eq(NOW.minus(TOKEN_RETENTION_DAYS, ChronoUnit.DAYS))))
                .thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> job.run()).doesNotThrowAnyException();

        verify(verificationTokenService).deleteExpiredTokens(any());
        verify(refreshTokenTracker).deleteRevokedFamiliesOlderThan(any());
    }
}
