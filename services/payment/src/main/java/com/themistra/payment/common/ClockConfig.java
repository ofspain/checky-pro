package com.themistra.payment.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Injectable clock so timestamped code (e.g. {@code OutboxEvent}'s {@code createdAt}/
 * {@code publishedAt}) is unit-testable with a fixed instant, never {@code Instant.now()} inline
 * (agents.md testing convention). Mirrors {@code crypto-service}'s own identical
 * {@code ClockConfig} exactly.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
