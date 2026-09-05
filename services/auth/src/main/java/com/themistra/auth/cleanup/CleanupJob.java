package com.themistra.auth.cleanup;

import com.themistra.auth.account.VerificationTokenService;
import com.themistra.auth.token.RefreshTokenTracker;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Nightly cleanup (T30, R40): hard-deletes expired verification tokens, refresh-token families
 * revoked longer ago than the configured retention (their {@code refresh_token_archive} rows go
 * with them via the existing {@code ON DELETE CASCADE}, V2), and stale {@code shedlock} rows.
 *
 * <p>Guarded by {@code @SchedulerLock} (agents.md: "multi-replica scheduled jobs are
 * ShedLock-guarded") since, unlike {@link com.themistra.auth.events.OutboxRelay}'s own
 * {@code @Scheduled} method, a hard delete has no idempotency of its own to fall back on if two
 * replicas ran it concurrently.</p>
 *
 * <p>The three steps are each independently try/catch-guarded and run outside any shared
 * transaction: {@link #deleteExpiredTokens()} and {@link #deleteOldRevokedFamilies()} delegate to
 * a target method that owns its own {@code @Transactional} boundary, and
 * {@link #deleteStaleShedLockRows()} issues its own single statement — so one step failing is
 * logged and never rolls back or blocks the other two.</p>
 */
@Component
public class CleanupJob {

    private static final Logger log = LoggerFactory.getLogger(CleanupJob.class);

    private final VerificationTokenService verificationTokenService;
    private final RefreshTokenTracker refreshTokenTracker;
    private final JdbcTemplate jdbcTemplate;
    private final CleanupProperties properties;
    private final Clock clock;

    public CleanupJob(VerificationTokenService verificationTokenService,
                      RefreshTokenTracker refreshTokenTracker,
                      JdbcTemplate jdbcTemplate,
                      CleanupProperties properties,
                      Clock clock) {
        this.verificationTokenService = verificationTokenService;
        this.refreshTokenTracker = refreshTokenTracker;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${themistra.auth.cleanup.cron}")
    @SchedulerLock(name = "auth-cleanup-job", lockAtLeastFor = "PT1M", lockAtMostFor = "PT1H")
    public void run() {
        deleteExpiredTokens();
        deleteOldRevokedFamilies();
        deleteStaleShedLockRows();
    }

    private void deleteExpiredTokens() {
        try {
            int deleted = verificationTokenService.deleteExpiredTokens(clock.instant());
            log.info("Cleanup: deleted {} expired verification tokens", deleted);
        } catch (Exception e) {
            log.error("Cleanup: failed to delete expired verification tokens", e);
        }
    }

    private void deleteOldRevokedFamilies() {
        try {
            Instant cutoff = clock.instant().minus(properties.familyRetentionDays(), ChronoUnit.DAYS);
            int deleted = refreshTokenTracker.deleteRevokedFamiliesOlderThan(cutoff);
            log.info("Cleanup: deleted {} old revoked refresh-token families", deleted);
        } catch (Exception e) {
            log.error("Cleanup: failed to delete old revoked refresh-token families", e);
        }
    }

    private void deleteStaleShedLockRows() {
        try {
            Instant cutoff = clock.instant().minus(properties.tokenRetentionDays(), ChronoUnit.DAYS);
            // A plain Object... varargs bind (jdbcTemplate.update(sql, cutoff)) fails at runtime -
            // the resolved pgjdbc driver's 2-arg setObject cannot infer a SQL type for a raw
            // Instant ("Can't infer the SQL type to use for an instance of java.time.Instant"),
            // discovered only once this ran against a real database for the first time (Docker
            // was never available before). An explicit TIMESTAMP_WITH_TIMEZONE type hint alone
            // isn't enough either - pgjdbc's own setObject only accepts OffsetDateTime (or
            // PGTimestamp) for that SQL type, throwing "Cannot cast an instance of
            // java.time.Instant" otherwise (verified directly against pgjdbc's PgPreparedStatement
            // source). Converting to OffsetDateTime at UTC is unambiguous - equivalent to the
            // Instant, and immune to java.sql.Timestamp's JVM-default-timezone risk.
            OffsetDateTime cutoffUtc = cutoff.atOffset(ZoneOffset.UTC);
            int deleted = jdbcTemplate.update(
                    "DELETE FROM shedlock WHERE lock_until < ? AND lock_until < now()",
                    ps -> ps.setObject(1, cutoffUtc, java.sql.Types.TIMESTAMP_WITH_TIMEZONE));
            log.info("Cleanup: deleted {} stale shedlock rows", deleted);
        } catch (Exception e) {
            log.error("Cleanup: failed to delete stale shedlock rows", e);
        }
    }
}
