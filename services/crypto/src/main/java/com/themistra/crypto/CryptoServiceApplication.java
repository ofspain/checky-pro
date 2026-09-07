package com.themistra.crypto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Themistra Crypto Service — multi-provider blockchain verification and KMS-backed attestation.
 *
 * <p>Spec: spec/crypto-service/design.md. Standing rules: spec/crypto-service/agents.md.</p>
 *
 * <p>{@code @ConfigurationPropertiesScan} added in T03, the first task to introduce any
 * {@code @ConfigurationProperties} class ({@code common.config.*Properties}).
 * {@code @EnableScheduling} added in T04 for {@code OutboxRelay}'s {@code @Scheduled} poll.
 * {@code WatcherRegistry} (T16, O5) is this service's first multi-replica-coordinated scheduled job,
 * but still requires no {@code @EnableSchedulerLock}: it uses ShedLock's programmatic {@code
 * LockProvider} API directly (needed for {@code shardCount} dynamically-named per-shard locks within
 * one reconciliation pass, which the declarative {@code @SchedulerLock} annotation cannot express),
 * not the declarative annotation {@code @EnableSchedulerLock} activates. {@code OutboxRelay} remains
 * deliberately lock-free, see its own class Javadoc.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class CryptoServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CryptoServiceApplication.class, args);
    }
}
