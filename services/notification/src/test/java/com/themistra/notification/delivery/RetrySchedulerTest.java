package com.themistra.notification.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.notification.common.config.RetryProperties;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mocked {@link DeliveryOrchestrator}/{@link DeliveryRetryRepository}, no Spring context, no
 * Docker. This class proves {@code processOne}'s own per-row decision logic given a known
 * {@code DeliveryOrchestrator.DeliveryOutcome} - the backoff formula (pinned semantics) is
 * exercised indirectly through the real {@code reschedule} mutation it produces, not via
 * reflection into the private {@code computeNextAttemptAt}.
 *
 * <p>Phase 11 Gap #1 (a real documentation bug, caught and fixed here): this Javadoc previously
 * claimed {@code sweep}'s own real {@code @Scheduled}/ShedLock timing was "proven at the
 * integration level (`RetrySchedulerIntegrationTest`)" - that file never existed. No real-time,
 * Awaitility-based proof of the actual scheduled firing exists in this suite, by deliberate choice
 * (Phase 5's own plan judged a live multi-replica ShedLock contention test disproportionate, since
 * nothing in this codebase runs multiple real instances in CI) - {@link #sweepLockAnnotationIsPresentWithANonEmptyName}
 * is the structural proof AC6 actually needs, and
 * {@code DeliveryOrchestratorIntegrationTest.sweepOnlyProcessesDueRowsOrderedOldestFirst} proves the
 * real repository query/ordering against a real Postgres instance by calling {@code sweep()}
 * directly rather than waiting on real elapsed time.</p>
 */
class RetrySchedulerTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-03-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    private final DeliveryRetryRepository retryRepository = mock(DeliveryRetryRepository.class);
    private final DeliveryOrchestrator orchestrator = mock(DeliveryOrchestrator.class);
    private final RetryProperties retryProperties = new RetryProperties(5, 30, 3600, 30);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RetryScheduler scheduler =
            new RetryScheduler(retryRepository, orchestrator, retryProperties, objectMapper, CLOCK);

    private static DeliveryRetry retryRow(short attempt, String eventDataJson) {
        return new DeliveryRetry("key-1", UUID.randomUUID(), "EMAIL", "verify_email",
                eventDataJson, attempt, FIXED_INSTANT, FIXED_INSTANT);
    }

    @Test
    void sentOutcomeDeletesTheRetryRowAndNeverReschedules() {
        DeliveryRetry retry = retryRow((short) 1, "{}");
        when(orchestrator.replay(any(), any(), any(), any(), any(), eq((short) 1)))
                .thenReturn(DeliveryOrchestrator.DeliveryOutcome.SENT);

        scheduler.processOne(retry);

        verify(retryRepository).delete(retry);
    }

    @Test
    void suppressedOutcomeDeletesTheRetryRow() {
        DeliveryRetry retry = retryRow((short) 1, "{}");
        when(orchestrator.replay(any(), any(), any(), any(), any(), eq((short) 1)))
                .thenReturn(DeliveryOrchestrator.DeliveryOutcome.SUPPRESSED);

        scheduler.processOne(retry);

        verify(retryRepository).delete(retry);
    }

    @Test
    void permanentFailureOutcomeDeletesTheRetryRow() {
        DeliveryRetry retry = retryRow((short) 1, "{}");
        when(orchestrator.replay(any(), any(), any(), any(), any(), eq((short) 1)))
                .thenReturn(DeliveryOrchestrator.DeliveryOutcome.PERMANENT_FAILURE);

        scheduler.processOne(retry);

        verify(retryRepository).delete(retry);
    }

    @Test
    void transientExhaustedOutcomeDeletesTheRetryRowWithoutRescheduling() {
        DeliveryRetry retry = retryRow((short) 4, "{}");
        when(orchestrator.replay(any(), any(), any(), any(), any(), eq((short) 4)))
                .thenReturn(DeliveryOrchestrator.DeliveryOutcome.TRANSIENT_EXHAUSTED);

        scheduler.processOne(retry);

        verify(retryRepository).delete(retry);
        assertThat(retry.getAttempt()).as("must not be mutated - dead-lettering already happened inside replay")
                .isEqualTo((short) 4);
    }

    /** Pinned backoff semantics: delay for the next attempt is computed from the attempt count
     * AFTER this failure (the count {@code DeliveryOrchestrator.scheduleFirstRetry} itself already
     * used initialBackoffSeconds directly for, going from 0 to 1 attempt made - see
     * {@code DeliveryOrchestratorTest.transientChannelSendFailureInsertsAFirstRetryRowAtAttemptOneWithTheCorrectBackoff}).
     * Here the row already has 1 attempt made; this replay is the 2nd attempt, and it also fails -
     * 2 attempts are now made, so the 3rd attempt's delay is initial * 2^(2-1) = double the initial. */
    @Test
    void transientFailureBringingTheTotalToTwoAttemptsReschedulesAtDoubleTheInitialBackoff() {
        DeliveryRetry retry = retryRow((short) 1, "{}");
        when(orchestrator.replay(any(), any(), any(), any(), any(), eq((short) 1)))
                .thenReturn(DeliveryOrchestrator.DeliveryOutcome.TRANSIENT_FAILURE);

        scheduler.processOne(retry);

        verify(retryRepository, never()).delete(any());
        // Phase 11 Gap #3: retry is detached (fetched outside processOne's own transaction in a
        // real sweep) - mutating it alone, without this explicit save/merge, is exactly the real
        // bug a prior integration test caught (see RetryScheduler.processOne's own comment). This
        // assertion is the regression guard that specific bug never had until now.
        verify(retryRepository).save(retry);
        assertThat(retry.getAttempt()).isEqualTo((short) 2);
        assertThat(retry.getNextAttemptAt()).isEqualTo(FIXED_INSTANT.plusSeconds(60));
    }

    /** 3 attempts already made; this replay (the 4th attempt) also fails transiently, bringing the
     * total to 4 - the 5th attempt's delay is initial * 2^(4-1) = initial * 8. */
    @Test
    void transientFailureBringingTheTotalToFourAttemptsReschedulesWithExponentiallyGrownBackoff() {
        DeliveryRetry retry = retryRow((short) 3, "{}");
        when(orchestrator.replay(any(), any(), any(), any(), any(), eq((short) 3)))
                .thenReturn(DeliveryOrchestrator.DeliveryOutcome.TRANSIENT_FAILURE);

        scheduler.processOne(retry);

        verify(retryRepository).save(retry);
        assertThat(retry.getAttempt()).isEqualTo((short) 4);
        assertThat(retry.getNextAttemptAt()).isEqualTo(FIXED_INSTANT.plusSeconds(240));
    }

    @Test
    void transientFailureBackoffNeverExceedsTheConfiguredMaximum() {
        RetryProperties tightCap = new RetryProperties(20, 30, 100, 30);
        RetryScheduler cappedScheduler = new RetryScheduler(retryRepository, orchestrator, tightCap, objectMapper, CLOCK);
        DeliveryRetry retry = retryRow((short) 10, "{}");
        when(orchestrator.replay(any(), any(), any(), any(), any(), eq((short) 10)))
                .thenReturn(DeliveryOrchestrator.DeliveryOutcome.TRANSIENT_FAILURE);

        cappedScheduler.processOne(retry);

        assertThat(retry.getNextAttemptAt()).isEqualTo(FIXED_INSTANT.plusSeconds(100));
    }

    /** Phase 3 Finding #7: a poison-pill row (unreadable event_data_json) is dead-lettered directly
     * and removed - orchestrator.replay must never even be called for it. */
    @Test
    void unreadableEventDataJsonDeadLettersDirectlyWithoutEverCallingReplay() {
        DeliveryRetry retry = retryRow((short) 2, "not valid json{{{");

        scheduler.processOne(retry);

        verify(orchestrator).recordUnrecoverableFailure(eq(retry.getAccountUuid()), eq("EMAIL"),
                eq("key-1"), eq((short) 3), any());
        verify(retryRepository).delete(retry);
        verify(orchestrator, never()).replay(any(), any(), any(), any(), any(), anyShort());
    }

    @Test
    void passesTheDeserializedEventDataAndStoredAttemptCountToReplay() {
        DeliveryRetry retry = retryRow((short) 2, "{\"sourceEventKey\":\"key-1\",\"token\":\"tok-1\"}");
        when(orchestrator.replay(any(), any(), any(), any(), any(), anyShort()))
                .thenReturn(DeliveryOrchestrator.DeliveryOutcome.SENT);

        scheduler.processOne(retry);

        verify(orchestrator).replay(eq(retry.getAccountUuid()), eq("EMAIL"), eq("verify_email"), eq("key-1"),
                eq(Map.of("sourceEventKey", "key-1", "token", "tok-1")), eq((short) 2));
    }

    @Test
    void sweepSkipsAFailingRowWithoutAbortingTheRest() {
        DeliveryRetry badRow = retryRow((short) 1, "not valid json{{{");
        DeliveryRetry goodRow = retryRow((short) 1, "{}");
        when(retryRepository.findByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(FIXED_INSTANT))
                .thenReturn(List.of(badRow, goodRow));
        org.mockito.Mockito.doThrow(new RuntimeException("db down while recording"))
                .when(orchestrator).recordUnrecoverableFailure(any(), any(), any(), anyShort(), any());
        when(orchestrator.replay(any(), any(), any(), any(), any(), eq((short) 1)))
                .thenReturn(DeliveryOrchestrator.DeliveryOutcome.SENT);

        scheduler.sweep();

        verify(retryRepository).delete(goodRow);
    }

    /** Phase 8 Finding #5: structural, not behavioral - {@code DeliveryOutcome} has exactly 5 real
     * values today, so a 6th cannot be fabricated through Mockito to exercise this branch directly.
     * Confirms the defensive {@code default} arm exists in source, mirroring
     * {@code DeliveryOrchestratorTest.replayGuardsAgainstANullTemplateNameMirroringDispatchOneChannel}'s
     * own identical structural-test precedent for an otherwise-unreachable defensive branch. */
    @Test
    void processOneSwitchHasADefaultArmGuardingAFutureUnhandledOutcome() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/themistra/notification/delivery/RetryScheduler.java"));

        assertThat(source)
                .as("the switch over DeliveryOutcome must have a default arm, since a switch "
                        + "statement (unlike an expression) is not compiler-checked for exhaustiveness")
                .contains("default -> throw new IllegalStateException(");
    }

    /** Phase 11 Gap #2: AC6 requires {@code sweep} itself to be ShedLock-guarded - the only
     * pre-existing coverage (`T01SkeletonRegressionTest`) checked `@EnableSchedulerLock` on the
     * application class, never the annotation on this actual method. A missing/misconfigured
     * `@SchedulerLock` here would let two replicas run overlapping sweeps undetected. */
    @Test
    void sweepLockAnnotationIsPresentWithANonEmptyName() throws NoSuchMethodException {
        Method sweep = RetryScheduler.class.getMethod("sweep");
        SchedulerLock lock = sweep.getAnnotation(SchedulerLock.class);

        assertThat(lock).as("sweep() must be @SchedulerLock-guarded (AC6)").isNotNull();
        assertThat(lock.name()).isNotBlank();
        assertThat(lock.lockAtMostFor()).isNotBlank();
    }
}
