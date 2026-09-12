package com.themistra.crypto.screening;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * The fail-closed {@link ScreeningClient} stub until a real vendor is chosen (Q2, {@code package.md}
 * §11). Performs no network I/O of any kind - there is no vendor wired in yet, so there is nothing to
 * call. Never returns {@link ScreeningOutcome#CLEARED} or {@link ScreeningOutcome#BLOCKED} for any
 * input: claiming a sanctioned hit was actually detected ({@code BLOCKED}) would be as false as
 * claiming one was ruled out ({@code CLEARED}), since no real screening ever ran (frozen brief Phase 4,
 * Finding #1 disposition). Every call still persists a {@link ScreeningResult} row, honoring
 * {@link ScreeningClient}'s own audit-trail contract.
 */
@Component
public class FailClosedScreeningClient implements ScreeningClient {

    private static final Logger log = LoggerFactory.getLogger(FailClosedScreeningClient.class);

    /** Persisted as {@link ScreeningResult#provider()} - a public constant so downstream queries/
     * dashboards never depend on a private literal that could be silently renamed (frozen brief Phase
     * 4, Finding #7 disposition). */
    public static final String PROVIDER_NAME = "fail-closed-stub";

    private final ScreeningResultRepository screeningResultRepository;
    private final Clock clock;

    public FailClosedScreeningClient(ScreeningResultRepository screeningResultRepository, Clock clock) {
        this.screeningResultRepository = screeningResultRepository;
        this.clock = clock;
    }

    @Override
    public ScreeningOutcome screen(String chain, String address, String txHash) {
        log.warn("Screening fail-closed stub active - address was not screened against a real vendor "
                + "(chain={}, txHash={})", chain, txHash);
        Instant screenedAt = clock.instant();
        ScreeningResult result = ScreeningResult.create(chain, address, txHash, ScreeningOutcome.ERROR,
                PROVIDER_NAME, null, screenedAt);
        screeningResultRepository.save(result);
        return ScreeningOutcome.ERROR;
    }
}
