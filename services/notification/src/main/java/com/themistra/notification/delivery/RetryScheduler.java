package com.themistra.notification.delivery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.notification.common.config.RetryProperties;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * T14's own ShedLock-guarded sweep (L7, {@code agents.md}'s "multi-replica scheduled jobs are
 * ShedLock-guarded" rule) - polls {@code delivery_retry} for due rows and replays each one through
 * {@link DeliveryOrchestrator#replay}. {@link #sweep} itself is NOT {@code @Transactional} (the
 * frozen brief's own explicit constraint: one transaction per row, never one for the whole sweep,
 * so one row's own rollback can never affect another's already-committed outcome); each row's own
 * {@link #processOne} is. One row's own failure - including an exception {@code processOne} itself
 * throws - is logged and skipped, never aborting the sweep for the rows after it (AC9).
 */
@Component
public class RetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetryScheduler.class);

    private final DeliveryRetryRepository retryRepository;
    private final DeliveryOrchestrator orchestrator;
    private final RetryProperties retryProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RetryScheduler(DeliveryRetryRepository retryRepository, DeliveryOrchestrator orchestrator,
                           RetryProperties retryProperties, ObjectMapper objectMapper, Clock clock) {
        this.retryRepository = retryRepository;
        this.orchestrator = orchestrator;
        this.retryProperties = retryProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    // Phase 3 Finding #8: this is, today, the only scheduled task in this service - sizing a
    // dedicated thread pool now would be speculative configuration for a contention problem that
    // cannot yet occur. Whichever future task adds a second scheduled job must revisit this.
    @Scheduled(fixedDelayString = "${themistra.notification.retry.scheduler-interval-seconds}",
            timeUnit = TimeUnit.SECONDS)
    @SchedulerLock(name = "retry-scheduler", lockAtMostFor = "5m", lockAtLeastFor = "10s")
    public void sweep() {
        List<DeliveryRetry> dueRows =
                retryRepository.findByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(clock.instant());
        for (DeliveryRetry retry : dueRows) {
            try {
                processOne(retry);
            } catch (Exception e) {
                log.error("Unable to process delivery_retry row id={} for accountUuid={}, channel={}",
                        retry.getId(), retry.getAccountUuid(), retry.getChannel(), e);
            }
        }
    }

    @Transactional
    void processOne(DeliveryRetry retry) {
        Map<String, String> eventData;
        try {
            eventData = objectMapper.readValue(retry.getEventDataJson(), new TypeReference<Map<String, String>>() {
            });
        } catch (JsonProcessingException e) {
            // Phase 3 Finding #7: a poison-pill row (corrupted event_data_json) can never be
            // replayed - dead-letter it directly and remove it, rather than retrying it forever.
            log.error("delivery_retry row id={} has unreadable event_data_json, dead-lettering",
                    retry.getId(), e);
            orchestrator.recordUnrecoverableFailure(retry.getAccountUuid(), retry.getChannel(),
                    retry.getSourceEventKey(), (short) (retry.getAttempt() + 1), e.getMessage());
            retryRepository.delete(retry);
            return;
        }

        DeliveryOrchestrator.DeliveryOutcome outcome = orchestrator.replay(retry.getAccountUuid(),
                retry.getChannel(), retry.getNotificationKind(), retry.getSourceEventKey(), eventData,
                retry.getAttempt());

        switch (outcome) {
            case SENT, SUPPRESSED, PERMANENT_FAILURE, TRANSIENT_EXHAUSTED -> retryRepository.delete(retry);
            case TRANSIENT_FAILURE -> {
                // retry was fetched by sweep() outside of this method's own transaction (and, in a
                // real sweep, outside any transaction at all), so it is detached here - dirty
                // checking alone would never persist the mutation. retryRepository.save (a merge,
                // since the entity already has an id) is required - confirmed by a real test
                // failure (the real integration test's own refetched row showed the OLD attempt
                // count) before this fix was added, not assumed safe.
                short newAttempt = (short) (retry.getAttempt() + 1);
                retry.reschedule(newAttempt, computeNextAttemptAt(newAttempt));
                retryRepository.save(retry);
            }
        }
    }

    /** Pinned backoff semantics (Phase 3 Findings #4/#5, frozen brief): for {@code n} attempts
     * already made, the next attempt's delay is {@code min(initialBackoffSeconds * 2^(n-1),
     * maxBackoffSeconds)} - the first retry waits exactly {@code initialBackoffSeconds}, doubling on
     * each subsequent failure, capped at {@code maxBackoffSeconds}. */
    private Instant computeNextAttemptAt(short attemptsAlreadyMade) {
        long uncappedSeconds = (long) retryProperties.initialBackoffSeconds() * (1L << (attemptsAlreadyMade - 1));
        long seconds = Math.min(uncappedSeconds, retryProperties.maxBackoffSeconds());
        return clock.instant().plusSeconds(seconds);
    }
}
