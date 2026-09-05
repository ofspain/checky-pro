package com.themistra.auth.cleanup;

import com.themistra.auth.account.VerificationTokenService;
import com.themistra.auth.token.RefreshTokenTracker;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

    @Test // Kimi Phase 11 Gap 3 - every other test here either mocks collaborators or calls
          // run() directly, bypassing ShedLock's AOP proxy entirely; a future edit that silently
          // dropped @SchedulerLock (breaking AC5's multi-replica guarantee) would still pass every
          // other test in this file, so this reflects on the method itself to catch it.
    void runIsAnnotatedWithScheduledAndSchedulerLock() throws NoSuchMethodException {
        Method run = CleanupJob.class.getDeclaredMethod("run");

        Scheduled scheduled = run.getAnnotation(Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("${themistra.auth.cleanup.cron}");

        SchedulerLock schedulerLock = run.getAnnotation(SchedulerLock.class);
        assertThat(schedulerLock).isNotNull();
        assertThat(schedulerLock.name()).isEqualTo("auth-cleanup-job");
        assertThat(schedulerLock.lockAtLeastFor()).isEqualTo("PT1M");
        assertThat(schedulerLock.lockAtMostFor()).isEqualTo("PT1H");
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
    void runDeletesStaleShedLockRowsUsingTokenRetentionDaysCutoffAndTheSafetyGuardPredicate() throws Exception {
        job.run();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PreparedStatementSetter> setterCaptor = ArgumentCaptor.forClass(PreparedStatementSetter.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), setterCaptor.capture());

        // Kimi Phase 11 Gap 4 - exact match, not substring: a substring check alone wouldn't
        // catch the two lock_until clauses being ORed instead of ANDed, which would defeat D4's
        // safety guard while still containing all three substrings.
        assertThat(sqlCaptor.getValue())
                .isEqualTo("DELETE FROM shedlock WHERE lock_until < ? AND lock_until < now()");

        // The cutoff is bound as an OffsetDateTime (UTC) via an explicit TIMESTAMP_WITH_TIMEZONE
        // setObject hint - neither a raw Object... varargs bind nor a raw Instant with this type
        // hint work against the real driver (verified against pgjdbc's own source: it only
        // accepts OffsetDateTime/PGTimestamp for TIMESTAMP_WITH_TIMEZONE), only discoverable once
        // this actually ran against a real database.
        PreparedStatement ps = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(ps);
        verify(ps).setObject(1,
                NOW.minus(TOKEN_RETENTION_DAYS, ChronoUnit.DAYS).atOffset(ZoneOffset.UTC),
                Types.TIMESTAMP_WITH_TIMEZONE);
    }

    @Test // D2/AC7 - a failure in the token-deletion step must not prevent the other two
    void runContinuesPastAFailureInDeleteExpiredTokens() {
        when(verificationTokenService.deleteExpiredTokens(any())).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> job.run()).doesNotThrowAnyException();

        verify(refreshTokenTracker).deleteRevokedFamiliesOlderThan(any());
        verify(jdbcTemplate).update(any(String.class), any(PreparedStatementSetter.class));
    }

    @Test // D2/AC7 - a failure in the family-deletion step must not prevent the other two
    void runContinuesPastAFailureInDeleteOldRevokedFamilies() {
        when(refreshTokenTracker.deleteRevokedFamiliesOlderThan(any())).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> job.run()).doesNotThrowAnyException();

        verify(verificationTokenService).deleteExpiredTokens(any());
        verify(jdbcTemplate).update(any(String.class), any(PreparedStatementSetter.class));
    }

    @Test // D2/AC7 - a failure in the ShedLock-row-deletion step must not roll back or hide the
          // fact the other two already ran (both precede it in run()'s own sequence)
    void runContinuesPastAFailureInDeleteStaleShedLockRows() {
        when(jdbcTemplate.update(any(String.class), any(PreparedStatementSetter.class)))
                .thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> job.run()).doesNotThrowAnyException();

        verify(verificationTokenService).deleteExpiredTokens(any());
        verify(refreshTokenTracker).deleteRevokedFamiliesOlderThan(any());
    }
}
