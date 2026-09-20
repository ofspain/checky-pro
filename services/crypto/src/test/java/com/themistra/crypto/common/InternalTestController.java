package com.themistra.crypto.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only stand-in for {@code WatchController} (T15) and {@code AttestController} (T21), neither
 * of which exists yet. Mirrors the real internal API's paths and methods exactly (design.md §4c)
 * so {@link ResourceServerConfigIntegrationTest} exercises the actual request-matcher shape
 * {@code ResourceServerConfig} protects, rather than an arbitrary stand-in path. Never shipped in
 * {@code src/main} — test scope only.
 */
@RestController
class InternalTestController {

    @PostMapping("/internal/v1/watches")
    ResponseEntity<Void> registerWatch() {
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/internal/v1/watches/{watchId}")
    ResponseEntity<Void> unregisterWatch(@PathVariable String watchId) {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/internal/v1/attest")
    ResponseEntity<Void> attest() {
        return ResponseEntity.ok().build();
    }
}
