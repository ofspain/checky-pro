package com.themistra.crypto.common;

import org.springframework.context.annotation.Profile;
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
 *
 * <p>Open-issues sweep (2026-10-19): the real controllers now exist, so this stand-in is only
 * loaded under the {@link #PROFILE} profile. Without it, component scanning put it in every
 * full-application test context, where its {@code POST /internal/v1/attest} clashed with {@code
 * AttestController}'s ("Ambiguous mapping") and the context failed to start.</p>
 */
@RestController
@Profile(InternalTestController.PROFILE)
class InternalTestController {

    static final String PROFILE = "internal-test-controller";

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
