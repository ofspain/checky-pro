package com.themistra.payment.common;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 11 Gap 3 — AC7 requires a single, injectable {@code Clock.systemUTC()} bean; without this
 * test the class could be deleted, or changed to a different zone/implementation, with nothing
 * failing.
 */
class ClockConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(ClockConfig.class);

    @Test
    void exposesExactlyOneUtcClockBean() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Clock.class);
            Clock clock = context.getBean(Clock.class);
            assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
            assertThat(Duration.between(clock.instant(), Instant.now()).abs())
                    .as("sanity: a real, live clock, not a stub")
                    .isLessThan(Duration.ofSeconds(5));
        });
    }
}
