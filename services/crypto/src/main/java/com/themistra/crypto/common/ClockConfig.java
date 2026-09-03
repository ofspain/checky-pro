package com.themistra.crypto.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Injectable clock so scheduled/timestamped code (e.g. {@code OutboxRelay}'s {@code publishedAt})
 * is unit-testable with a fixed instant, never {@code Instant.now()} inline (agents.md testing
 * convention).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
