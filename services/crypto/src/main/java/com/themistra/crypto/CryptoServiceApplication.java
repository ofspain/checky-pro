package com.themistra.crypto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Crypto Service — the only component that talks to blockchains (ARCHITECTURE §3.4).
 *
 * <p>A daemon with an API, not a request handler: long-running watchers hold subscriptions and
 * polling loops, while a small REST surface accepts watch registrations from the payment service
 * and exposes the internal attestation endpoint.
 */
@SpringBootApplication
public class CryptoServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CryptoServiceApplication.class, args);
    }
}
