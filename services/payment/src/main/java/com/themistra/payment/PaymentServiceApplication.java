package com.themistra.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * T01 — bare entry point. No other annotation is added here; each one
 * ({@code @ConfigurationPropertiesScan}, {@code @EnableScheduling}, {@code @EnableSchedulerLock},
 * etc.) is added only in the task that actually introduces the thing it enables (frozen brief,
 * mirrors {@code crypto-service} T01's own established discipline).
 */
@SpringBootApplication
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
