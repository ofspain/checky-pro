package com.themistra.crypto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Themistra Crypto Service — multi-provider blockchain verification and KMS-backed attestation.
 *
 * <p>Spec: spec/crypto-service/design.md. Standing rules: spec/crypto-service/agents.md.</p>
 *
 * <p>Deliberately bare (T01): no {@code @ConfigurationPropertiesScan}, {@code @EnableScheduling},
 * or {@code @EnableSchedulerLock} yet — none has a class to scan or a job to lock until a later
 * task introduces one (design.md O5). Added here, not mirrored from auth's own annotations, the
 * moment the first such class exists.</p>
 */
@SpringBootApplication
public class CryptoServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CryptoServiceApplication.class, args);
    }
}
