package com.themistra.crypto.screening;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Local development only (infra/local, 2026-10-21). {@link FailClosedScreeningClient} refuses every
 * attestation until a real vendor is chosen (Q2), so no receipt could ever be issued on a developer's
 * machine. This client clears every address except those listed in
 * {@code themistra.crypto.screening.local-dev.blocked-addresses}, which screen as
 * {@link ScreeningOutcome#BLOCKED}, so the HELD path can be tried too.
 *
 * <p>Two guards: it exists only when {@code themistra.crypto.screening.mode=local-dev} is set
 * explicitly, and it refuses to start outside the {@code local} profile. Every call still persists a
 * {@link ScreeningResult} row under its own provider name, so a local result is never mistaken for a
 * real one.</p>
 */
@Component
@ConditionalOnProperty(name = "themistra.crypto.screening.mode", havingValue = "local-dev")
public class LocalDevScreeningClient implements ScreeningClient {

    private static final Logger log = LoggerFactory.getLogger(LocalDevScreeningClient.class);

    public static final String PROVIDER_NAME = "local-dev-stub";

    private final ScreeningResultRepository screeningResultRepository;
    private final Clock clock;
    private final Set<String> blockedAddresses;

    public LocalDevScreeningClient(ScreeningResultRepository screeningResultRepository, Clock clock,
                                   Environment environment,
                                   @Value("${themistra.crypto.screening.local-dev.blocked-addresses:}")
                                   List<String> blockedAddresses) {
        if (!environment.acceptsProfiles(Profiles.of("local"))) {
            throw new IllegalStateException(
                    "themistra.crypto.screening.mode=local-dev is for local development only");
        }
        this.screeningResultRepository = screeningResultRepository;
        this.clock = clock;
        this.blockedAddresses = blockedAddresses.stream()
                .filter(address -> !address.isBlank())
                .map(address -> address.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        log.warn("LOCAL-DEV screening stub active: every address clears except {} listed as blocked. "
                + "Never acceptable outside local development.", this.blockedAddresses.size());
    }

    @Override
    public ScreeningOutcome screen(String chain, String address, String txHash) {
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(address, "address");
        ScreeningOutcome outcome = blockedAddresses.contains(address.toLowerCase(Locale.ROOT))
                ? ScreeningOutcome.BLOCKED : ScreeningOutcome.CLEARED;
        Instant screenedAt = clock.instant();
        screeningResultRepository.save(ScreeningResult.create(chain, address, txHash, outcome, PROVIDER_NAME,
                null, screenedAt));
        return outcome;
    }
}
