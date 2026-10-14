package com.themistra.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * T01 — bare entry point, no other annotation (mirrors {@code crypto-service} T01's own
 * established discipline). {@code @ConfigurationPropertiesScan} added in T03, the first task to
 * introduce any {@code @ConfigurationProperties} class ({@code common.config.*Properties}).
 * {@code @EnableScheduling} added in T04 for {@link com.themistra.payment.events.OutboxRelay}'s
 * {@code @Scheduled} poll. {@code @EnableSchedulerLock} lands only in the task that introduces
 * the first ShedLock-coordinated job (task 8, invoice expiry sweep).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
