package com.themistra.notification.common;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only stand-in for the in-app stream/read endpoints (task 13), neither of which exists yet
 * (Kimi Phase 3 Finding #3). Gives {@link ResourceServerConfigIntegrationTest} a real, arbitrary
 * secured path to exercise {@link ResourceServerConfig}'s {@code .anyRequest().authenticated()}
 * rule against. Never shipped in {@code src/main} - test scope only.
 *
 * <p>Kimi sweep review, Finding 5 (2026-10-20): loaded only under {@link #PROFILE}. Without it,
 * component scanning put this endpoint into every full-application test context, as crypto's
 * equivalent was until the sweep.</p>
 */
@RestController
@Profile(ResourceServerTestController.PROFILE)
class ResourceServerTestController {

    static final String PROFILE = "resource-server-test-controller";

    @GetMapping("/v1/notifications/unread")
    ResponseEntity<Void> unread() {
        return ResponseEntity.ok().build();
    }
}
