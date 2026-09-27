package com.themistra.notification.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Injectable clock so timestamped code (e.g. {@code IdempotencyGuard}'s {@code processedAt}) is
 * unit-testable with a fixed instant, never {@code Instant.now()} inline (`agents.md` testing
 * convention). Mirrors {@code services/crypto}'s own identical class.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
