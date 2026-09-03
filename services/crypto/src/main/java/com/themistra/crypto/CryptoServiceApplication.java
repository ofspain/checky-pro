package com.themistra.crypto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Themistra Crypto Service — multi-provider blockchain verification and KMS-backed attestation.
 *
 * <p>Spec: spec/crypto-service/design.md. Standing rules: spec/crypto-service/agents.md.</p>
 *
 * <p>{@code @ConfigurationPropertiesScan} added in T03, the first task to introduce any
 * {@code @ConfigurationProperties} class ({@code common.config.*Properties}). Still no
 * {@code @EnableScheduling}/{@code @EnableSchedulerLock} — no scheduled/locked job exists until a
 * later task introduces one (design.md O5).</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CryptoServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CryptoServiceApplication.class, args);
    }
}
