package com.themistra.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * T01 — bare entry point, no other annotation (mirrors {@code crypto-service} T01's own
 * established discipline). {@code @ConfigurationPropertiesScan} added in T03, the first task to
 * introduce any {@code @ConfigurationProperties} class ({@code common.config.*Properties}).
 * {@code @EnableScheduling}/{@code @EnableSchedulerLock} land only in the task that introduces
 * the thing each one enables.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
